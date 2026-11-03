// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.nnue;

import com.kingfischer.engine.chess.Position;
import java.util.Arrays;

/**
 * Lazy incremental NNUE. Feature lists are diffed against the nearest evaluated
 * ancestor. Only changed feature vectors are added/subtracted. Undo reuses the
 * parent's frame. This portable implementation trades Stockfish's dirty-attack
 * machinery for list generation.
 */
public final class AccumulatorStack {

    private final Frame[] frames = new Frame[256];
    private final Network net;
    private final int[] transformed = new int[1024], temp = new int[32], concat = new int[128];
    private int top;
    public long evaluations, featureUpdates, refreshes;

    public AccumulatorStack(Network net) {
        this.net = net;
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new Frame();
        }
    }

    public void push() {
        frames[++top].valid = false;
    }

    public void pop() {
        if (top == 0) {
            throw new IllegalStateException("Accumulator underflow");
        }
        top--;
    }

    public void reset() {
        top = 0;
        for (Frame f : frames) {
            f.valid = false;
        }
    }

    public void ensure(Position p) {
        Frame f = frames[top];
        if (f.valid && f.key == p.key) {
            return;
        }
        Frame ancestor = null;
        for (int i = top - 1; i >= 0; i--) {
            if (frames[i].valid) {
                ancestor = frames[i];
                break;
            }
        }
        for (int c = 0; c < 2; c++) {
            f.n[c] = Features.active(p, c, f.features[c]);
            if (ancestor == null) {
                System.arraycopy(net.biases, 0, f.acc[c], 0, 1024);
                Arrays.fill(f.psqt[c], 0);
                refreshes++;
                for (int i = 0; i < f.n[c]; i++) {
                    net.update(f.acc[c], f.psqt[c], f.features[c][i], 1);
                    featureUpdates++;
                }
            } else {
                System.arraycopy(ancestor.acc[c], 0, f.acc[c], 0, 1024);
                System.arraycopy(ancestor.psqt[c], 0, f.psqt[c], 0, 8);
                int i = 0, j = 0;
                while (i < ancestor.n[c] || j < f.n[c]) {
                    int a = i < ancestor.n[c] ? ancestor.features[c][i] : Integer.MAX_VALUE,
                            b = j < f.n[c] ? f.features[c][j] : Integer.MAX_VALUE;
                    if (a == b) {
                        i++;
                        j++;
                    } else if (a < b) {
                        net.update(f.acc[c], f.psqt[c], a, -1);
                        i++;
                        featureUpdates++;
                    } else {
                        net.update(f.acc[c], f.psqt[c], b, 1);
                        j++;
                        featureUpdates++;
                    }
                }
            }
        }
        f.key = p.key;
        f.valid = true;
    }

    /**
     * Returns packed PSQT (high 32 bits) and positional (low 32 bits), in
     * Stockfish units.
     */
    public long raw(Position p) {
        ensure(p);
        evaluations++;
        Frame f = frames[top];
        int bucket = (Long.bitCount(p.occupied()) - 1) / 4;
        int psqt = ((f.psqt[p.side][bucket] - f.psqt[p.side ^ 1][bucket]) / 2) / 16;
        for (int c = 0; c < 2; c++) {
            short[] a = f.acc[p.side ^ c];
            for (int j = 0; j < 512; j++) {
                transformed[c * 512 + j]
                        = Math.max(0, Math.min(255, a[j])) * Math.max(0, Math.min(255, a[j + 512])) / 512;
            }
        }
        return ((long) psqt << 32) | (net.positional(bucket, transformed, temp, concat) & 0xffffffffL);
    }

    public boolean equalsFresh(Position p) {
        ensure(p);
        AccumulatorStack fresh = new AccumulatorStack(net);
        fresh.ensure(p);
        for (int c = 0; c < 2; c++) {
            if (!Arrays.equals(frames[top].acc[c], fresh.frames[0].acc[c])
                    || !Arrays.equals(frames[top].psqt[c], fresh.frames[0].psqt[c])) {
                return false;
            }
        }
        return raw(p) == fresh.raw(p);
    }

    private static final class Frame {

        final short[][] acc = new short[2][1024];
        final int[][] psqt = new int[2][8], features = new int[2][1024];
        final int[] n = new int[2];
        long key;
        boolean valid;
    }
}
