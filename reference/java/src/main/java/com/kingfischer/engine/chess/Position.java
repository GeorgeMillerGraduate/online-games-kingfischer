// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.chess;

import com.kingfischer.engine.bitboard.Attacks;
import com.kingfischer.engine.movegen.MoveGenerator;
import com.kingfischer.engine.util.Zobrist;

/**
 * Mutable orthodox chess position. One owner thread; incremental bitboards and
 * hashes.
 */
public final class Position {

    public static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public final int[] board = new int[64];
    public final long[] pieces = new long[16], colors = new long[2];
    public int side, rights, ep = -1, halfmove, fullmove = 1, ply;
    public long key, pawnKey;
    private int nullPly = -1;
    private final StateInfo[] states = new StateInfo[4096];
    private final long[] keys = new long[4097];

    public Position() {
        this(START);
    }

    public Position(String fen) {
        for (int i = 0; i < states.length; i++) {
            states[i] = new StateInfo();
        }
        load(fen);
    }

    public long occupied() {
        return colors[0] | colors[1];
    }

    public int king(int color) {
        return Long.numberOfTrailingZeros(pieces[(color << 3) | 6]);
    }

    public long attackers(int sq, int color, long occ, long removed) {
        int c = color << 3;
        long keep = ~removed;
        return ((Attacks.PAWN[color ^ 1][sq] & pieces[c | 1])
                | (Attacks.KNIGHT[sq] & pieces[c | 2])
                | (Attacks.KING[sq] & pieces[c | 6])
                | (Attacks.bishop(sq, occ) & (pieces[c | 3] | pieces[c | 5]))
                | (Attacks.rook(sq, occ) & (pieces[c | 4] | pieces[c | 5])))
                & keep;
    }

    public boolean attacked(int sq, int color) {
        return attackers(sq, color, occupied(), 0) != 0;
    }

    public boolean inCheck() {
        return attacked(king(side), side ^ 1);
    }

    private void put(int s, int p) {
        int old = board[s];
        long bit = 1L << s;
        if (old != 0) {
            pieces[old] ^= bit;
            colors[old >>> 3] ^= bit;
            key ^= Zobrist.PIECE[old][s];
            if ((old & 7) == 1) {
                pawnKey ^= Zobrist.PIECE[old][s];
            }
        }
        board[s] = p;
        if (p != 0) {
            pieces[p] ^= bit;
            colors[p >>> 3] ^= bit;
            key ^= Zobrist.PIECE[p][s];
            if ((p & 7) == 1) {
                pawnKey ^= Zobrist.PIECE[p][s];
            }
        }
    }

    /**
     * Repetition keys include EP only when an EP capture is actually legal.
     */
    private int epFile() {
        if (ep < 0) {
            return -1;
        }
        int cap = ep + (side == 0 ? -8 : 8);
        if (board[ep] != 0 || board[cap] != ((side ^ 1) << 3 | 1)) {
            return -1;
        }
        long candidates = Attacks.PAWN[side ^ 1][ep] & pieces[side << 3 | 1];
        while (candidates != 0) {
            int from = Long.numberOfTrailingZeros(candidates);
            candidates &= candidates - 1;
            long occ = (occupied() & ~(1L << from) & ~(1L << cap)) | (1L << ep);
            if (attackers(king(side), side ^ 1, occ, 1L << cap) == 0) {
                return ep & 7;
            }
        }
        return -1;
    }

    private long stateHash() {
        int f = epFile();
        return Zobrist.CASTLING[rights] ^ (side == 1 ? Zobrist.SIDE : 0) ^ (f < 0 ? 0 : Zobrist.EP[f]);
    }

    public long recomputeKey() {
        long h = stateHash();
        for (int s = 0; s < 64; s++) {
            if (board[s] != 0) {
                h ^= Zobrist.PIECE[board[s]][s];
            }
        }
        return h;
    }

