// SPDX-License-Identifier: GPL-3.0-or-later
/** Four-way full-key clustered table, halfmove-sensitive keys supplied by Search. */
export class TranspositionTable {
  static EXACT = 1;
  static LOWER = 2;
  static UPPER = 3;
  constructor(mb = 8) {
    const n = 2 ** Math.floor(Math.log2((mb * 1048576) / 20));
    this.mask = n - 4;
    this.keys = new BigUint64Array(n);
    this.moves = new Int32Array(n);
    this.scores = new Int16Array(n);
    this.evals = new Int16Array(n);
    this.depths = new Int16Array(n);
    this.bounds = new Uint8Array(n);
    this.ages = new Uint8Array(n);
    this.generation = 1;
  }
  clear() {
    this.bounds.fill(0);
  }
  newSearch() {
    this.generation = (this.generation + 1) & 255;
  }
  probe(key) {
    const start = Number((key ^ (key >> 32n)) & BigInt(this.mask));
    for (let i = start; i < start + 4; i++)
      if (this.bounds[i] && this.keys[i] === key) return i;
    return -1;
  }
  score(i, ply) {
    const v = this.scores[i];
    return v > 31000 ? v - ply : v < -31000 ? v + ply : v;
  }
  store(key, depth, score, evalScore, move, bound, ply) {
    const start = Number((key ^ (key >> 32n)) & BigInt(this.mask));
    let best = start,
      quality = Infinity;
    for (let i = start; i < start + 4; i++) {
      if (!this.bounds[i] || this.keys[i] === key) {
        best = i;
        break;
      }
      const q = this.depths[i] - 8 * ((this.generation - this.ages[i]) & 255);
      if (q < quality) {
        quality = q;
        best = i;
      }
    }
    if (
      this.keys[best] === key &&
      this.bounds[best] &&
      bound !== 1 &&
      depth < this.depths[best] - 4 &&
      this.ages[best] === this.generation
    )
      return;
    if (move || this.keys[best] !== key) this.moves[best] = move;
    this.keys[best] = key;
    this.depths[best] = depth;
    this.evals[best] = evalScore;
    this.bounds[best] = bound;
    this.ages[best] = this.generation;
    this.scores[best] =
      score > 31000 ? score + ply : score < -31000 ? score - ply : score;
  }
  hashfull() {
    let used = 0,
      n = Math.min(1000, this.bounds.length);
    for (let i = 0; i < n; i++)
      if (this.bounds[i] && this.ages[i] === this.generation) used++;
    return Math.floor((used * 1000) / n);
  }
}
