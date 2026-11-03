import assert from "node:assert/strict";
import fs from "node:fs";
import { Position } from "../js/engine/Position.js";
import { Move } from "../js/engine/Move.js";
import { MoveGenerator as G } from "../js/engine/MoveGenerator.js";
import { TranspositionTable as TT } from "../js/engine/TranspositionTable.js";
import { Evaluator } from "../js/engine/Evaluator.js";
import { Search } from "../js/engine/Search.js";
const results = { perft: [], rules: [], search: [] };
for (const [name, fen, expected] of [
  ["start", Position.START, [20, 400, 8902, 197281]],
  [
    "kiwipete",
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    [48, 2039, 97862],
  ],
  ["rook ending", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", [14, 191, 2812]],
  [
    "promotion/castling",
    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
    [6, 264, 9467],
  ],
  [
    "underpromotion",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    [44, 1486, 62379],
  ],
]) {
  const p = new Position(fen);
  for (let d = 1; d <= expected.length; d++) {
    const start = performance.now(),
      n = G.perft(p, d);
    assert.equal(n, expected[d - 1], name + " depth " + d);
    results.perft.push({
      name,
      depth: d,
      nodes: n,
      ms: Math.round(performance.now() - start),
    });
  }
  assert.equal(p.fen(), fen);
  assert.equal(p.key, p.recomputeKey());
}
const p = new Position(),
  initial = p.fen(),
  key = p.key;
let seed = 123456789;
for (let i = 0; i < 80; i++) {
  seed = (Math.imul(1664525, seed) + 1013904223) >>> 0;
  const moves = G.legal(p);
  if (!moves.length) break;
  p.make(moves[seed % moves.length]);
  assert.equal(p.key, p.recomputeKey());
  assert.equal(new Position(p.fen()).key, p.key);
}
while (p.ply) {
  p.undo();
  assert.equal(p.key, p.recomputeKey());
}
assert.equal(p.fen(), initial);
assert.equal(p.key, key);
results.rules.push(
  "Incremental hash and full make/undo restoration across 80 plies",
);
const rep = new Position();
for (const u of [
  "g1f3",
  "g8f6",
  "f3g1",
  "f6g8",
  "g1f3",
  "g8f6",
  "f3g1",
  "f6g8",
])
  rep.make(G.parse(rep, u));
assert(rep.repetition(0));
results.rules.push("Threefold repetition");
const epPinned = new Position("k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
assert(!G.legal(epPinned).some((m) => m & Move.EP));
assert.equal(epPinned.key, new Position(epPinned.fen().replace("d6", "-")).key);
results.rules.push(
  "Pinned en passant rejected and absent from repetition hash",
);
const tt = new TT(1);
tt.store(23n, 8, 31995, 10, 123, TT.LOWER, 5);
const idx = tt.probe(23n);
assert.equal(tt.score(idx, 2), 31998);
assert.equal(tt.bounds[idx], 2);
assert.equal(tt.probe(24n), -1);
results.rules.push("TT full-key lookup, bound and mate-distance normalisation");
for (const [name, fen, timeMs, depth] of [
  ["start deadline", Position.START, 300, 64],
  ["mate in one", "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", 600, 4],
  ["check evasion", "4r1k1/8/8/8/8/8/8/4K3 w - - 0 1", 300, 4],
  ["promotion", "7k/P7/6K1/8/8/8/8/8 w - - 0 1", 300, 4],
]) {
  const p = new Position(fen),
    s = new Search(p, new Evaluator()),
    start = performance.now(),
    r = await s.run({ timeMs, maxDepth: depth }),
    elapsed = performance.now() - start;
  assert(G.legal(p).includes(r.bestMove));
  assert.equal(p.fen(), fen);
  assert(elapsed < timeMs + 500);
  for (const line of r.lines) {
    const q = new Position(fen);
    for (const m of line.pv) {
      assert(G.legal(q).includes(m));
      q.make(m);
    }
  }
  if (name === "mate in one") {
    p.make(r.bestMove);
    assert(p.inCheck() && !G.legal(p).length);
  }
  results.search.push({
    name,
    depth: r.depth,
    nodes: r.nodes,
    bestMove: Move.uci(r.bestMove),
    elapsedMs: Math.round(elapsed),
  });
}
// A match with different depth ceilings must make consecutive legal moves.
const match = new Position();
for (let turn = 0; turn < 8; turn++) {
  const s = new Search(match, new Evaluator()),
    r = await s.run({ timeMs: 200, maxDepth: match.side ? 2 : 1 });
  assert(G.legal(match).includes(r.bestMove));
  match.make(r.bestMove);
}
results.search.push({ name: "8-ply engine match", fen: match.fen() });
fs.writeFileSync(
  new URL("results.json", import.meta.url),
  JSON.stringify(results, null, 2),
);
console.log(JSON.stringify(results, null, 2));
