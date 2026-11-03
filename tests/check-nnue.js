import fs from "node:fs";
import assert from "node:assert/strict";
import { Position } from "../js/engine/Position.js";
import { MoveGenerator as G } from "../js/engine/MoveGenerator.js";
import { Move } from "../js/engine/Move.js";
import { Network, AccumulatorStack } from "../js/engine/Network.js";
import { Evaluator } from "../js/engine/Evaluator.js";
const root = new URL(".", import.meta.url),
  fens = fs
    .readFileSync(new URL("differential.fens", root), "utf8")
    .trim()
    .split("\n"),
  oracle = fs
    .readFileSync(new URL("java-oracle.tsv", root), "utf8")
    .trim()
    .split("\n");
let legal = 0,
  hashes = 0,
  neural = 0;
const buffer = fs.readFileSync(
  process.argv[2] || new URL("../networks/nn-1a298aa575a0.nnue", root),
);
console.time("decode");
const net = new Network(
  buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength),
);
console.timeEnd("decode");
for (const row of oracle) {
  const [j, key, moves, psqt, positional, score] = row.split("\t"),
    p = new Position(fens[+j]);
  assert.equal(p.key.toString(16), key, "hash at " + j);
  hashes++;
  assert.equal(
    G.legal(p)
      .map((m) => Move.uci(m))
      .sort()
      .join(","),
    moves,
    "moves at " + j,
  );
  legal++;
  if (psqt !== undefined) {
    const ev = new Evaluator(net),
      raw = ev.acc.raw(p);
    assert.equal(raw.psqt, +psqt, "PSQT at " + j);
    assert.equal(raw.positional, +positional, "NNUE position at " + j);
    assert.equal(ev.evaluate(p), +score, "eval at " + j);
    neural++;
  }
}
// Verify lazy incremental updates and parent restoration along a legal line.
const p = new Position(),
  a = new AccumulatorStack(net);
let updates = 0;
for (let i = 0; i < 22; i++) {
  const fresh = new AccumulatorStack(net);
  assert.deepEqual(a.raw(p), fresh.raw(p));
  const moves = G.legal(p);
  if (!moves.length) break;
  p.make(moves[(i * 17 + 3) % moves.length]);
  a.push();
  updates++;
}
for (let i = 0; i < updates; i++) {
  a.pop();
  p.undo();
  const fresh = new AccumulatorStack(net);
  assert.deepEqual(a.raw(p), fresh.raw(p));
}
const result = {
  legalMoveSets: legal,
  zobristHashes: hashes,
  nnueRawAndEvaluation: neural,
  incrementalAndUndoPositions: updates * 2 + 1,
};
console.log(result);
fs.writeFileSync(
  new URL("nnue-results.json", root),
  JSON.stringify(result, null, 2),
);
