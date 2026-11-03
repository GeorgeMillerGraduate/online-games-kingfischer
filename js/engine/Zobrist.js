// SPDX-License-Identifier: GPL-3.0-or-later
/** Identical SplitMix64 sequence to the supplied Java engine. */
export class Zobrist {
  static seed = 0x4a656e6761466973n;
  static next() {
    let z = (this.seed = BigInt.asUintN(64, this.seed + 0x9e3779b97f4a7c15n));
    z = BigInt.asUintN(64, (z ^ (z >> 30n)) * 0xbf58476d1ce4e5b9n);
    z = BigInt.asUintN(64, (z ^ (z >> 27n)) * 0x94d049bb133111ebn);
    return z ^ (z >> 31n);
  }
  static PIECE = Array.from({ length: 16 }, () =>
    Array.from({ length: 64 }, () => Zobrist.next()),
  );
  static CASTLING = Array.from({ length: 16 }, () => Zobrist.next());
  static EP = Array.from({ length: 8 }, () => Zobrist.next());
  static SIDE = Zobrist.next();
}
