// SPDX-License-Identifier: GPL-3.0-or-later
/** Java-compatible packed moves. Squares run a1=0 to h8=63. */
export class Move {
  static EP = 1 << 15;
  static CASTLE = 1 << 16;
  static of(from, to, promotion = 0) {
    return from | (to << 6) | (promotion << 12);
  }
  static from(m) {
    return m & 63;
  }
  static to(m) {
    return (m >>> 6) & 63;
  }
  static promotion(m) {
    return (m >>> 12) & 7;
  }
  static square(s) {
    if (typeof s === "number") return "abcdefgh"[s & 7] + (1 + (s >>> 3));
    if (!/^[a-h][1-8]$/.test(s)) throw Error("Invalid square");
    return s.charCodeAt(0) - 97 + 8 * (+s[1] - 1);
  }
  static uci(m) {
    return m
      ? this.square(this.from(m)) +
          this.square(this.to(m)) +
          (this.promotion(m) ? " pnbrqk"[this.promotion(m)] : "")
      : "0000";
  }
}
