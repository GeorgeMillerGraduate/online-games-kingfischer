// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.search;

public record SearchResult(
        int bestMove, int ponderMove, int score, int depth, long nodes, long milliseconds) {

}
