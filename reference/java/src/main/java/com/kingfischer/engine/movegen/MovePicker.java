// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.movegen;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.eval.Evaluation;
import com.kingfischer.engine.history.HistoryTables;

/**
 * Preallocated staged priority bands: TT, sound tactical moves, quiet history,
 * losing captures.
 */
public final class MovePicker {

    public final int[] moves = new int[256], see = new int[256];
    private final int[] scores = new int[256], gain = new int[32];
    public int count, index, lastSee, legalCount;

    public void init(
            Position p, int ttMove, HistoryTables history, int previous, boolean tacticalOnly) {
        count = MoveGenerator.legal(p, moves);
        legalCount = count;
        index = 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            int m = moves[i];
            boolean capture = MoveGenerator.capture(p, m), promotion = Move.promotion(m) != 0;
            if (tacticalOnly && !capture && !promotion) {
                continue;
            }
            int s = 0, e = 0;
            if (capture || promotion) {
                e = StaticExchange.see(p, m, gain);
                s
                        = (e >= 0 ? 1_000_000 : -1_000_000)
                        + 8 * Evaluation.VALUE[p.board[Move.to(m)] & 7]
                        + history.capture(p, m)
                        + Evaluation.VALUE[Move.promotion(m)];
            } else {
                s = history.quiet(p, m, previous);
            }
            if (m == ttMove) {
                s = 2_000_000;
            }
            moves[n] = m;
            scores[n] = s;
            see[n] = e;
            n++;
        }
        count = n;
    }

    public int next() {
        if (index == count) {
            return 0;
        }
        int best = index;
        for (int i = index + 1; i < count; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        int m = moves[best], s = scores[best], e = see[best];
        moves[best] = moves[index];
        scores[best] = scores[index];
        see[best] = see[index];
        moves[index] = m;
        scores[index] = s;
        see[index] = e;
        index++;
        lastSee = e;
        return m;
    }
}
