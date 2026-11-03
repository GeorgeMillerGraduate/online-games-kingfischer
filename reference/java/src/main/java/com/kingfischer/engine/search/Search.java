// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.search;

import com.kingfischer.engine.nnue.Network;
import com.kingfischer.engine.nnue.AccumulatorStack;
import com.kingfischer.engine.movegen.MoveGenerator;
import com.kingfischer.engine.movegen.MovePicker;
import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.engine.EngineCallbacks;
import com.kingfischer.engine.eval.Evaluation;
import com.kingfischer.engine.history.HistoryTables;
import com.kingfischer.engine.time.TimeManager;
import com.kingfischer.engine.tt.TranspositionTable;
import com.kingfischer.engine.uci.UciScore;
import java.util.*;

/**
 * Iterative deepening / aspiration PVS, quiescence and selective search.
 * Selected relationships adapted from Stockfish search.cpp, not a line-for-line
 * parity port. Copyright (C) 2004-2026 The Stockfish developers (see
 * STOCKFISH-AUTHORS). See docs/PORT-STATUS.md for precisely which mechanisms
 * differ.
 */
public final class Search {

    private static final int INF = 32700, MATE = 32000, MAX_PLY = 240;
    private final Position p;
    private final TranspositionTable tt;
    private final HistoryTables history;
    private final AccumulatorStack acc;
    private final SearchLimits limits;
    private final TimeManager time;
    private final EngineCallbacks output;
    private final int multiPV;
    private final boolean trace;
    private final MovePicker[] pickers = new MovePicker[256], excludedPickers = new MovePicker[256];
    private final int[][] pv = new int[256][256];
    private final int[] pvLength = new int[256],
            staticEval = new int[256],
            previous = new int[256],
            reductions = new int[256];
    private volatile boolean stopped, pondering;
    private volatile long timedStart;
    private long start,
            nodes,
            qnodes,
            ttHits,
            cutoffs,
            nullAttempts,
            nullCuts,
            lmr,
            probCuts,
            singular,
            seePrunes,
            historyPrunes;
    private int selDepth, rootDepth, rootDelta = 64, nmpMinPly;
    private static final Abort ABORT = new Abort();

    private static final class Abort extends RuntimeException {

        Abort() {
            super(null, null, false, false);
        }
    }

    public Search(
            Position position,
            Network net,
            TranspositionTable tt,
            HistoryTables history,
            SearchLimits limits,
            int multiPV,
            int overhead,
            boolean trace,
            EngineCallbacks output) {
        this.p = position;
        this.tt = tt;
        this.history = history;
        this.limits = limits;
        this.multiPV = multiPV;
        this.trace = trace;
        this.output = output;
        acc = new AccumulatorStack(net);
        time = new TimeManager(limits, p.side, (p.fullmove - 1) * 2 + p.side, overhead);
        pondering = limits.ponder;
        for (int i = 0; i < 256; i++) {
            pickers[i] = new MovePicker();
            excludedPickers[i] = new MovePicker();
            reductions[i] = i == 0 ? 0 : (int) (2872 / 128.0 * Math.log(i));
        }
    }

    public void stop() {
        stopped = true;
        synchronized (this) {
            notifyAll();
        }
    }

    public void ponderHit() {
        timedStart = System.nanoTime();
        pondering = false;
        synchronized (this) {
            notifyAll();
        }
    }

    private long elapsed() {
        return Math.max(1, (System.nanoTime() - start) / 1_000_000);
    }

    private void tick(int ply, boolean q) {
        if (stopped
                || nodes >= limits.nodes
                || (!pondering
                && time.maximum != Long.MAX_VALUE
                && (System.nanoTime() - timedStart) / 1_000_000 >= time.maximum)) {
            throw ABORT;
        }
        nodes++;
        if (q) {
            qnodes++;
        }
        selDepth = Math.max(selDepth, ply);
    }

    private long ttKey() {
        return p.key ^ ((long) p.halfmove * 0x9e3779b97f4a7c15L);
    }

    private void make(int move, int ply) {
        previous[ply + 1] = move == 0 ? 0 : p.board[Move.from(move)] * 64 + Move.to(move);
        p.make(move);
        acc.push();
    }

    private void undo() {
        acc.pop();
        p.undo();
    }

    private boolean draw(int ply) {
        return p.halfmove >= 100 || p.repetition(ply) || p.insufficientMaterial();
    }

    private void updatePv(int ply, int move) {
        pv[ply][0] = move;
        int n = pvLength[ply + 1];
        System.arraycopy(pv[ply + 1], 0, pv[ply], 1, n);
        pvLength[ply] = n + 1;
    }

