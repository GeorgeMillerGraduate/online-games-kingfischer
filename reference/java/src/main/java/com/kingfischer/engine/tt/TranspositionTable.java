// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.tt;

import java.util.Arrays;

/**
 * Four-way clustered primitive-array TT. Full keys, mate-distance
 * normalization, aged replacement. Design adapted from Stockfish tt.cpp;
 * different Java storage and cluster width.
 */
public final class TranspositionTable {

    public static final int EXACT = 1, LOWER = 2, UPPER = 3, MATE = 32000;
    private final long[] keys;
    private final int[] moves;
    private final short[] scores, evals, depths;
    private final byte[] bounds, ages;
    private final int mask;
    private int generation = 1;

    public TranspositionTable(int mb) {
        if (mb < 1 || mb > 512) {
            throw new IllegalArgumentException("Hash must be 1..512 MB");
        }
        int capacity = Integer.highestOneBit((int) ((long) mb * 1024 * 1024 / 20));
        capacity = Math.max(4, capacity);
        mask = capacity - 4;
        keys = new long[capacity];
        moves = new int[capacity];
        scores = new short[capacity];
        evals = new short[capacity];
        depths = new short[capacity];
        bounds = new byte[capacity];
        ages = new byte[capacity];
    }

    public void clear() {
        Arrays.fill(bounds, (byte) 0);
    }

    public void newSearch() {
        generation = (generation + 1) & 255;
    }

    public int probe(long key) {
        int start = (int) (key ^ (key >>> 32)) & mask;
        for (int i = start; i < start + 4; i++) {
            if (bounds[i] != 0 && keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    public int move(int i) {
        return i < 0 ? 0 : moves[i];
    }

    public int depth(int i) {
        return depths[i];
    }

    public int bound(int i) {
        return bounds[i];
    }

    public int eval(int i) {
        return evals[i];
    }

    public int score(int i, int ply) {
        int v = scores[i];
        return v > 31000 ? v - ply : v < -31000 ? v + ply : v;
    }

    public void store(long key, int depth, int score, int eval, int move, int bound, int ply) {
        int start = (int) (key ^ (key >>> 32)) & mask, best = start, quality = Integer.MAX_VALUE;
        for (int i = start; i < start + 4; i++) {
            if (bounds[i] == 0 || keys[i] == key) {
                best = i;
                break;
            }
            int q = depths[i] - 8 * ((generation - (ages[i] & 255)) & 255);
            if (q < quality) {
                quality = q;
                best = i;
            }
        }
        if (keys[best] == key
                && bounds[best] != 0
                && bound != EXACT
                && depth < depths[best] - 4
                && (ages[best] & 255) == generation) {
            return;
        }
        if (move != 0 || keys[best] != key) {
            moves[best] = move;
        }
        keys[best] = key;
        depths[best] = (short) depth;
        evals[best] = (short) eval;
        bounds[best] = (byte) bound;
        ages[best] = (byte) generation;
        scores[best] = (short) (score > 31000 ? score + ply : score < -31000 ? score - ply : score);
    }

    public int hashfull() {
        int used = 0, n = Math.min(1000, bounds.length);
        for (int i = 0; i < n; i++) {
            if (bounds[i] != 0 && (ages[i] & 255) == generation) {
                used++;
            }
        }
        return used * 1000 / n;
    }
}
