// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.movegen;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.bitboard.Attacks;

/**
 * Allocation-free pseudo generation and make/unmake legality filtering.
 */
public final class MoveGenerator {

    private MoveGenerator() {
    }

    private static int pawn(int[] out, int n, int from, int to, int flags) {
        int m = Move.of(from, to) | flags;
        if ((to >>> 3) == 0 || (to >>> 3) == 7) {
            for (int p = 5; p >= 2; p--) {
                out[n++] = m | p << 12;
            }
        } else {
            out[n++] = m;
        }
        return n;
    }

    public static int pseudo(Position p, int[] out) {
        int us = p.side, n = 0;
        long own = p.colors[us],
                enemy = p.colors[us ^ 1] & ~p.pieces[(us ^ 1) << 3 | 6],
                occ = p.occupied();
        long bb = own;
        while (bb != 0) {
            int from = Long.numberOfTrailingZeros(bb);
            bb &= bb - 1;
            int pc = p.board[from];
            if ((pc & 7) == 1) {
                int dir = us == 0 ? 8 : -8, to = from + dir;
                if (to >= 0 && to < 64 && p.board[to] == 0) {
                    n = pawn(out, n, from, to, 0);
                    if ((from >>> 3) == (us == 0 ? 1 : 6) && p.board[to + dir] == 0) {
                        out[n++] = Move.of(from, to + dir);
                    }
                }
                long caps = Attacks.PAWN[us][from] & enemy;
                while (caps != 0) {
                    int t = Long.numberOfTrailingZeros(caps);
                    caps &= caps - 1;
                    n = pawn(out, n, from, t, 0);
                }
                if (p.ep >= 0 && (Attacks.PAWN[us][from] & (1L << p.ep)) != 0) {
                    out[n++] = Move.of(from, p.ep) | Move.EP;
                }
            } else {
                long a = Attacks.of(pc, from, occ) & ~own & ~p.pieces[(us ^ 1) << 3 | 6];
                while (a != 0) {
                    int to = Long.numberOfTrailingZeros(a);
                    a &= a - 1;
                    out[n++] = Move.of(from, to);
                }
            }
        }
        int k = us == 0 ? 4 : 60;
        if ((p.rights & (3 << (us * 2))) != 0 && !p.attacked(k, us ^ 1)) {
            long withoutKing = occ & ~(1L << k);
            if ((p.rights & (1 << (us * 2))) != 0
                    && p.board[k + 1] == 0
                    && p.board[k + 2] == 0
                    && p.attackers(k + 1, us ^ 1, withoutKing, 0) == 0
                    && p.attackers(k + 2, us ^ 1, withoutKing, 0) == 0) {
                out[n++] = Move.of(k, k + 2) | Move.CASTLE;
            }
            if ((p.rights & (2 << (us * 2))) != 0
                    && p.board[k - 1] == 0
                    && p.board[k - 2] == 0
                    && p.board[k - 3] == 0
                    && p.attackers(k - 1, us ^ 1, withoutKing, 0) == 0
                    && p.attackers(k - 2, us ^ 1, withoutKing, 0) == 0) {
                out[n++] = Move.of(k, k - 2) | Move.CASTLE;
            }
        }
        return n;
    }

    public static int legal(Position p, int[] out) {
        int n = pseudo(p, out), j = 0, us = p.side;
        for (int i = 0; i < n; i++) {
            int m = out[i];
            p.make(m);
            boolean ok = !p.attacked(p.king(us), p.side);
            p.undo();
            if (ok) {
                out[j++] = m;
            }
        }
        return j;
    }

    public static boolean capture(Position p, int m) {
        return p.board[Move.to(m)] != 0 || (m & Move.EP) != 0;
    }
}
