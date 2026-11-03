// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.chess;

/**
 * Reused undo record, storing only changed state, never a board copy.
 */
public final class StateInfo {

    int move, piece, captured, captureSquare, rights, ep, halfmove, fullmove, nullPly;
    long key, pawnKey;
}
