// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.bitboard;

/**
 * Portable ray lookup: trim each precomputed ray at its nearest blocker.
 */
public final class Attacks {

    public static final long[][] PAWN = new long[2][64];
    public static final long[] KNIGHT = new long[64], KING = new long[64];
    private static final long[][] RAY = new long[8][64];
    private static final int[] DX = {1, -1, 0, 0, 1, -1, 1, -1}, DY = {0, 0, 1, -1, 1, 1, -1, -1};

    static {
        for (int s = 0; s < 64; s++) {
            int x = s & 7, y = s >>> 3;
            for (int t = 0; t < 64; t++) {
                int a = Math.abs(x - (t & 7)), b = Math.abs(y - (t >>> 3));
                if (a * b == 2) {
                    KNIGHT[s] |= 1L << t;
                }
                if (Math.max(a, b) == 1) {
                    KING[s] |= 1L << t;
                }
                if (a == 1 && (t >>> 3) == y + 1) {
                    PAWN[0][s] |= 1L << t;
                }
                if (a == 1 && (t >>> 3) == y - 1) {
                    PAWN[1][s] |= 1L << t;
                }
            }
            for (int d = 0; d < 8; d++) {
                for (int a = x + DX[d], b = y + DY[d];
                        a >= 0 && a < 8 && b >= 0 && b < 8;
                        a += DX[d], b += DY[d]) {
                    RAY[d][s] |= 1L << (b * 8 + a);
                }
            }
        }
    }

    private Attacks() {
    }

    private static long slide(int s, long occ, int start, int end) {
        long a = 0;
        for (int d = start; d < end; d++) {
            long ray = RAY[d][s], blockers = ray & occ;
            if (blockers != 0) {
                int b
                        = (DY[d] * 8 + DX[d]) > 0
                                ? Long.numberOfTrailingZeros(blockers)
                                : 63 - Long.numberOfLeadingZeros(blockers);
                ray ^= RAY[d][b];
            }
            a |= ray;
        }
        return a;
    }

    public static long rook(int s, long occ) {
        return slide(s, occ, 0, 4);
    }

    public static long bishop(int s, long occ) {
        return slide(s, occ, 4, 8);
    }

    public static long of(int piece, int s, long occ) {
        return switch (piece & 7) {
            case 1 ->
                PAWN[piece >>> 3][s];
            case 2 ->
                KNIGHT[s];
            case 3 ->
                bishop(s, occ);
            case 4 ->
                rook(s, occ);
            case 5 ->
                bishop(s, occ) | rook(s, occ);
            case 6 ->
                KING[s];
            default ->
                0;
        };
    }
}
