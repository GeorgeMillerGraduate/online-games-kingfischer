// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.movegen.MoveGenerator;
import java.util.*;

/**
 * Owned by the JavaFX thread. Search receives copies only.
 */
public final class GameModel {

    private final Position position = new Position();
    private final List<Integer> moves = new ArrayList<>();
    private final List<String> notation = new ArrayList<>();
    private String resigned;

    public Position position() {
        return position;
    }

    public int size() {
        return moves.size();
    }

    public int move(int i) {
        return moves.get(i);
    }

    public String notation(int i) {
        return notation.get(i);
    }

    public int[] legal() {
        int[] a = new int[256];
        return Arrays.copyOf(a, MoveGenerator.legal(position, a));
    }

    public void play(int move) {
        if (result() != null) {
            throw new IllegalStateException("Game has ended");
        }
        String san = MoveNotation.format(position, move, true);
        position.make(move);
        moves.add(move);
        notation.add(san);
    }

    public Position at(int ply) {
        if (ply < 0 || ply > size()) {
            throw new IllegalArgumentException("Invalid history position");
        }
        Position p = new Position();
        for (int i = 0; i < ply; i++) {
            p.make(moves.get(i));
        }
        return p;
    }

    public void undoTurn(int human) {
        resigned = null;
        if (moves.isEmpty()) {
            return;
        }
        do {
            position.undo();
            moves.remove(moves.size() - 1);
            notation.remove(notation.size() - 1);
        } while (!moves.isEmpty() && position.side != human);
    }

    public void resign(int human) {
        resigned = (human == 0 ? "Black" : "White") + " wins by resignation";
    }

    public String result() {
        if (resigned != null) {
            return resigned;
        }
        if (legal().length == 0) {
            return position.inCheck()
                    ? (position.side == 0 ? "Black" : "White") + " wins by checkmate"
                    : "Draw by stalemate";
        }
        if (position.halfmove >= 100) {
            return "Draw by fifty-move rule";
        }
        if (position.repetition(0)) {
            return "Draw by threefold repetition";
        }
        if (position.insufficientMaterial()) {
            return "Draw by insufficient material";
        }
        return null;
    }

    public String resultCode() {
        String r = result();
        return r == null
                ? "*"
                : r.startsWith("White") ? "1-0" : r.startsWith("Black") ? "0-1" : "1/2-1/2";
    }

    public String pgn(int human) {
        StringBuilder b = new StringBuilder();
        b.append("[Event \"KingFischer 2.0 casual game\"]\n[Site \"Local\"]\n");
        b.append("[White \"").append(human == 0 ? "You" : "KingFischer 2.0").append("\"]\n");
        b.append("[Black \"").append(human == 1 ? "You" : "KingFischer 2.0").append("\"]\n");
        b.append("[Result \"").append(resultCode()).append("\"]\n\n");
        for (int i = 0; i < size(); i++) {
            if (i % 2 == 0) {
                b.append(i / 2 + 1).append(". ");
            }
            b.append(notation.get(i).replace("++", "+")).append(' ');
        }
        return b.append(resultCode()).append('\n').toString();
    }
}
