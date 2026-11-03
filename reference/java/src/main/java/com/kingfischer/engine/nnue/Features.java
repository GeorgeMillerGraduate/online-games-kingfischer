// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.nnue;

import com.kingfischer.engine.bitboard.Attacks;
import com.kingfischer.engine.chess.Position;
import java.util.Arrays;

/**
 * Scalar port of HalfKAv2_hm, FullThreats and PP_3Wide at the pinned Stockfish
 * revision. Copyright (C) 2004-2026 The Stockfish developers (see
 * STOCKFISH-AUTHORS).
 */
public final class Features {

    public static final int PSQ = 22528, THREATS = 59808, PAIRS = 4560;
    private static final int[] TARGETS = {0, 4, 10, 8, 8, 10, 0, 0, 0, 4, 10, 8, 8, 10, 0, 0};
    private static final int[][] MAP = {
        {-1, 0, -1, 1, -1, -1},
        {0, 1, 2, 3, 4, -1},
        {0, 1, 2, 3, -1, -1},
        {0, 1, 2, 3, -1, -1},
        {0, 1, 2, 3, 4, -1},
        {-1, -1, -1, -1, -1, -1}
    };
    private static final int[][] OFF = new int[16][64];
    private static final int[] SPAN = new int[16], BASE = new int[16];
    private static final long[][] PSEUDO = new long[16][64];

    static {
        int total = 0;
        for (int pc = 1; pc <= 14; pc++) {
            if ((pc & 7) == 0 || (pc & 7) == 7) {
                continue;
            }
            BASE[pc] = total;
            for (int s = 0; s < 64; s++) {
                OFF[pc][s] = SPAN[pc];
                PSEUDO[pc][s] = Attacks.of(pc, s, 0);
                if ((pc & 7) != 1 || (s >= 8 && s <= 55)) {
                    SPAN[pc] += Long.bitCount(PSEUDO[pc][s]);
                }
            }
            total += TARGETS[pc] * SPAN[pc];
        }
        if (total != THREATS) {
            throw new ExceptionInInitializerError("Threat feature layout mismatch: " + total);
        }
    }

    private Features() {
    }

    public static int psq(int perspective, int square, int piece, int king) {
        int k = king ^ (56 * perspective), bucket = (7 - (k >>> 3)) * 4 + Math.min(k & 7, 7 - (k & 7));
        int type = piece & 7, pc = type == 6 ? 10 : 2 * (type - 1) + ((piece >>> 3) ^ perspective);
        return bucket * 704 + pc * 64 + (square ^ ((king & 7) < 4 ? 7 : 0) ^ (56 * perspective));
    }

    public static int threat(
            int perspective, int attacker, int from, int to, int attacked, int king) {
        int orient = ((king & 7) < 4 ? 0 : 7) ^ (56 * perspective);
        int a = attacker ^ (perspective * 8),
                b = attacked ^ (perspective * 8),
                f = from ^ orient,
                t = to ^ orient;
        int type = a & 7, target = b & 7, map = MAP[type - 1][target - 1];
        if (map < 0 || (f < t && type == target && ((a ^ b) == 8 || type != 1))) {
            return -1;
        }
        return BASE[a]
                + ((b >>> 3) * (TARGETS[a] / 2) + map) * SPAN[a]
                + OFF[a][f]
                + Long.bitCount(PSEUDO[a][f] & ((1L << t) - 1));
    }

    public static int pair(int perspective, int pc, int from, int other, int to, int king) {
        int o = ((king & 7) < 4 ? 0 : 7) ^ (56 * perspective);
        int a = 48 * ((pc >>> 3) ^ perspective) + (from ^ o) - 8,
                b = 48 * ((other >>> 3) ^ perspective) + (to ^ o) - 8;
        int hi = Math.max(a, b), lo = Math.min(a, b);
        return THREATS + hi * (hi - 1) / 2 + lo;
    }

    /**
     * Unified feature list: PSQ first, then threats and pawn pairs at offset
     * PSQ.
     */
    public static int active(Position p, int perspective, int[] out) {
        int n = 0, k = p.king(perspective);
        long occupied = p.occupied(), bb = occupied;
        while (bb != 0) {
            int s = Long.numberOfTrailingZeros(bb);
            bb &= bb - 1;
            int pc = p.board[s];
            out[n++] = psq(perspective, s, pc, k);
            if ((pc & 7) < 6) {
                long attacks = Attacks.of(pc, s, occupied) & occupied;
                while (attacks != 0) {
                    int t = Long.numberOfTrailingZeros(attacks);
                    attacks &= attacks - 1;
                    int idx = threat(perspective, pc, s, t, p.board[t], k);
                    if (idx >= 0) {
                        out[n++] = PSQ + idx;
                    }
                }
            }
        }
        bb = p.pieces[1] | p.pieces[9];
        while (bb != 0) {
            int a = Long.numberOfTrailingZeros(bb);
            bb &= bb - 1;
            long rest = bb;
            while (rest != 0) {
                int b = Long.numberOfTrailingZeros(rest);
                rest &= rest - 1;
                if (Math.abs((a & 7) - (b & 7)) <= 1) {
                    out[n++] = PSQ + pair(perspective, p.board[a], a, p.board[b], b, k);
                }
            }
        }
        Arrays.sort(out, 0, n);
        return n;
    }
}
