// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.movegen.MoveGenerator;
import java.util.Arrays;

/**
 * Algebraic notation from legal moves, including pinned-piece disambiguation.
 */
public final class MoveNotation {

    private MoveNotation() {
    }

    public static String format(Position p, int move, boolean doubleCheck) {
        int[] legal = new int[256];
        int count = MoveGenerator.legal(p, legal);
        if (Arrays.stream(legal, 0, count).noneMatch(m -> m == move)) {
            throw new IllegalArgumentException("Illegal move");
        }
        int from = Move.from(move), to = Move.to(move), piece = p.board[from], type = piece & 7;
        StringBuilder s = new StringBuilder();
        if ((move & Move.CASTLE) != 0) {
            s.append(to > from ? "O-O" : "O-O-O");
        } else {
            boolean capture = p.board[to] != 0 || (move & Move.EP) != 0;
            if (type != 1) {
                s.append(" PNBRQK".charAt(type));
                boolean other = false, sameFile = false, sameRank = false;
                for (int i = 0; i < count; i++) {
                    int m = legal[i], f = Move.from(m);
                    if (f != from && Move.to(m) == to && p.board[f] == piece) {
                        other = true;
                        sameFile |= (f & 7) == (from & 7);
                        sameRank |= (f >>> 3) == (from >>> 3);
                    }
                }
                if (other) {
                    if (!sameFile) {
                        s.append((char) ('a' + (from & 7)));
                    } else if (!sameRank) {
                        s.append((char) ('1' + (from >>> 3)));
                    } else {
                        s.append(Move.square(from));
                    }
                }
            } else if (capture) {
                s.append((char) ('a' + (from & 7)));
            }
            if (capture) {
                s.append('x');
            }
            s.append(Move.square(to));
            if (Move.promotion(move) != 0) {
                s.append('=').append(" PNBRQK".charAt(Move.promotion(move)));
            }
        }
        p.make(move);
        try {
            if (p.inCheck()) {
                if (MoveGenerator.legal(p, new int[256]) == 0) {
                    s.append('#');
                } else if (doubleCheck
                        && Long.bitCount(p.attackers(p.king(p.side), p.side ^ 1, p.occupied(), 0)) > 1) {
                    s.append("++");
                } else {
                    s.append('+');
                }
            }
        } finally {
            p.undo();
        }
        return s.toString();
    }
}
