// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.movegen;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.eval.Evaluation;

/**
 * Legal least-valuable-attacker swap-off, with evolving occupancy, x-rays and
 * king safety.
 */
public final class StaticExchange {

    private StaticExchange() {
    }

    public static int see(Position p, int move, int[] gain) {
        if ((move & Move.CASTLE) != 0) {
            return 0;
        }
        int from = Move.from(move), to = Move.to(move), us = p.side;
        int capture = (move & Move.EP) != 0 ? to + (us == 0 ? -8 : 8) : to;
        int occupant = Move.promotion(move) != 0 ? Move.promotion(move) : (p.board[from] & 7);
        gain[0]
                = Evaluation.VALUE[p.board[capture] & 7]
                + (Move.promotion(move) != 0 ? Evaluation.VALUE[occupant] - Evaluation.VALUE[1] : 0);
        long removed = (1L << from) | (1L << capture), occ = (p.occupied() & ~removed) | (1L << to);
        int side = us ^ 1, depth = 0;
        while (depth < 30) {
            long candidates = p.attackers(to, side, occ, removed);
            int selected = -1, type = 0;
            for (int t = 1; t <= 6 && selected < 0; t++) {
                long bb = candidates & p.pieces[side << 3 | t];
                while (bb != 0) {
                    int s = Long.numberOfTrailingZeros(bb);
                    bb &= bb - 1;
                    long after = occ & ~(1L << s), gone = removed | (1L << s);
                    int king = t == 6 ? to : p.king(side);
                    if (p.attackers(king, side ^ 1, after, gone) == 0) {
                        selected = s;
                        type = t;
                        break;
                    }
                }
            }
            if (selected < 0) {
                break;
            }
            int promote
                    = type == 1 && ((to >>> 3) == 0 || (to >>> 3) == 7)
                            ? Evaluation.VALUE[5] - Evaluation.VALUE[1]
                            : 0;
            gain[++depth] = Evaluation.VALUE[occupant] + promote - gain[depth - 1];
            occupant = promote == 0 ? type : 5;
            removed |= 1L << selected;
            occ &= ~(1L << selected);
            side ^= 1;
            if (type == 6) {
                break;
            }
        }
        while (depth > 0) {
            gain[depth - 1] = -Math.max(-gain[depth - 1], gain[depth]);
            depth--;
        }
        return gain[0];
    }
}
