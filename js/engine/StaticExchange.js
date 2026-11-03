// SPDX-License-Identifier: GPL-3.0-or-later
import { Move } from "./Move.js";
import { VALUE } from "./Evaluator.js";
import { bit, squares } from "./Attacks.js";
/** Legal least-valuable-attacker SEE, preserving source x-rays and king-safety test. */
export class StaticExchange {
  static see(p, m) {
    if (m & Move.CASTLE) return 0;
    const f = Move.from(m),
      t = Move.to(m),
      capture = m & Move.EP ? t + (p.side ? 8 : -8) : t;
    let occupant = Move.promotion(m) || p.board[f] & 7,
      removed = bit(f) | bit(capture),
      occ = (p.occupied() & ~removed) | bit(t),
      side = p.side ^ 1,
      d = 0;
    const gain = [
      VALUE[p.board[capture] & 7] +
        (Move.promotion(m) ? VALUE[occupant] - VALUE[1] : 0),
    ];
    while (d < 30) {
      const candidates = p.attackers(t, side, occ, removed);
      let selected = -1,
        type = 0;
      for (let q = 1; q <= 6 && selected < 0; q++)
        for (const s of squares(candidates & p.pieces[side * 8 + q]))
          if (
            !p.attackers(
              q === 6 ? t : p.king(side),
              side ^ 1,
              occ & ~bit(s),
              removed | bit(s),
            )
          ) {
            selected = s;
            type = q;
            break;
          }
      if (selected < 0) break;
      const promote =
        type === 1 && (t < 8 || t >= 56) ? VALUE[5] - VALUE[1] : 0;
      gain[d + 1] = VALUE[occupant] + promote - gain[d];
      d++;
      occupant = promote ? 5 : type;
      removed |= bit(selected);
      occ &= ~bit(selected);
      side ^= 1;
      if (type === 6) break;
    }
    while (d > 0) {
      gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
      d--;
    }
    return gain[0];
  }
}
