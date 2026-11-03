// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine;

import com.kingfischer.engine.search.SearchResult;
import com.kingfischer.engine.search.Search;
import com.kingfischer.engine.search.SearchLimits;
import com.kingfischer.engine.nnue.Network;
import com.kingfischer.engine.nnue.AccumulatorStack;
import com.kingfischer.engine.movegen.MoveGenerator;
import com.kingfischer.engine.movegen.StaticExchange;
import com.kingfischer.engine.chess.Position;
import static org.junit.jupiter.api.Assertions.*;

import com.kingfischer.engine.bitboard.Attacks;
import com.kingfischer.engine.eval.Evaluation;
import com.kingfischer.engine.history.HistoryTables;
import com.kingfischer.engine.time.TimeManager;
import com.kingfischer.engine.tt.TranspositionTable;
import com.kingfischer.engine.uci.UciEngine;
import com.kingfischer.engine.util.Perft;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EngineTest {

    static Network network;

    @BeforeAll
    static void loadNetwork() throws Exception {
        network = Network.bundled();
    }

    @Test
    void fenRoundTripAndInvalidInput() {
        assertEquals(Position.START, new Position().fen());
        for (String fen
                : List.of(
                        "8/8/8/8/8/8/8/8 w - - 0 1",
                        "9/8/8/8/8/8/8/K6k w - - 0 1",
                        "8/8/8/8/8/8/8/K6k x - - 0 1",
                        "8/8/8/8/8/8/8/K6k w K - 0 1")) {
            assertThrows(IllegalArgumentException.class, () -> new Position(fen));
        }
    }

    @ParameterizedTest
    @CsvSource({"1,20", "2,400", "3,8902", "4,197281", "5,4865609", "6,119060324"})
    void startingPerft(int depth, long expected) {
        assertEquals(expected, new Perft().count(new Position(), depth));
    }

    @Test
    void difficultPerft() {
        assertEquals(
                4085603,
                new Perft()
                        .count(
                                new Position(
                                        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"),
                                4));
        assertEquals(
                674624, new Perft().count(new Position("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 5));
        assertEquals(
                422333,
                new Perft()
                        .count(
                                new Position("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"),
                                4));
        assertEquals(
                2103487,
                new Perft()
                        .count(new Position("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 4));
    }

    @Test
    void randomizedMakeUndoRestoresEverything() {
        Random rng = new Random(7);
        int[] moves = new int[256];
        for (int game = 0; game < 30; game++) {
            Position p = new Position();
            List<String> fens = new ArrayList<>();
            List<Long> keys = new ArrayList<>();
            List<Long> pawns = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int n = MoveGenerator.legal(p, moves);
                if (n == 0) {
                    break;
                }
                fens.add(p.fen());
                keys.add(p.key);
                pawns.add(p.pawnKey);
                p.make(moves[rng.nextInt(n)]);
                assertEquals(p.recomputeKey(), p.key);
                checkBitboards(p);
            }
            for (int i = fens.size() - 1; i >= 0; i--) {
                p.undo();
                assertEquals(fens.get(i), p.fen());
                assertEquals(keys.get(i).longValue(), p.key);
                assertEquals(pawns.get(i).longValue(), p.pawnKey);
                checkBitboards(p);
            }
        }
    }

    private static void checkBitboards(Position p) {
        long[] pieces = new long[16];
        for (int s = 0; s < 64; s++) {
            if (p.board[s] != 0) {
                pieces[p.board[s]] |= 1L << s;
            }
        }
        assertArrayEquals(pieces, p.pieces);
        long w = 0, b = 0;
        for (int t = 1; t <= 6; t++) {
            w |= pieces[t];
            b |= pieces[t | 8];
        }
        assertEquals(w, p.colors[0]);
        assertEquals(b, p.colors[1]);
    }

    @Test
    void nullMoveAndRepetition() {
        Position p = new Position();
        long key = p.key;
        p.make(0);
        assertEquals(p.recomputeKey(), p.key);
        p.undo();
        assertEquals(key, p.key);
        assertEquals(Position.START, p.fen());
        String[] cycle = {"g1f3", "g8f6", "f3g1", "f6g8"};
        for (String m : cycle) {
            p.make(p.parseMove(m));
        }
        assertFalse(p.repetition(0));
        assertTrue(p.repetition(5));
        for (String m : cycle) {
            p.make(p.parseMove(m));
        }
        assertTrue(p.repetition(0));
    }

    @Test
    void epCanonicalHashAndPinnedEp() {
        Position p = new Position();
        p.make(p.parseMove("e2e4"));
        assertEquals(p.key, new Position(p.fen().replace("e3", "-")).key);
        p = new Position("8/6bb/8/8/R1pP2k1/4P3/P7/K7 b - d3 0 1");
        Position same = new Position(p.fen().replace("d3", "-"));
        assertEquals(p.key, same.key);
        Position pinned = p;
        assertThrows(IllegalArgumentException.class, () -> pinned.parseMove("c4d3"));
    }

    @Test
    void promotionCastlingAndEp() {
        String[] fens = {
            "7k/P7/8/8/8/8/8/7K w - - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "7k/8/8/3pP3/8/8/8/7K w - d6 0 1"
        };
        String[] moves = {"a7a8n", "e1c1", "e5d6"};
        for (int i = 0; i < 3; i++) {
            Position p = new Position(fens[i]);
            long h = p.key;
            p.make(p.parseMove(moves[i]));
            assertEquals(p.recomputeKey(), p.key);
            p.undo();
            assertEquals(fens[i], p.fen());
            assertEquals(h, p.key);
        }
    }

    @Test
    void attackGeometry() {
        assertEquals(2, Long.bitCount(Attacks.KNIGHT[0]));
        assertEquals(8, Long.bitCount(Attacks.KNIGHT[27]));
        assertEquals(14, Long.bitCount(Attacks.rook(27, 0)));
        assertEquals(13, Long.bitCount(Attacks.bishop(27, 0)));
        assertEquals(3, Long.bitCount(Attacks.KING[0]));
    }

    @Test
    void staticExchange() {
        Position p = new Position("7k/8/4p3/3p4/4P3/8/8/K7 w - - 0 1");
        assertEquals(0, StaticExchange.see(p, p.parseMove("e4d5"), new int[32]));
        p = new Position("7k/8/8/3q4/4P3/8/8/K7 w - - 0 1");
        assertEquals(Evaluation.VALUE[5], StaticExchange.see(p, p.parseMove("e4d5"), new int[32]));
    }

    @Test
    void ttBoundsAndMateDistance() {
        TranspositionTable t = new TranspositionTable(1);
        t.store(123, 9, 31993, 50, 100, TranspositionTable.LOWER, 3);
        int i = t.probe(123);
        assertEquals(9, t.depth(i));
        assertEquals(31993, t.score(i, 3));
        assertEquals(31991, t.score(i, 5));
        assertEquals(100, t.move(i));
        assertEquals(TranspositionTable.LOWER, t.bound(i));
        t.clear();
        assertEquals(-1, t.probe(123));
    }

    @Test
    void historyGravityIsBounded() {
        Position p = new Position();
        HistoryTables h = new HistoryTables();
        int m = p.parseMove("e2e4");
        for (int i = 0; i < 10000; i++) {
            h.rewardQuiet(p, m, 0, 100000);
        }
        assertTrue(h.quiet(p, m, 0) > 0);
        assertTrue(h.quiet(p, m, 0) <= 2 * 7183 + 8192);
    }

    @Test
    void networkKnownOracleValues() {
        Position p = new Position();
        AccumulatorStack a = new AccumulatorStack(network);
        assertEquals(-1, (int) a.raw(p));
        p.make(p.parseMove("e2e4"));
        a.push();
        long raw = a.raw(p);
        assertEquals(-85, (int) (raw >> 32));
        assertEquals(25, (int) raw);
        assertEquals(-76, Evaluation.evaluate(p, a));
    }

    @Test
    void incrementalNnueMatchesFullRefreshAcrossMakeUndo() {
        Position p = new Position();
        AccumulatorStack acc = new AccumulatorStack(network);
        Random r = new Random(23);
        int[] moves = new int[256];
        for (int i = 0; i < 100; i++) {
            assertTrue(acc.equalsFresh(p), p.fen());
            int n = MoveGenerator.legal(p, moves);
            if (n == 0) {
                break;
            }
            p.make(moves[r.nextInt(n)]);
            acc.push();
        }
        while (p.ply > 0) {
            acc.pop();
            p.undo();
            assertTrue(acc.equalsFresh(p), p.fen());
        }
    }

    @Test
    void incrementalSpecialMoves() {
        String[][] cases = {
            {"7k/P7/8/8/8/8/8/7K w - - 0 1", "a7a8q"},
            {"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"},
            {"7k/8/8/3pP3/8/8/8/7K w - d6 0 1", "e5d6"}
        };
        for (String[] c : cases) {
            Position p = new Position(c[0]);
            AccumulatorStack a = new AccumulatorStack(network);
            a.ensure(p);
            p.make(p.parseMove(c[1]));
            a.push();
            assertTrue(a.equalsFresh(p));
            a.pop();
            p.undo();
            assertTrue(a.equalsFresh(p));
        }
    }

    @Test
    void corruptNetworkRejected() throws Exception {
        Path f = Files.createTempFile("bad-net", ".nnue");
        try {
            Files.write(f, new byte[12]);
            assertThrows(IOException.class, () -> Network.load(f));
        } finally {
            Files.delete(f);
        }
    }

    private static SearchResult search(Position p, String command) {
        SearchLimits l = SearchLimits.parse(command);
        return new Search(
                p, network, new TranspositionTable(1), new HistoryTables(), l, 1, 0, false, line -> {
                })
                .run();
    }

    @Test
    void findsMateAndPreservesPosition() {
        Position p = new Position("7k/5Q2/6K1/8/8/8/8/8 w - - 0 1");
        String fen = p.fen();
        SearchResult r = search(p, "go depth 4");
        assertEquals(fen, p.fen());
        assertTrue(r.score() > 31000);
        p.make(r.bestMove());
        assertTrue(p.inCheck());
        assertEquals(0, MoveGenerator.legal(p, new int[256]));
    }

    @Test
    void stalemateAndCheckmate() {
        assertEquals(
                0, search(new Position("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"), "go depth 3").bestMove());
        SearchResult r = search(new Position("7k/6Q1/6K1/8/8/8/8/8 b - - 100 1"), "go depth 3");
        assertEquals(0, r.bestMove());
        assertEquals(-32000, r.score());
    }

    @Test
    void drawAndNodesLimits() {
        Position p = new Position();
        SearchResult r = search(p, "go nodes 100");
        assertEquals(100, r.nodes());
        assertNotEquals(0, r.bestMove());
        assertEquals(Position.START, p.fen());
        assertEquals(0, search(new Position("7k/8/8/8/8/8/8/K7 w - - 0 1"), "go depth 3").score());
    }

    @Test
    void goParsingAndTimeBudget() {
        SearchLimits l
                = SearchLimits.parse(
                        "go wtime 60000 btime 55000 winc 1000 movestogo 20 searchmoves e2e4 d2d4 depth 7");
        assertEquals(7, l.depth);
        assertEquals(List.of("e2e4", "d2d4"), l.searchMoves);
        TimeManager t = new TimeManager(l, 0, 0, 10);
        assertTrue(t.optimum > 0);
        assertTrue(t.maximum >= t.optimum);
        assertTrue(t.maximum <= 59990);
        assertThrows(IllegalArgumentException.class, () -> SearchLimits.parse("go movetime -10"));
    }

    @Test
    void uciHandshakeAndTransactionalPosition() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (UciEngine uci = new UciEngine(bytes)) {
            uci.command("uci");
            uci.command("isready");
            uci.command("position startpos moves e2e4 e7e5");
            uci.command("position startpos moves e2e5");
            uci.command("d");
        }
        String text = bytes.toString();
        assertTrue(text.contains("uciok"));
        assertTrue(text.contains("readyok"));
        assertTrue(text.contains("Error: Illegal move"));
        assertTrue(text.contains("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "3Q4/k7/8/1K6/8/8/8/8 w - - 0 1",
                "k7/3Q4/8/3K4/8/8/8/8 w - - 0 1",
                "8/8/8/8/Q7/3K4/8/1k6 w - - 0 1",
                "5Q2/8/8/8/8/8/3K4/k7 w - - 0 1"
            })
    void stockfishVerifiedMateInTwo(String fen) {
        Position p = new Position(fen);
        SearchResult result = search(p, "go depth 6");
        assertEquals(31997, result.score(), fen);
        assertNotEquals(0, result.bestMove());
        assertEquals(fen, p.fen());
    }
}
