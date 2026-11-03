// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.eval;

import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.nnue.AccumulatorStack;

/**
 * Port of evaluate.cpp's NNUE combination, with zero search optimism. Copyright
 * (C) 2004-2026 The Stockfish developers (see STOCKFISH-AUTHORS).
 */
public final class Evaluation {

    public static final int[] VALUE = {0, 208, 781, 825, 1276, 2538, 0};

    private Evaluation() {
    }

    public static int evaluate(Position p, AccumulatorStack acc) {
        long raw = acc.raw(p);
        int psqt = (int) (raw >> 32), pos = (int) raw, nnue = psqt + pos;
        int complexity = Math.abs(psqt - pos);
        nnue -= (int) ((long) nnue * complexity / 18236);
        int material = 534 * Long.bitCount(p.pieces[1] | p.pieces[9]);
        for (int t = 2; t <= 5; t++) {
            material += VALUE[t] * Long.bitCount(p.pieces[t] | p.pieces[t | 8]);
        }
        int v = nnue + (int) ((long) nnue * material / 91000);
        v -= v * p.halfmove / 199;
        return Math.max(-30000, Math.min(30000, v));
    }
}
