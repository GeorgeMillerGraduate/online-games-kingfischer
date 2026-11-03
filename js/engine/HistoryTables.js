// SPDX-License-Identifier: GPL-3.0-or-later
import { Move } from "./Move.js";
/** Bounded gravity histories; browser search uses main/capture and two killer slots. */
export class HistoryTables {
  constructor() {
    this.main = new Int16Array(8192);
    this.capture = new Int16Array(8192);
    this.killers = Array.from({ length: 128 }, () => [0, 0]);
  }
  quiet(p, m) {
    return this.main[p.side * 4096 + (m & 4095)] * 2;
  }
  captureScore(p, m) {
    return this.capture[
      (p.board[Move.from(m)] * 64 + Move.to(m)) * 8 + (p.board[Move.to(m)] & 7)
    ];
  }
  update(a, i, b, limit) {
    a[i] += b - Math.trunc((a[i] * Math.abs(b)) / limit);
  }
  reward(p, m, ply, b) {
    if (p.board[Move.to(m)] || m & Move.EP)
      this.update(
        this.capture,
        (p.board[Move.from(m)] * 64 + Move.to(m)) * 8 +
          (p.board[Move.to(m)] & 7),
        b,
        10692,
      );
    else {
      this.update(this.main, p.side * 4096 + (m & 4095), b, 7183);
      if (this.killers[ply][0] !== m)
        this.killers[ply] = [m, this.killers[ply][0]];
    }
  }
}
