// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.puzzles;

import com.kingfischer.engine.chess.*;
import com.kingfischer.engine.movegen.MoveGenerator;
import java.util.*;

/** One puzzle attempt, owned by the JavaFX thread. No engine search is needed. */
public final class PuzzleSession {
    private final Puzzle puzzle;
    private Position position;
    private int next, player, lastMove;
    private boolean complete, assisted, mistaken, revealed;
    public PuzzleSession(Puzzle puzzle) {
        this.puzzle=puzzle;
        // Validate the entire recorded line before allowing an attempt.
        Position test=new Position(puzzle.fen());
        for(String uci:puzzle.moves()) test.make(test.parseMove(uci));
        reset();
    }
    public void reset() {
        position=new Position(puzzle.fen());
        lastMove=position.parseMove(puzzle.moves().get(0)); position.make(lastMove);
        next=1; player=position.side;
        complete=assisted=mistaken=revealed=false;
    }
    public Puzzle puzzle() { return puzzle; }
    public Position position() { return position; }
    public int player() { return player; }
    public int lastMove() { return lastMove; }
    public boolean complete() { return complete; }
    public boolean revealed() { return revealed; }
    public boolean clean() { return complete && !assisted && !mistaken && !revealed; }
    public boolean playerTurn() { return !complete && !revealed && position.side==player; }
    public int expectedMove() { return position.parseMove(puzzle.moves().get(next)); }
    public boolean tryMove(int move) {
        if(!playerTurn()) throw new IllegalStateException("It is not your turn");
        int legal=position.parseMove(Move.uci(move));
        boolean correct=Move.uci(legal).equals(puzzle.moves().get(next));
        if(!correct) {
            position.make(legal);
            int[] moves=new int[256];
            correct=position.inCheck() && MoveGenerator.legal(position,moves)==0;
            position.undo();
            if(correct) { position.make(legal); lastMove=legal; complete=true; return true; }
            mistaken=true; return false;
        }
        advance(legal); return true;
    }
    private void advance(int move) {
        position.make(move); lastMove=move; next++;
        complete=next>=puzzle.moves().size();
    }
    public int reply() {
        if(complete || revealed || position.side==player) return 0;
        int move=expectedMove(); advance(move); return move;
    }
    public int hint() {
        if(!playerTurn()) return -1;
        assisted=true; return Move.from(expectedMove());
    }
    public List<String> reveal() {
        assisted=true; revealed=true;
        return List.copyOf(puzzle.moves().subList(1,puzzle.moves().size()));
    }
}
