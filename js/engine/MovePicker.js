// SPDX-License-Identifier: GPL-3.0-or-later
import { MoveGenerator as G } from "./MoveGenerator.js";
import { Move } from "./Move.js";
import { StaticExchange } from "./StaticExchange.js";
import { VALUE } from "./Evaluator.js";
export class MovePicker {
  static ordered(p, moves, ttMove, history, ply) {
    return moves
      .map((move) => {
        const capture = G.capture(p, move),
          promo = Move.promotion(move),
          see = capture || promo ? StaticExchange.see(p, move) : 0;
        let reason = "history",
          order = history.quiet(p, move);
        if (move === ttMove) {
          order = 2000000;
          reason = "transposition";
        } else if (capture || promo) {
          order =
            (see >= 0 ? 1000000 : -1000000) +
            8 * VALUE[p.board[Move.to(move)] & 7] +
            history.captureScore(p, move) +
            (promo ? VALUE[promo] : 0);
          reason = promo
            ? "promotion"
            : see >= 0
              ? "good capture"
              : "losing capture";
        } else if (history.killers[ply]?.includes(move)) {
          order += 50000;
          reason = "killer";
        }
        return { move, see, order, reason };
      })
      .sort((a, b) => b.order - a.order);
  }
}
