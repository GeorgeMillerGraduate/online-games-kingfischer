// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.uci;

import com.kingfischer.engine.chess.Position;

/**
 * Stockfish's material-dependent normalization, independent of the NNUE
 * internal unit.
 */
public final class UciScore {

    private UciScore() {
    }

    public static int cp(int value, Position p) {
        int material = 0;
        int[] weights = {0, 1, 3, 3, 5, 9, 0};
        for (int t = 1; t <= 5; t++) {
            material += weights[t] * Long.bitCount(p.pieces[t] | p.pieces[t | 8]);
        }
        double m = Math.max(17, Math.min(78, material)) / 58.0;
        double a = ((-142.72052667 * m + 372.35176398) * m - 340.71073572) * m + 415.23490212;
        return (int) Math.round(100 * value / a);
    }

    public static String text(int value, Position p) {
        if (Math.abs(value) > 31000) {
            int moves = (32000 - Math.abs(value) + 1) / 2;
            return "mate " + (value < 0 ? -moves : moves);
        }
        return "cp " + cp(value, p);
    }
}
