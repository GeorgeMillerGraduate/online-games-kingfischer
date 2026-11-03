// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.history;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;

/**
 * Bounded gravity updates adapted from Stockfish history.h. Compact
 * thread-local tables.
 */
public final class HistoryTables {

    private final short[] main = new short[8192],
            capture = new short[8192],
            continuation = new short[1024 * 1024],
            pawn = new short[256 * 1024],
            correction = new short[32768];

    private static void update(short[] a, int i, int bonus, int limit) {
        int b = Math.max(-limit, Math.min(limit, bonus)), v = a[i];
        a[i] = (short) (v + b - v * Math.abs(b) / limit);
    }

    private static int pt(int piece, int move) {
        return piece * 64 + Move.to(move);
    }

    public int quiet(Position p, int move, int previous) {
        int pt = pt(p.board[Move.from(move)], move);
        return 2 * main[p.side * 4096 + (move & 4095)]
                + (previous == 0 ? 0 : continuation[previous * 1024 + pt])
                + pawn[((int) p.pawnKey & 255) * 1024 + pt];
    }

    public int capture(Position p, int move) {
        return capture[(pt(p.board[Move.from(move)], move) * 8) + (p.board[Move.to(move)] & 7)];
    }

    public void rewardQuiet(Position p, int move, int previous, int bonus) {
        int pt = pt(p.board[Move.from(move)], move);
        update(main, p.side * 4096 + (move & 4095), bonus, 7183);
        if (previous != 0) {
            update(continuation, previous * 1024 + pt, bonus, 30000);
        }
        update(pawn, ((int) p.pawnKey & 255) * 1024 + pt, bonus, 8192);
    }

    public void rewardCapture(Position p, int move, int bonus) {
        update(
                capture,
                pt(p.board[Move.from(move)], move) * 8 + (p.board[Move.to(move)] & 7),
                bonus,
                10692);
    }

    public int correction(Position p) {
        return correction[p.side * 16384 + ((int) p.pawnKey & 16383)] / 16;
    }

    public void correct(Position p, int difference, int depth) {
        update(
                correction,
                p.side * 16384 + ((int) p.pawnKey & 16383),
                Math.max(-256, Math.min(256, difference * depth / 8)),
                1024);
    }
}
