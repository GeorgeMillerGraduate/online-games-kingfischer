// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.engine;

import com.kingfischer.engine.search.SearchLimits;
import com.kingfischer.engine.search.SearchResult;
import com.kingfischer.engine.search.Search;
import com.kingfischer.engine.nnue.Network;
import com.kingfischer.engine.nnue.AccumulatorStack;
import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.eval.Evaluation;
import com.kingfischer.engine.history.HistoryTables;
import com.kingfischer.engine.movegen.MoveGenerator;
import com.kingfischer.engine.tt.TranspositionTable;
import com.kingfischer.engine.uci.UciScore;
import com.kingfischer.engine.util.Perft;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Owns the game position and one asynchronous search worker. UCI input never
 * searches recursively.
 */
public final class Engine implements AutoCloseable {

    private Position position = new Position();
    private Network network;
    private String evalFile = Network.DEFAULT_FILE;
    private TranspositionTable tt = new TranspositionTable(16);
    private HistoryTables history = new HistoryTables();
    private final EngineCallbacks output;
    private Thread worker;
    private Search search;
    private int multiPV = 1, overhead = 10;
    private boolean trace;

    public Engine(EngineCallbacks output) {
        this.output = output;
    }

    public Position position() {
        return position;
    }

    public void identify() {
        output.line("id name JengaFish 0.1.0");
        output.line("id author George Miller and Stockfish developers");
        output.line("option name Hash type spin default 16 min 1 max 512");
        output.line("option name Threads type spin default 1 min 1 max 1");
        output.line("option name Clear Hash type button");
        output.line("option name Ponder type check default false");
        output.line("option name MultiPV type spin default 1 min 1 max 256");
        output.line("option name Move Overhead type spin default 10 min 0 max 5000");
        output.line("option name EvalFile type string default " + Network.DEFAULT_FILE + "");
        output.line("option name SearchTrace type check default false");
        output.line("uciok");
    }

    public void setPosition(String line) {
        String[] tokens = line.trim().split("\\s+");
        int i = 1;
        String fen;
        if (i < tokens.length && tokens[i].equals("startpos")) {
            fen = Position.START;
            i++;
        } else if (i < tokens.length && tokens[i].equals("fen")) {
            i++;
            if (tokens.length < i + 6) {
                throw new IllegalArgumentException("FEN requires six fields");
            }
            fen = String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + 6));
            i += 6;
        } else {
            throw new IllegalArgumentException("Expected startpos or fen");
        }
        Position candidate = new Position(fen);
        if (i < tokens.length) {
            if (!tokens[i++].equals("moves")) {
                throw new IllegalArgumentException("Expected moves");
            }
            while (i < tokens.length) {
                candidate.make(candidate.parseMove(tokens[i++]));
            }
        }
        stopAndWait();
        position = candidate;
    }

    private Network network() throws IOException {
        if (network == null) {
            network
                    = evalFile.equals(Network.DEFAULT_FILE)
                    ? Network.bundled()
                    : Network.load(Path.of(evalFile));
        }
        return network;
    }

    public void setOption(String name, String value) throws IOException {
        stopAndWait();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "hash" ->
                tt = new TranspositionTable(integer(value, 1, 512));
            case "threads" ->
                integer(value, 1, 1);
            case "clear hash" ->
                tt.clear();
            case "multipv" ->
                multiPV = integer(value, 1, 256);
            case "move overhead" ->
                overhead = integer(value, 0, 5000);
            case "searchtrace" ->
                trace = bool(value);
            case "ponder" ->
                bool(value); // go ponder controls actual pondering, per UCI.
            case "evalfile" -> {
                Network candidate
                        = value.equals(Network.DEFAULT_FILE) ? Network.bundled() : Network.load(Path.of(value));
                network = candidate;
                evalFile = value;
                tt.clear();
            }
            default ->
                throw new IllegalArgumentException("Unsupported option: " + name);
        }
    }

    private static boolean bool(String s) {
        if (!s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Expected true or false");
        }
        return Boolean.parseBoolean(s);
    }

    private static int integer(String s, int min, int max) {
        int v = Integer.parseInt(s);
        if (v < min || v > max) {
            throw new IllegalArgumentException("Value must be " + min + ".." + max);
        }
        return v;
    }

    public void newGame() {
        stopAndWait();
        tt.clear();
        history = new HistoryTables();
    }

    public void go(SearchLimits limits) throws IOException {
        stopAndWait();
        for (String m : limits.searchMoves) {
            position.parseMove(m);
        }
        search
                = new Search(
                        position.copy(), network(), tt, history, limits, multiPV, overhead, trace, output);
        Search current = search;
        worker
                = new Thread(
                        () -> {
                            try {
                                current.run();
                            } catch (RuntimeException e) {
                                output.line("info string Search failed: " + e.getMessage());
                                output.line("bestmove 0000");
                            }
                        },
                        "JengaFish-search");
        worker.start();
    }

    public void stop() {
        if (search != null) {
            search.stop();
        }
    }

    public void ponderHit() {
        if (search != null) {
            search.ponderHit();
        }
    }

    public void await() {
        if (worker != null) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
            }
        }
    }

    public void stopAndWait() {
        stop();
        await();
        worker = null;
        search = null;
    }

    public void evaluate() throws IOException {
        AccumulatorStack acc = new AccumulatorStack(network());
        long raw = acc.raw(position);
        int value = Evaluation.evaluate(position, acc);
        output.line(
                "info string NNUE psqt "
                + (int) (raw >> 32)
                + " positional "
                + (int) raw
                + " raw "
                + ((int) (raw >> 32) + (int) raw)
                + " evaluation "
                + value
                + " cp "
                + UciScore.cp(value, position)
                + " (side to move)");
    }

    public void perft(int depth, boolean divide) {
        stopAndWait();
        if (depth < 0 || depth > 12) {
            throw new IllegalArgumentException("Perft depth must be 0..12");
        }
        long start = System.nanoTime(), total = 0;
        Perft perft = new Perft();
        if (divide && depth > 0) {
            int[] moves = new int[256];
            int n = MoveGenerator.legal(position, moves);
            for (int i = 0; i < n; i++) {
                position.make(moves[i]);
                long nodes;
                try {
                    nodes = perft.count(position, depth - 1);
                } finally {
                    position.undo();
                }
                total += nodes;
                output.line(Move.uci(moves[i]) + ": " + nodes);
            }
        } else {
            total = perft.count(position, depth);
        }
        long ms = Math.max(1, (System.nanoTime() - start) / 1_000_000);
        output.line("Nodes searched: " + total);
        output.line("info string perft time " + ms + " nps " + (total * 1000 / ms));
    }

    public void benchmark(int depth) throws IOException {
        stopAndWait();
        if (depth < 1 || depth > 12) {
            throw new IllegalArgumentException("Bench depth must be 1..12");
        }
        String[] fens = {
            Position.START,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        };
        long nodes = 0, ms = 0;
        for (String fen : fens) {
            SearchLimits l = new SearchLimits();
            l.depth = depth;
            Search s
                    = new Search(
                            new Position(fen),
                            network(),
                            new TranspositionTable(16),
                            new HistoryTables(),
                            l,
                            1,
                            0,
                            false,
                            line -> {
                            });
            SearchResult r = s.run();
            nodes += r.nodes();
            ms += r.milliseconds();
        }
        output.line(
                "info string bench positions 3 depth "
                + depth
                + " nodes "
                + nodes
                + " time "
                + ms
                + " nps "
                + nodes * 1000 / Math.max(1, ms));
    }

    public void close() {
        stopAndWait();
    }
}
