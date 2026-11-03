// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.util;

import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.movegen.MoveGenerator;

public final class Perft {

    private final int[][] moves = new int[64][256];

    public long count(Position p, int depth) {
        if (depth < 0 || depth >= 64) {
            throw new IllegalArgumentException("Perft depth must be 0..63");
        }
        return visit(p, depth, 0);
    }

    private long visit(Position p, int d, int ply) {
        if (d == 0) {
            return 1;
        }
        int[] list = moves[ply];
        int n = MoveGenerator.legal(p, list);
        if (d == 1) {
            return n;
        }
        long sum = 0;
        for (int i = 0; i < n; i++) {
            p.make(list[i]);
            sum += visit(p, d - 1, ply + 1);
            p.undo();
        }
        return sum;
    }
}
