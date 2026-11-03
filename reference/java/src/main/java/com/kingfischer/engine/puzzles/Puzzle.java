// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.puzzles;

import java.util.*;

/** Immutable Lichess puzzle; moves[0] is the opponent's setup move. */
public record Puzzle(String id, String fen, List<String> moves, int rating,
                     Set<String> themes, String gameUrl) {
    public Puzzle {
        if (id == null || id.isBlank() || fen == null || fen.isBlank())
            throw new IllegalArgumentException("Missing puzzle ID or FEN");
        moves = List.copyOf(moves);
        themes = Set.copyOf(themes);
        if (moves.size() < 2) throw new IllegalArgumentException("Missing solution moves");
        for (String move : moves)
            if (!move.matches("[a-h][1-8][a-h][1-8][qrbn]?"))
                throw new IllegalArgumentException("Invalid UCI move: " + move);
    }
}
