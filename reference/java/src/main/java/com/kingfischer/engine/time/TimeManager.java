// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.time;

import com.kingfischer.engine.search.SearchLimits;

/**
 * Clock allocation adapted from Stockfish timeman.cpp (zero nodestime; one
 * worker).
 */
public final class TimeManager {

    public final long optimum, maximum;

    public TimeManager(SearchLimits l, int side, int gamePly, int overhead) {
        if (l.infinite) {
            optimum = maximum = Long.MAX_VALUE;
            return;
        }
        if (l.moveTime >= 0) {
            optimum = maximum = Math.max(1, l.moveTime - overhead);
            return;
        }
        if (l.time[side] < 0) {
            optimum = maximum = Long.MAX_VALUE;
            return;
        }
        long time = l.time[side], inc = l.increment[side];
        int mtg = l.movesToGo > 0 ? Math.min(l.movesToGo, 50) : 50;
        if (time < 1000 && l.movesToGo == 0) {
            mtg = Math.max(1, (int) (time * 0.05));
        }
        long left = Math.max(1, time + inc * (mtg - 1) - (long) overhead * (2 + mtg));
        double opt, max;
        if (l.movesToGo == 0) {
            double adjust = 0.3272 * Math.log10(left) - 0.4141,
                    log = Math.log10(Math.max(1, time) / 1000.0);
            double oc = Math.min(0.0029869 + 0.00033554 * log, 0.004905),
                    mc = Math.max(3.3744 + 3.0608 * log, 3.1441);
            opt
                    = Math.min(0.012112 + Math.pow(gamePly + 3.22713, 0.46866) * oc, 0.19404 * time / left)
                    * adjust;
            max = Math.min(6.873, mc + gamePly / 12.352);
        } else {
            opt = Math.min((0.88 + gamePly / 116.4) / mtg, 0.88 * time / left);
            max = 1.3 + 0.11 * mtg;
        }
        if (l.time[side ^ 1] >= 0 && l.movesToGo != 1) {
            opt *= 1 + 0.9 * Math.min((time - l.time[side ^ 1]) / (1.0 + time + l.time[side ^ 1]), 0);
        }
        long available = Math.max(1, time - overhead);
        optimum = Math.min(available, Math.max(1, (long) (opt * left)));
        maximum
                = Math.min(
                        available, Math.max(optimum, (long) Math.min(0.8097 * time - overhead, max * optimum)));
    }
}
