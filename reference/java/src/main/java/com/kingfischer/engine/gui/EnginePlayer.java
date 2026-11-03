// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.search.SearchLimits;
import com.kingfischer.engine.search.Search;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.history.HistoryTables;
import com.kingfischer.engine.movegen.MoveGenerator;
import com.kingfischer.engine.nnue.Network;
import com.kingfischer.engine.tt.TranspositionTable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javafx.application.Platform;

/**
 * Searches snapshots off the event thread; generations reject cancelled
 * results.
 */
public final class EnginePlayer implements AutoCloseable {

    private final ExecutorService worker
            = Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "JengaFish-GUI-search");
                        t.setDaemon(true);
                        return t;
                    });
    private final AtomicLong generation = new AtomicLong();
    private volatile Search active;
    private Network network;

    public static SearchLimits limits(int strength) {
        double x = (Math.max(100, Math.min(3500, strength)) - 100) / 3400.0;
        SearchLimits l = new SearchLimits();
        l.depth = 2 + (int) (10 * x);
        l.nodes = 128 + (long) (499872 * x * x * x);
        l.moveTime = 150 + (long) (3350 * x * x);
        return l;
    }

    public void cancel() {
        generation.incrementAndGet();
        Search s = active;
        if (s != null) {
            s.stop();
        }
    }

    public void think(Position snapshot, int strength, IntConsumer complete, Consumer<String> error) {
        cancel();
        long ticket = generation.get();
        worker.submit(
                () -> {
                    try {
                        if (generation.get() != ticket) {
                            return;
                        }
                        if (network == null) {
                            network = Network.bundled();
                        }
                        if (generation.get() != ticket) {
                            return;
                        }
                        Search s
                        = new Search(
                                snapshot,
                                network,
                                new TranspositionTable(16),
                                new HistoryTables(),
                                limits(strength),
                                1,
                                10,
                                false,
                                line -> {
                                });
                        active = s;
                        if (generation.get() != ticket) {
                            s.stop();
                            return;
                        }
                        int move = s.run().bestMove();
                        double x = (Math.max(100, Math.min(3500, strength)) - 100) / 3400.0;
                        if (ThreadLocalRandom.current().nextDouble() < 0.8 * (1 - x) * (1 - x)) {
                            int[] legal = new int[256];
                            int n = MoveGenerator.legal(snapshot, legal);
                            if (n > 0) {
                                move = legal[ThreadLocalRandom.current().nextInt(n)];
                            }
                        }
                        final int chosen = move;
                        Platform.runLater(
                                () -> {
                                    if (generation.get() == ticket) {
                                        complete.accept(chosen);
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(
                                () -> {
                                    if (generation.get() == ticket) {
                                        error.accept(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                                    }
                                });
                    } finally {
                        active = null;
                    }
                });
    }

    public void close() {
        cancel();
        worker.shutdownNow();
    }
}
