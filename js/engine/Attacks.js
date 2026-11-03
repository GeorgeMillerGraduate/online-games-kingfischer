// SPDX-License-Identifier: GPL-3.0-or-later
export const bit = (s) => 1n << BigInt(s);
export function lsb(b) {
  const lo = Number(b & 0xffffffffn);
  return lo
    ? 31 - Math.clz32((lo & -lo) >>> 0)
    : 32 + 31 - Math.clz32((Number(b >> 32n) & -Number(b >> 32n)) >>> 0);
}
export function popcount(b) {
  let n = 0;
  while (b) {
    b &= b - 1n;
    n++;
  }
  return n;
}
export function* squares(b) {
  while (b) {
    yield lsb(b);
    b &= b - 1n;
  }
}
/** Precomputed leaper attacks and occupancy-dependent sliding rays. */
export class Attacks {
  static PAWN = [[], []];
  static KNIGHT = [];
  static KING = [];
  static rays = [];
  static init() {
    const dx = [1, -1, 0, 0, 1, -1, 1, -1],
      dy = [0, 0, 1, -1, 1, 1, -1, -1];
    for (let s = 0; s < 64; s++) {
      const x = s & 7,
        y = s >> 3;
      this.rays[s] = [];
      for (let d = 0; d < 8; d++) {
        const a = [];
        for (
          let f = x + dx[d], r = y + dy[d];
          f >= 0 && f < 8 && r >= 0 && r < 8;
          f += dx[d], r += dy[d]
        )
          a.push(r * 8 + f);
        this.rays[s][d] = a;
      }
      let n = 0n,
        k = 0n;
      for (const [a, b] of [
        [1, 2],
        [2, 1],
        [-1, 2],
        [-2, 1],
        [1, -2],
        [2, -1],
        [-1, -2],
        [-2, -1],
      ])
        if (x + a >= 0 && x + a < 8 && y + b >= 0 && y + b < 8)
          n |= bit(s + a + 8 * b);
      for (let a = -1; a <= 1; a++)
        for (let b = -1; b <= 1; b++)
          if ((a || b) && x + a >= 0 && x + a < 8 && y + b >= 0 && y + b < 8)
            k |= bit(s + a + 8 * b);
      this.KNIGHT[s] = n;
      this.KING[s] = k;
      for (let c = 0; c < 2; c++) {
        let p = 0n;
        const r = y + (c ? -1 : 1);
        if (r >= 0 && r < 8)
          for (const f of [x - 1, x + 1])
            if (f >= 0 && f < 8) p |= bit(r * 8 + f);
        this.PAWN[c][s] = p;
      }
    }
  }
  static slider(s, occ, lo, hi) {
    let a = 0n;
    for (let d = lo; d < hi; d++)
      for (const t of this.rays[s][d]) {
        const b = bit(t);
        a |= b;
        if (occ & b) break;
      }
    return a;
  }
  static bishop(s, o) {
    return this.slider(s, o, 4, 8);
  }
  static rook(s, o) {
    return this.slider(s, o, 0, 4);
  }
  static of(p, s, o) {
    switch (p & 7) {
      case 1:
        return this.PAWN[p >>> 3][s];
      case 2:
        return this.KNIGHT[s];
      case 3:
        return this.bishop(s, o);
      case 4:
        return this.rook(s, o);
      case 5:
        return this.bishop(s, o) | this.rook(s, o);
      case 6:
        return this.KING[s];
      default:
        return 0n;
    }
  }
}
Attacks.init();
