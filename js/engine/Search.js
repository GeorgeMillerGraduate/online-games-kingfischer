// SPDX-License-Identifier: GPL-3.0-or-later
import { Move } from "./Move.js";
import { MoveGenerator as G } from "./MoveGenerator.js";
import { MovePicker } from "./MovePicker.js";
import { TranspositionTable as TT } from "./TranspositionTable.js";
import { HistoryTables } from "./HistoryTables.js";
import { Evaluator } from "./Evaluator.js";
const INF = 32700,
  MATE = 32000,
  ABORT = Symbol("deadline");
/** Iterative-deepening multi-PV alpha-beta/PVS in a Worker.
 * Browser adaptation of Java Search: qsearch, full-key TT, mate distance, SEE,
 * conservative null move and LMR. See PORT-STATUS.md for deliberately omitted heuristics.
 * Snapshots distinguish partial root work from completed depth; only completed depths
 * replace the fallback. Trace is bounded and consists of actual searched nodes.
 */
export class Search {
  constructor(p, evaluator, tt = new TT(), history = new HistoryTables()) {
    this.p = p;
    this.eval = evaluator;
    this.tt = tt;
    this.history = history;
  }
  tick(ply, q = false) {
    this.nodes++;
    if (q) this.qnodes++;
    this.selDepth = Math.max(this.selDepth, ply);
    if (
      (this.nodes & 31) === 0 &&
      (performance.now() >= this.deadline || this.nodes >= this.nodeLimit)
    )
      throw ABORT;
  }
  key() {
    return BigInt.asUintN(
      64,
      this.p.key ^ (BigInt(this.p.halfmove) * 0x9e3779b97f4a7c15n),
    );
  }
  make(m) {
    this.p.make(m);
    this.eval.push();
  }
  undo() {
    this.eval.pop();
    this.p.undo();
  }
  draw(ply) {
    return (
      this.p.halfmove >= 100 ||
      this.p.repetition(ply) ||
      this.p.insufficientMaterial()
    );
  }
  score(v) {
    return Evaluator.centipawns(v, this.p, !!this.eval.acc);
  }
  traceNode(ply, m, score, cutoff, reason) {
    if (ply > 2 || this.trace.length >= 48) return;
    this.trace.push({
      ply,
      move: Move.uci(m),
      score: this.score(score),
      cutoff,
      reason,
      parent: this.path
        .slice(0, ply)
        .map((m) => Move.uci(m))
        .join(" "),
    });
  }
  snapshot(partial = false) {
    const now = performance.now();
    return {
      depth: this.completeDepth,
      workingDepth: this.depth,
      partial,
      staticPreview: this.completeDepth === 0,
      nodes: this.nodes,
      qnodes: this.qnodes,
      ttHits: this.ttHits,
      cutoffs: this.cutoffs,
      lmr: this.lmr,
      nullCuts: this.nullCuts,
      seePrunes: this.seePrunes,
      selDepth: this.selDepth,
      elapsed: now - this.start,
      nps: Math.round((this.nodes * 1000) / Math.max(1, now - this.start)),
      hashfull: this.tt.hashfull(),
      rootKey: this.p.key.toString(16),
      bestMove: this.bestMove,
      score: this.score(this.bestScore),
      lines: this.completed.map((r) => ({ ...r, score: this.score(r.score) })),
      current: this.current,
      trace: this.trace.slice(),
      history: this.depthHistory.slice(),
      evaluation: this.rootEvaluation,
      ttEntry: this.rootTT,
    };
  }
  async run(
    { timeMs = 3000, maxDepth = 64, nodeLimit = Infinity, graphicsHz = 5 } = {},
    emit = () => {},
  ) {
    this.start = performance.now();
    this.deadline = this.start + Math.max(25, timeMs);
    this.nodeLimit = nodeLimit;
    this.nodes = 0;
    this.qnodes = 0;
    this.ttHits = 0;
    this.cutoffs = 0;
    this.lmr = 0;
    this.nullCuts = 0;
    this.seePrunes = 0;
    this.selDepth = 0;
    this.completeDepth = 0;
    this.depth = 0;
    this.bestScore = 0;
    this.trace = [];
    this.path = [];
    this.pv = Array.from({ length: 128 }, () => []);
    this.depthHistory = [];
    this.current = null;
    this.rootTT = null;
    this.completed = [];
    this.eval.reset();
    this.tt.newSearch();
    this.rootEvaluation = this.eval.breakdown(this.p);
    const legal = G.legal(this.p);
    this.bestMove = legal[0] || 0;
    let roots = MovePicker.ordered(this.p, legal, 0, this.history, 0).map(
      (r) => ({ ...r, score: -INF, pv: [r.move], nodes: 0 }),
    );
    this.completed = roots
      .slice(0, 3)
      .map((r) => ({ ...r, score: 0, unsearched: true }));
    let lastEmit = 0;
    if (!legal.length || this.draw(0)) {
      this.bestScore = !legal.length && this.p.inCheck() ? -MATE : 0;
      const out = this.snapshot();
      out.terminal = true;
      emit(out);
      return out;
    }
    // An explicitly labelled static pass supplies useful previews before quiescence.
    // This is not a completed search depth and is never added to the depth chart.
    const seeded = [];
    for (const r of roots) {
      if (performance.now() >= this.deadline) break;
      this.make(r.move);
      let value;
      try {
        const replies = G.legal(this.p);
        value = !replies.length
          ? this.p.inCheck()
            ? MATE - 1
            : 0
          : this.draw(1)
            ? 0
            : -this.eval.evaluate(this.p);
      } finally {
        this.undo();
      }
      this.nodes++;
      seeded.push({
        ...r,
        score: value,
        pv: [r.move],
        nodes: 1,
        unsearched: false,
        staticPreview: true,
      });
    }
    if (seeded.length) {
      seeded.sort((a, b) => b.score - a.score);
      this.completed = seeded.slice(0, 3);
      this.bestMove = seeded[0].move;
      this.bestScore = seeded[0].score;
      const priorities = new Map(seeded.map((r) => [r.move, r.score]));
      roots.sort(
        (a, b) =>
          (priorities.get(b.move) ?? -INF) - (priorities.get(a.move) ?? -INF),
      );
      emit(this.snapshot());
      await new Promise((r) => setTimeout(r, 0));
    }
    try {
      for (this.depth = 1; this.depth <= maxDepth; this.depth++) {
        this.trace = [];
        const iteration = [];
        for (let i = 0; i < roots.length; i++) {
          if (performance.now() >= this.deadline) throw ABORT;
          const r = roots[i],
            before = this.nodes;
          this.path = [r.move];
          this.make(r.move);
          let value, line;
          try {
            value = -this.search(this.depth - 1, 1, -INF, INF, true, true);
            line = [r.move, ...this.pv[1]];
          } finally {
            this.undo();
          }
          iteration.push({
            ...r,
            reason: this.depth === 1 ? "static priority" : "previous depth",
            score: value,
            pv: line,
            nodes: this.nodes - before,
            unsearched: false,
            staticPreview: false,
          });
          this.current = {
            move: Move.uci(r.move),
            index: i + 1,
            total: roots.length,
            score: this.score(value),
            depth: this.depth,
          };
          if (performance.now() - lastEmit >= 1000 / graphicsHz) {
            lastEmit = performance.now();
            emit(this.snapshot(true));
            await new Promise((r) => setTimeout(r, 0));
          }
        }
        roots = iteration.sort((a, b) => b.score - a.score);
        this.completed = roots.slice(0, 3).map((r) => ({ ...r }));
        this.bestMove = roots[0].move;
        this.bestScore = roots[0].score;
        this.completeDepth = this.depth;
        this.depthHistory.push({
          depth: this.depth,
          score: this.score(this.bestScore),
          nodes: this.nodes,
        });
        const key = this.key();
        this.tt.store(
          key,
          this.depth,
          this.bestScore,
          this.rootEvaluation.score,
          this.bestMove,
          TT.EXACT,
          0,
        );
        this.rootTT = {
          key: key.toString(16),
          bound: "EXACT",
          depth: this.depth,
          move: Move.uci(this.bestMove),
          score: this.score(this.bestScore),
        };
        emit(this.snapshot());
        await new Promise((r) => setTimeout(r, 0));
        if (Math.abs(this.bestScore) > 31000) break;
      }
    } catch (e) {
      if (e !== ABORT) throw e;
    }
    const result = this.snapshot();
    result.finished = true;
    result.reason =
      performance.now() >= this.deadline
        ? "Time limit"
        : this.nodes >= this.nodeLimit
          ? "Node limit"
          : "Depth complete";
    emit(result);
    return result;
  }
  search(depth, ply, alpha, beta, pvNode, allowNull) {
    if (depth <= 0) return this.qsearch(ply, alpha, beta);
    this.tick(ply);
    this.pv[ply] = [];
    const p = this.p,
      check = p.inCheck(),
      moves = G.legal(p);
    if (!moves.length) return check ? -MATE + ply : 0;
    if (this.draw(ply)) return 0;
    if (ply >= 96) return check ? 0 : this.eval.evaluate(p);
    alpha = Math.max(alpha, -MATE + ply);
    beta = Math.min(beta, MATE - ply - 1);
    if (alpha >= beta) return alpha;
    const original = alpha,
      key = this.key(),
      ti = this.tt.probe(key),
      ttMove = ti < 0 ? 0 : this.tt.moves[ti];
    if (ti >= 0) {
      this.ttHits++;
      const v = this.tt.score(ti, ply),
        b = this.tt.bounds[ti];
      if (
        !pvNode &&
        this.tt.depths[ti] >= depth &&
        p.halfmove < 96 &&
        (b === 1 || (b === 2 && v >= beta) || (b === 3 && v <= alpha))
      )
        return v;
    }
    const raw = check ? 0 : ti >= 0 ? this.tt.evals[ti] : this.eval.evaluate(p);
    // Conservative null move; disabled in pawn-only endings and after a null move.
    if (
      allowNull &&
      !pvNode &&
      !check &&
      depth >= 4 &&
      p.nonPawnMaterial() &&
      raw >= beta &&
      Math.abs(beta) < 30000
    ) {
      this.make(0);
      let v;
      try {
        v = -this.search(depth - 3, ply + 1, -beta, -beta + 1, false, false);
      } finally {
        this.undo();
      }
      if (v >= beta && v < 31000) {
        this.nullCuts++;
        return v;
      }
    }
    let best = -INF,
      bestMove = 0,
      searched = 0;
    const ordered = MovePicker.ordered(p, moves, ttMove, this.history, ply);
    for (const r of ordered) {
      const m = r.move,
        quiet = !G.capture(p, m) && !Move.promotion(m);
      this.path[ply] = m;
      this.make(m);
      let value;
      try {
        const givesCheck = p.inCheck();
        let reduction =
          depth >= 3 && searched >= 3 && quiet && !check && !givesCheck
            ? Math.min(
                depth - 2,
                Math.floor((Math.log(depth) * Math.log(searched + 1)) / 2),
              )
            : 0;
        if (reduction) this.lmr++;
        if (!searched)
          value = -this.search(depth - 1, ply + 1, -beta, -alpha, pvNode, true);
        else {
          value = -this.search(
            depth - 1 - reduction,
            ply + 1,
            -alpha - 1,
            -alpha,
            false,
            true,
          );
          if (reduction && value > alpha)
            value = -this.search(
              depth - 1,
              ply + 1,
              -alpha - 1,
              -alpha,
              false,
              true,
            );
          if (pvNode && value > alpha && value < beta)
            value = -this.search(depth - 1, ply + 1, -beta, -alpha, true, true);
        }
      } finally {
        this.undo();
      }
      searched++;
      if (value > best) {
        best = value;
        bestMove = m;
      }
      if (value > alpha) {
        alpha = value;
        this.pv[ply] = [m, ...this.pv[ply + 1]];
      }
      const cutoff = alpha >= beta;
      this.traceNode(ply, m, value, cutoff, r.reason);
      if (cutoff) {
        this.cutoffs++;
        this.history.reward(p, m, ply, Math.min(2000, 131 * depth));
        break;
      }
    }
    this.tt.store(
      key,
      depth,
      best,
      raw,
      bestMove,
      best >= beta ? TT.LOWER : best > original ? TT.EXACT : TT.UPPER,
      ply,
    );
    return best;
  }
  qsearch(ply, alpha, beta) {
    this.tick(ply, true);
    this.pv[ply] = [];
    const p = this.p,
      check = p.inCheck(),
      legal = G.legal(p);
    if (!legal.length) return check ? -MATE + ply : 0;
    if (this.draw(ply)) return 0;
    if (ply >= 96) return check ? 0 : this.eval.evaluate(p);
    const key = this.key(),
      ti = this.tt.probe(key),
      original = alpha;
    if (beta - alpha === 1 && ti >= 0) {
      const v = this.tt.score(ti, ply),
        bound = this.tt.bounds[ti];
      if (
        bound === TT.EXACT ||
        (bound === TT.LOWER && v >= beta) ||
        (bound === TT.UPPER && v <= alpha)
      ) {
        this.ttHits++;
        return v;
      }
    }
    const raw = check ? 0 : this.eval.evaluate(p);
    let best = check ? -INF : raw,
      bestMove = 0;
    if (!check) {
      if (best >= beta) return best;
      alpha = Math.max(alpha, best);
    }
    const ordered = MovePicker.ordered(
      p,
      check ? legal : legal.filter((m) => G.capture(p, m) || Move.promotion(m)),
      0,
      this.history,
      ply,
    );
    for (const r of ordered) {
      this.make(r.move);
      let v;
      try {
        if (!check && !Move.promotion(r.move) && r.see < -74 && !p.inCheck()) {
          this.seePrunes++;
          continue;
        }
        v = -this.qsearch(ply + 1, -beta, -alpha);
      } finally {
        this.undo();
      }
      if (v > best) {
        best = v;
        bestMove = r.move;
      }
      if (v > alpha) {
        alpha = v;
        this.pv[ply] = [r.move, ...this.pv[ply + 1]];
      }
      if (alpha >= beta) {
        this.cutoffs++;
        break;
      }
    }
    this.tt.store(
      key,
      0,
      best,
      raw,
      bestMove,
      best >= beta ? TT.LOWER : best > original ? TT.EXACT : TT.UPPER,
      ply,
    );
    return best;
  }
}