    public void make(int m) {
        if (ply >= states.length - 1) {
            throw new IllegalStateException("Position history limit reached");
        }
        StateInfo st = states[ply];
        st.move = m;
        st.rights = rights;
        st.ep = ep;
        st.halfmove = halfmove;
        st.fullmove = fullmove;
        st.key = key;
        st.pawnKey = pawnKey;
        st.nullPly = nullPly;
        key ^= stateHash();
        if (m == 0) {
            st.piece = st.captured = 0;
            ep = -1;
            halfmove++;
            nullPly = ply + 1;
        } else {
            int from = Move.from(m),
                    to = Move.to(m),
                    p = board[from],
                    cap = (m & Move.EP) != 0 ? to + (side == 0 ? -8 : 8) : to;
            st.piece = p;
            st.captureSquare = cap;
            st.captured = board[cap];
            put(from, 0);
            if (st.captured != 0) {
                put(cap, 0);
            }
            put(to, Move.promotion(m) != 0 ? (side << 3) | Move.promotion(m) : p);
            if ((m & Move.CASTLE) != 0) {
                int rf = to > from ? from + 3 : from - 4, rt = to > from ? from + 1 : from - 1;
                put(rf, 0);
                put(rt, (side << 3) | 4);
            }
            rights &= castleMask(from) & castleMask(to);
            ep = (p & 7) == 1 && Math.abs(to - from) == 16 ? (from + to) / 2 : -1;
            halfmove = (p & 7) == 1 || st.captured != 0 ? 0 : halfmove + 1;
        }
        if (side == 1) {
            fullmove++;
        }
        side ^= 1;
        key ^= stateHash();
        keys[++ply] = key;
    }

    private static int castleMask(int s) {
        return switch (s) {
            case 0 ->
                13;
            case 4 ->
                12;
            case 7 ->
                14;
            case 56 ->
                7;
            case 60 ->
                3;
            case 63 ->
                11;
            default ->
                15;
        };
    }

    public void undo() {
        if (ply == 0) {
            throw new IllegalStateException("No move to undo");
        }
        StateInfo st = states[--ply];
        side ^= 1;
        int m = st.move;
        if (m != 0) {
            int from = Move.from(m), to = Move.to(m);
            put(to, 0);
            put(from, st.piece);
            if ((m & Move.CASTLE) != 0) {
                int rf = to > from ? from + 3 : from - 4, rt = to > from ? from + 1 : from - 1;
                put(rt, 0);
                put(rf, (side << 3) | 4);
            }
            if (st.captured != 0) {
                put(st.captureSquare, st.captured);
            }
        }
        rights = st.rights;
        ep = st.ep;
        halfmove = st.halfmove;
        fullmove = st.fullmove;
        key = st.key;
        pawnKey = st.pawnKey;
        nullPly = st.nullPly;
    }

