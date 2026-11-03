// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.util;

public final class Zobrist {

    public static final long[][] PIECE = new long[16][64];
    public static final long[] CASTLING = new long[16], EP = new long[8];
    public static final long SIDE;
    private static long seed = 0x4a656e6761466973L;

    static {
        for (int p = 0; p < 16; p++) {
            for (int s = 0; s < 64; s++) {
                PIECE[p][s] = next();
            }
        }
        for (int i = 0; i < 16; i++) {
            CASTLING[i] = next();
        }
        for (int i = 0; i < 8; i++) {
            EP[i] = next();
        }
        SIDE = next();
    }

    private static long next() {
        long z = (seed += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private Zobrist() {
    }
}