    public SearchResult run() {
        start = timedStart = System.nanoTime();
        tt.newSearch();
        int[] legal = new int[256];
        int count = MoveGenerator.legal(p, legal);
        List<RootMove> roots = new ArrayList<>();
        Set<Integer> allowed = new HashSet<>();
        if (limits.restrictMoves) {
            for (String m : limits.searchMoves) {
                allowed.add(p.parseMove(m));
            }
        }
        for (int i = 0; i < count; i++) {
            if (!limits.restrictMoves || allowed.contains(legal[i])) {
                roots.add(new RootMove(legal[i]));
            }
        }
        int best = roots.isEmpty() ? 0 : roots.get(0).move,
                ponder = 0,
                bestScore = 0,
                completeDepth = 0;
        if (roots.isEmpty()) {
            bestScore = count == 0 && p.inCheck() ? -MATE : 0;
            output.line(
                    "info depth 0 score " + UciScore.text(bestScore, p) + " nodes 0 time " + elapsed());
        } else {
            try {
                if (draw(0)) {
                    bestScore = 0;
                    output.line(
                            "info depth 0 score cp 0 nodes 0 time " + elapsed() + " pv " + Move.uci(best));
                } else {
                    for (rootDepth = 1; rootDepth <= limits.depth; rootDepth++) {
                        int window = rootDepth < 4 ? INF : 32,
                                alpha = rootDepth < 4 ? -INF : Math.max(-INF, bestScore - window),
                                beta = rootDepth < 4 ? INF : Math.min(INF, bestScore + window);
                        while (true) {
                            rootDelta = Math.max(1, beta - alpha);
                            rootSearch(roots, rootDepth, alpha, beta);
                            roots.sort(Comparator.comparingInt((RootMove r) -> r.score).reversed());
                            int score = roots.get(0).score;
                            if (multiPV > 1 || (score > alpha && score < beta)) {
                                break;
                            }
                            window = Math.min(INF, window * 2);
                            alpha = Math.max(-INF, score - window);
                            beta = Math.min(INF, score + window);
                        }
                        RootMove r = roots.get(0);
                        best = r.move;
                        ponder = r.length > 1 ? r.pv[1] : 0;
                        bestScore = r.score;
                        completeDepth = rootDepth;
                        for (int i = 0; i < Math.min(multiPV, roots.size()); i++) {
                            report(roots.get(i), i + 1);
                        }
                        if (!pondering
                                && !limits.infinite
                                && time.optimum != Long.MAX_VALUE
                                && (System.nanoTime() - timedStart) / 1_000_000 >= time.optimum) {
                            break;
                        }
                        if (limits.mate > 0 && bestScore > 31000 && (MATE - bestScore + 1) / 2 <= limits.mate) {
                            break;
                        }
                    }
                }
            } catch (Abort ignored) {
                /* Only fully completed iterations replace the fallback bestmove. */
            }
        }
        if ((pondering || limits.infinite) && !stopped) {
            synchronized (this) {
                while ((pondering || limits.infinite) && !stopped)
          try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopped = true;
                }
            }
        }
        if (trace) {
            output.line(
                    "info string trace nodes="
                    + nodes
                    + " qnodes="
                    + qnodes
                    + " ttHits="
                    + ttHits
                    + " cutoffs="
                    + cutoffs
                    + " nullAttempts="
                    + nullAttempts
                    + " nullCutoffs="
                    + nullCuts
                    + " lmr="
                    + lmr
                    + " probCut="
                    + probCuts
                    + " singular="
                    + singular
                    + " seePrunes="
                    + seePrunes
                    + " historyPrunes="
                    + historyPrunes
                    + " nnue="
                    + acc.evaluations
                    + " featureUpdates="
                    + acc.featureUpdates);
        }
        output.line("bestmove " + Move.uci(best) + (ponder == 0 ? "" : " ponder " + Move.uci(ponder)));
        return new SearchResult(best, ponder, bestScore, completeDepth, nodes, elapsed());
    }

    private void report(RootMove r, int index) {
        StringBuilder b
                = new StringBuilder("info depth ")
                        .append(rootDepth)
                        .append(" seldepth ")
                        .append(selDepth)
                        .append(" multipv ")
                        .append(index)
                        .append(" score ")
                        .append(UciScore.text(r.score, p))
                        .append(" nodes ")
                        .append(nodes)
                        .append(" nps ")
                        .append(nodes * 1000 / elapsed())
                        .append(" hashfull ")
                        .append(tt.hashfull())
                        .append(" time ")
                        .append(elapsed())
                        .append(" pv");
        for (int j = 0; j < r.length; j++) {
            b.append(' ').append(Move.uci(r.pv[j]));
        }
        output.line(b.toString());
    }

    private void rootSearch(List<RootMove> roots, int depth, int alpha, int beta) {
        tick(0, false);
        acc.ensure(p);
        int index = 0;
        for (RootMove r : roots) {
            long before = nodes;
            make(r.move, 0);
            int value;
            try {
                if (multiPV > 1) {
                    value = -search(depth - 1, 1, -INF, INF, true, false, 0, true);
                } else if (index == 0) {
                    value = -search(depth - 1, 1, -beta, -alpha, true, false, 0, true);
                } else {
                    value = -search(depth - 1, 1, -alpha - 1, -alpha, false, true, 0, true);
                    if (value > alpha) {
                        value = -search(depth - 1, 1, -beta, -alpha, true, false, 0, true);
                    }
                }
                r.score = value;
                r.length = pvLength[1] + 1;
                r.pv[0] = r.move;
                System.arraycopy(pv[1], 0, r.pv, 1, pvLength[1]);
                r.nodes = nodes - before;
            } finally {
                undo();
            }
            index++;
            if (multiPV == 1) {
                if (value > alpha) {
                    alpha = value;
                }
                if (alpha >= beta) {
                    break;
                }
            }
        }
        // A cutoff leaves unsearched roots at a lower bound priority for this retry.
        if (multiPV == 1 && index < roots.size()) {
            for (int i = index; i < roots.size(); i++) {
                roots.get(i).score = -INF;
            }
        }
    }

    private int search(
            int depth,
            int ply,
            int alpha,
            int beta,
            boolean pvNode,
            boolean cutNode,
            int excluded,
            boolean allowNull) {
        if (depth <= 0) {
            return qsearch(ply, alpha, beta);
        }
        tick(ply, false);
        pvLength[ply] = 0;
        boolean check = p.inCheck();
        MovePicker mp = excluded == 0 ? pickers[ply] : excludedPickers[ply];
        long key = ttKey();
        int ti = excluded == 0 ? tt.probe(key) : -1,
                ttMove = tt.move(ti),
                ttValue = ti < 0 ? 0 : tt.score(ti, ply);
        if (ti >= 0) {
            ttHits++;
        }
        mp.init(p, ttMove, history, previous[ply], false);
        if (mp.count == 0) {
            return check ? -MATE + ply : 0;
        }
        if (draw(ply)) {
            return 0;
        }
        if (ply >= MAX_PLY) {
            return check ? 0 : Evaluation.evaluate(p, acc);
        }
        alpha = Math.max(alpha, -MATE + ply);
        beta = Math.min(beta, MATE - ply - 1);
        if (alpha >= beta) {
            return alpha;
        }
        int originalAlpha = alpha;
        if (!pvNode && ti >= 0 && tt.depth(ti) >= depth && p.halfmove < 96) {
            int bound = tt.bound(ti);
            if (bound == TranspositionTable.EXACT
                    || (bound == TranspositionTable.LOWER && ttValue >= beta)
                    || (bound == TranspositionTable.UPPER && ttValue <= alpha)) {
                return ttValue;
            }
        }
        int raw = check ? 0 : ti >= 0 ? tt.eval(ti) : Evaluation.evaluate(p, acc);
        int eval = raw + history.correction(p);
        staticEval[ply] = eval;
        boolean improving = ply >= 2 && eval > staticEval[ply - 2];
        if (!check && excluded == 0) {
            if (!pvNode && depth <= 3 && eval < alpha - 482 * depth) {
                return qsearch(ply, alpha, beta);
            }
            if (!pvNode
                    && depth < 9
                    && Math.abs(beta) < 30000
                    && Math.abs(eval) < 30000
                    && (!ttMoveIsQuiet(ttMove))) {
                int mult = Math.min(45 + depth * 4, 85) - (ti < 0 ? 20 : 0);
                int margin = mult * depth - (improving ? 2789 * mult / 1024 : 0);
                if (eval - margin >= beta) {
                    return (661 * beta + 363 * eval) / 1024;
                }
            }
            if (allowNull
                    && !pvNode
                    && cutNode
                    && depth >= 3
                    && ply >= nmpMinPly
                    && previous[ply] != 0
                    && p.nonPawnMaterial()
                    && beta >= -2000
                    && Math.abs(beta) < 30000
                    && eval >= beta - 13 * depth - (improving ? 47 : 0) + 365) {
                nullAttempts++;
                int reduction = 7 + depth / 3 + Math.max(0, (eval - beta) / 256);
                make(0, ply);
                int value;
                try {
                    value = -search(depth - reduction, ply + 1, -beta, -beta + 1, false, false, 0, false);
                } finally {
                    undo();
                }
                if (value >= beta && value < 31000) {
                    if (depth < 16 || nmpMinPly != 0) {
                        nullCuts++;
                        return value;
                    }
                    nmpMinPly = ply + 3 * Math.max(0, depth - reduction) / 4;
                    int verify;
                    try {
                        verify = search(depth - reduction, ply, beta - 1, beta, false, false, 0, false);
                    } finally {
                        nmpMinPly = 0;
                    }
                    if (verify >= beta) {
                        nullCuts++;
                        return value;
                    }
                    mp.init(p, ttMove, history, previous[ply], false);
                }
            }
            if (!pvNode && depth >= 6 && ttMove == 0) {
                depth--;
            }
            int probBeta = beta + 241 - (improving ? 64 : 0);
            if (!pvNode && depth >= 5 && Math.abs(beta) < 30000 && !(ti >= 0 && ttValue < probBeta)) {
                MovePicker captures = excludedPickers[ply];
                captures.init(p, ttMove, history, previous[ply], true);
                int m;
                while ((m = captures.next()) != 0) {
                    if (captures.lastSee < probBeta - eval) {
                        continue;
                    }
                    make(m, ply);
                    int v;
                    try {
                        v = -qsearch(ply + 1, -probBeta, -probBeta + 1);
                        if (v >= probBeta) {
                            v
                                    = -search(
                                            depth - (improving ? 5 : 3),
                                            ply + 1,
                                            -probBeta,
                                            -probBeta + 1,
                                            false,
                                            !cutNode,
                                            0,
                                            true);
                        }
                    } finally {
                        undo();
                    }
                    if (v >= probBeta && Math.abs(v) < 31000) {
                        probCuts++;
                        tt.store(key, depth - 3, v, raw, m, TranspositionTable.LOWER, ply);
                        return v - (probBeta - beta);
                    }
                }
            }
        }
        int best = -INF, bestMove = 0, searched = 0, move;
        while ((move = mp.next()) != 0) {
            if (move == excluded) {
                continue;
            }
            boolean capture = MoveGenerator.capture(p, move),
                    promotion = Move.promotion(move) != 0,
                    quiet = !capture && !promotion;
            int historyScore = quiet ? history.quiet(p, move, previous[ply]) : history.capture(p, move);
            int extension = 0;
            if (excluded == 0
                    && move == ttMove
                    && depth >= 7
                    && ti >= 0
                    && tt.depth(ti) >= depth - 3
                    && tt.bound(ti) == TranspositionTable.LOWER
                    && Math.abs(ttValue) < 30000
                    && ply < rootDepth * 2) {
                int singularBeta = ttValue - 59 * depth / 63;
                int v
                        = search(
                                (depth - 1) / 2, ply, singularBeta - 1, singularBeta, false, cutNode, move, false);
                pvLength[ply] = 0;
                if (v < singularBeta) {
                    extension = 1;
                    singular++;
                }
            }
            int nextDepth = depth - 1 + extension;
            make(move, ply);
            int value;
            try {
                boolean givesCheck = p.inCheck();
                if (!pvNode
                        && !check
                        && !givesCheck
                        && searched > 0
                        && best > -30000
                        && quiet
                        && depth <= 5
                        && excluded == 0) {
                    if (mp.index >= (3 + depth * depth) / (improving ? 1 : 2)
                            || historyScore < -4136 * depth) {
                        historyPrunes++;
                        continue;
                    }
                    if (eval + 119 * depth + 254 <= alpha) {
                        historyPrunes++;
                        continue;
                    }
                }
                if (!pvNode
                        && !check
                        && !givesCheck
                        && searched > 0
                        && capture
                        && mp.lastSee < -177 * depth
                        && depth <= 5
                        && excluded == 0) {
                    seePrunes++;
                    continue;
                }
                int reduction = 0;
                if (depth >= 3 && searched >= 2 && quiet && !check && !givesCheck) {
                    int scale = reductions[Math.min(depth, 255)] * reductions[Math.min(mp.index, 255)];
                    int r
                            = scale
                            - (beta - alpha) * 577 / rootDelta
                            + (!improving ? scale * 197 / 512 : 0)
                            + 982
                            + 697
                            - mp.index * 65;
                    if (pvNode) {
                        r -= 4027;
                    }
                    if (cutNode) {
                        r += 4026;
                    }
                    if (move == ttMove) {
                        r -= 2179;
                    }
                    r -= historyScore * 439 / 4096;
                    reduction = Math.max(0, Math.min(nextDepth - 1, r / 1024));
                    if (reduction > 0) {
                        lmr++;
                    }
                }
                if (searched == 0) {
                    value = -search(nextDepth, ply + 1, -beta, -alpha, pvNode, !cutNode, 0, true);
                } else {
                    value = -search(nextDepth - reduction, ply + 1, -alpha - 1, -alpha, false, true, 0, true);
                    if (reduction > 0 && value > alpha) {
                        value = -search(nextDepth, ply + 1, -alpha - 1, -alpha, false, !cutNode, 0, true);
                    }
                    if (pvNode && value > alpha && value < beta) {
                        value = -search(nextDepth, ply + 1, -beta, -alpha, true, false, 0, true);
                    }
                }
            } finally {
                undo();
            }
            searched++;
            if (value > best) {
                best = value;
                bestMove = move;
            }
            if (value > alpha) {
                alpha = value;
                updatePv(ply, move);
            }
            if (alpha >= beta) {
                cutoffs++;
                int bonus = Math.min(2000, 131 * depth);
                if (quiet) {
                    history.rewardQuiet(p, move, previous[ply], bonus);
                    for (int i = 0; i < mp.index - 1; i++) {
                        int earlier = mp.moves[i];
                        if (!MoveGenerator.capture(p, earlier) && Move.promotion(earlier) == 0) {
                            history.rewardQuiet(p, earlier, previous[ply], -bonus / 2);
                        }
                    }
                } else {
                    history.rewardCapture(p, move, bonus);
                }
                break;
            }
        }
        if (searched == 0) {
            return excluded != 0 ? originalAlpha : check ? -MATE + ply : 0;
        }
        if (excluded == 0) {
            int bound
                    = best >= beta
                            ? TranspositionTable.LOWER
                            : best > originalAlpha ? TranspositionTable.EXACT : TranspositionTable.UPPER;
            tt.store(key, depth, best, raw, bestMove, bound, ply);
            if (!check
                    && Math.abs(best) < 30000
                    && !MoveGenerator.capture(p, bestMove)
                    && (bound == TranspositionTable.EXACT
                    || (bound == TranspositionTable.LOWER && best > eval)
                    || (bound == TranspositionTable.UPPER && best < eval))) {
                history.correct(p, best - raw, depth);
            }
        }
        return best;
    }

    private boolean ttMoveIsQuiet(int m) {
        return m != 0 && !MoveGenerator.capture(p, m) && Move.promotion(m) == 0;
    }

    private int qsearch(int ply, int alpha, int beta) {
        tick(ply, true);
        pvLength[ply] = 0;
        boolean check = p.inCheck();
        MovePicker mp = pickers[ply];
        long key = ttKey();
        int ti = tt.probe(key);
        mp.init(p, tt.move(ti), history, previous[ply], !check);
        if (mp.legalCount == 0) {
            return check ? -MATE + ply : 0;
        }
        if (draw(ply)) {
            return 0;
        }
        if (ply >= MAX_PLY) {
            return check ? 0 : Evaluation.evaluate(p, acc);
        }
        int original = alpha;
        // PV qsearch avoids TT cutoffs so its reported principal variation stays complete.
        if (beta - alpha == 1 && ti >= 0) {
            int v = tt.score(ti, ply), b = tt.bound(ti);
            if (b == TranspositionTable.EXACT
                    || (b == TranspositionTable.LOWER && v >= beta)
                    || (b == TranspositionTable.UPPER && v <= alpha)) {
                ttHits++;
                return v;
            }
        }
        int raw = check ? 0 : Evaluation.evaluate(p, acc),
                best = check ? -INF : raw + history.correction(p),
                bestMove = 0;
        if (!check) {
            if (best >= beta) {
                return best;
            }
            alpha = Math.max(alpha, best);
        }
        int move;
        while ((move = mp.next()) != 0) {
            boolean prune = !check && Move.promotion(move) == 0 && mp.lastSee < -74;
            make(move, ply);
            int value;
            try {
                if (prune && !p.inCheck()) {
                    seePrunes++;
                    continue;
                }
                value = -qsearch(ply + 1, -beta, -alpha);
            } finally {
                undo();
            }
            if (value > best) {
                best = value;
                bestMove = move;
            }
            if (value > alpha) {
                alpha = value;
                updatePv(ply, move);
            }
            if (alpha >= beta) {
                break;
            }
        }
        tt.store(
                key,
                0,
                best,
                raw,
                bestMove,
                best >= beta
                        ? TranspositionTable.LOWER
                        : best > original ? TranspositionTable.EXACT : TranspositionTable.UPPER,
                ply);
        return best;
    }
}