    public boolean repetition(int searchPly) {
        int count = 0, limit = Math.max(Math.max(0, ply - halfmove), nullPly);
        for (int i = ply - 2; i >= limit; i -= 2) {
            if (keys[i] == key) {
                if (i > ply - searchPly || ++count >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean insufficientMaterial() {
        if ((pieces[1] | pieces[9] | pieces[4] | pieces[12] | pieces[5] | pieces[13]) != 0) {
            return false;
        }
        long minors = pieces[2] | pieces[10] | pieces[3] | pieces[11];
        if (Long.bitCount(minors) <= 1) {
            return true;
        }
        if ((pieces[2] | pieces[10]) != 0) {
            return false;
        }
        return (minors & 0x55aa55aa55aa55aaL) == 0 || (minors & 0xaa55aa55aa55aa55L) == 0;
    }

    public boolean nonPawnMaterial() {
        return (colors[side] & ~(pieces[side << 3 | 1] | pieces[side << 3 | 6])) != 0;
    }

    public int parseMove(String uci) {
        int[] m = new int[256];
        int n = MoveGenerator.legal(this, m);
        for (int i = 0; i < n; i++) {
            if (Move.uci(m[i]).equals(uci)) {
                return m[i];
            }
        }
        throw new IllegalArgumentException("Illegal move: " + uci);
    }

    public Position copy() {
        Position p = new Position(fen());
        p.ply = ply;
        p.nullPly = nullPly;
        System.arraycopy(keys, 0, p.keys, 0, ply + 1);
        return p;
    }

    private void load(String fen) {
        String[] a = fen.trim().split("\\s+");
        if (a.length != 6) {
            throw new IllegalArgumentException("FEN requires six fields");
        }
        String[] ranks = a[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN requires eight ranks");
        }
        String chars = " PNBRQK  pnbrqk";
        for (int r = 0; r < 8; r++) {
            int f = 0;
            for (char c : ranks[r].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    f += c - '0';
                } else {
                    int p = chars.indexOf(c);
                    if (p < 1 || f >= 8) {
                        throw new IllegalArgumentException("Invalid FEN piece/rank");
                    }
                    put((7 - r) * 8 + f++, p);
                }
            }
            if (f != 8) {
                throw new IllegalArgumentException("FEN rank width is not eight");
            }
        }
        if (!a[1].equals("w") && !a[1].equals("b")) {
            throw new IllegalArgumentException("Invalid side");
        }
        side = a[1].equals("w") ? 0 : 1;
        if (!a[2].equals("-")) {
            for (char c : a[2].toCharArray()) {
                int i = "KQkq".indexOf(c);
                if (i < 0 || (rights & (1 << i)) != 0) {
                    throw new IllegalArgumentException("Invalid castling rights");
                }
                rights |= 1 << i;
            }
        }
        ep = a[3].equals("-") ? -1 : Move.square(a[3]);
        halfmove = Integer.parseInt(a[4]);
        fullmove = Integer.parseInt(a[5]);
        if (halfmove < 0
                || fullmove < 1
                || Long.bitCount(pieces[6]) != 1
                || Long.bitCount(pieces[14]) != 1
                || Long.bitCount(occupied()) > 32) {
            throw new IllegalArgumentException("Invalid FEN state/kings");
        }
        if (((pieces[1] | pieces[9]) & 0xff000000000000ffL) != 0) {
            throw new IllegalArgumentException("Pawn on promotion rank");
        }
        if (ep >= 0
                && ((ep >>> 3) != (side == 0 ? 5 : 2)
                || board[ep] != 0
                || board[ep + (side == 0 ? -8 : 8)] != ((side ^ 1) << 3 | 1))) {
            throw new IllegalArgumentException("Invalid en passant square");
        }
        int[] kingSquares = {4, 4, 60, 60}, rookSquares = {7, 0, 63, 56};
        for (int i = 0; i < 4; i++) {
            if ((rights & (1 << i)) != 0
                    && (board[kingSquares[i]] != ((i / 2) << 3 | 6)
                    || board[rookSquares[i]] != ((i / 2) << 3 | 4))) {
                throw new IllegalArgumentException("Castling rights without king/rook");
            }
        }
        if (attacked(king(side ^ 1), side)) {
            throw new IllegalArgumentException("Side not to move is in check");
        }
        key = recomputeKey();
        keys[0] = key;
    }

    public String fen() {
        StringBuilder b = new StringBuilder();
        String chars = " PNBRQK  pnbrqk";
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int p = board[r * 8 + f];
                if (p == 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        b.append(empty);
                    }
                    empty = 0;
                    b.append(chars.charAt(p));
                }
            }
            if (empty > 0) {
                b.append(empty);
            }
            if (r > 0) {
                b.append('/');
            }
        }
        b.append(side == 0 ? " w " : " b ");
        if (rights == 0) {
            b.append('-');
        } else {
            for (int i = 0; i < 4; i++) {
                if ((rights & (1 << i)) != 0) {
                    b.append("KQkq".charAt(i));
                }
            }
        }
        return b.append(' ')
                .append(ep < 0 ? "-" : Move.square(ep))
                .append(' ')
                .append(halfmove)
                .append(' ')
                .append(fullmove)
                .toString();
    }
}
