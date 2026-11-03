// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.search;

public final class RootMove {

    public final int move;
    public final int[] pv = new int[256];
    public int score = -32700, length = 1;
    public long nodes;

    public RootMove(int move) {
        this.move = move;
        pv[0] = move;
    }
}
