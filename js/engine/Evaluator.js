// SPDX-License-Identifier: GPL-3.0-or-later
import { Attacks, popcount, squares } from "./Attacks.js";
import { AccumulatorStack } from "./Network.js";
export const VALUE = [0, 208, 781, 825, 1276, 2538, 0];
const trunc = Math.trunc;
/** NNUE follows Java's actual quantized pipeline. Classic is a separately labelled browser evaluator. */
export class Evaluator {
  constructor(network = null) {
    this.acc = network ? new AccumulatorStack(network) : null;
    this.cache = new Map();
    this.kind = network ? "Original NNUE" : "Explainable classic";
  }
  push() {
    this.acc?.push();
  }
  pop() {
    this.acc?.pop();
  }
  reset() {
    this.acc?.reset();
  }
  evaluate(p) {
    if (this.acc) return this.breakdown(p).score;
    const cached = this.cache.get(p.key);
    if (cached !== undefined) return cached;
    const value = this.breakdown(p).score;
    if (this.cache.size >= 32768) this.cache.clear();
    this.cache.set(p.key, value);
    return value;
  }
  breakdown(p) {
    if (this.acc) {
      const raw = this.acc.raw(p);
      let n = raw.psqt + raw.positional;
      const complexity = -trunc(
        (n * Math.abs(raw.psqt - raw.positional)) / 18236,
      );
      n += complexity;
      let material = 534 * popcount(p.pieces[1] | p.pieces[9]);
      for (let t = 2; t <= 5; t++)
        material += VALUE[t] * popcount(p.pieces[t] | p.pieces[t + 8]);
      const scaling = trunc((n * material) / 91000);
      n += scaling;
      const rule50 = -trunc((n * p.halfmove) / 199);
      const score = Math.max(-30000, Math.min(30000, n + rule50));
      return {
        score,
        unit: "NNUE internal",
        terms: {
          PSQT: raw.psqt,
          Network: raw.positional,
          Complexity: complexity,
          "Material scaling": scaling,
          "Rule 50": rule50,
        },
        whiteScore: p.side ? -score : score,
      };
    }
    const terms = {
      Material: 0,
      "Piece activity": 0,
      "Pawn structure": 0,
      "King safety": 0,
      Mobility: 0,
    };
    let phase = 0;
    for (let s = 0; s < 64; s++) {
      const pc = p.board[s];
      if (pc && (pc & 7) >= 2 && (pc & 7) <= 5)
        phase += [0, 0, 1, 1, 2, 4, 0][pc & 7];
    }
    phase = Math.min(24, phase) / 24;
    const values = [0, 100, 320, 330, 500, 900, 0],
      occ = p.occupied();
    for (let c = 0; c < 2; c++) {
      const sign = c ? -1 : 1,
        own = p.colors[c];
      for (const s of squares(own)) {
        const pc = p.board[s],
          t = pc & 7,
          f = s & 7,
          r = c ? 7 - (s >> 3) : s >> 3,
          central = 3.5 - (Math.abs(f - 3.5) + Math.abs(r - 3.5)) / 2;
        terms.Material += sign * values[t];
        if (t === 1) {
          terms["Piece activity"] += sign * (r * 7 + central * 2);
          let ahead = false,
            isolated = true,
            doubled = 0;
          for (const q of squares(p.pieces[c * 8 + 1])) {
            if (Math.abs((q & 7) - f) === 1) isolated = false;
            if ((q & 7) === f && q !== s) doubled++;
          }
          for (const q of squares(p.pieces[(c ^ 1) * 8 + 1]))
            if (
              Math.abs((q & 7) - f) <= 1 &&
              (c ? q >> 3 < s >> 3 : q >> 3 > s >> 3)
            )
              ahead = true;
          terms["Pawn structure"] +=
            sign *
            ((ahead ? 0 : r * r * 3) - (isolated ? 12 : 0) - doubled * 8);
        } else if (t === 6) {
          terms["King safety"] +=
            sign * ((1 - phase) * central * 14 - phase * central * 14);
          for (const q of squares(Attacks.KING[s] & p.pieces[c * 8 + 1]))
            terms["King safety"] += sign * phase * 7;
        } else {
          terms["Piece activity"] +=
            sign * central * (t === 2 ? 14 : t === 3 ? 9 : 3);
          terms.Mobility +=
            sign * popcount(Attacks.of(pc, s, occ) & ~own) * (t === 5 ? 1 : 3);
        }
      }
      if (popcount(p.pieces[c * 8 + 3]) >= 2)
        terms["Piece activity"] += sign * 25;
    }
    for (const k in terms) terms[k] = Math.round(terms[k]);
    const whiteScore = Object.values(terms).reduce((a, b) => a + b, 0);
    return {
      score: p.side ? -whiteScore : whiteScore,
      whiteScore,
      terms,
      unit: "centipawns",
    };
  }
  static centipawns(score, p, nnue = false) {
    if (!nnue || Math.abs(score) > 31000) return score;
    let material = 0;
    for (let t = 1; t <= 5; t++)
      material +=
        [0, 1, 3, 3, 5, 9][t] * popcount(p.pieces[t] | p.pieces[t + 8]);
    const m = Math.max(17, Math.min(78, material)) / 58,
      a =
        ((-142.72052667 * m + 372.35176398) * m - 340.71073572) * m +
        415.23490212;
    return Math.floor((100 * score) / a + 0.5);
  }
}
