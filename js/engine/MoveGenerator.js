// SPDX-License-Identifier: GPL-3.0-or-later
import { Attacks, bit, squares } from "./Attacks.js";
import { Move } from "./Move.js";
/** Source-compatible legal move generation, including en passant king exposure. */
export class MoveGenerator {
  static capture(p, m) {
    return !!p.board[Move.to(m)] || !!(m & Move.EP);
  }
  static legal(p) {
    const a = [],
      own = p.colors[p.side],
      enemy = p.colors[p.side ^ 1] & ~p.pieces[(p.side ^ 1) * 8 + 6],
      occ = p.occupied(),
      push = (f, t, flag = 0) => {
        if ((p.board[f] & 7) === 1 && (t < 8 || t >= 56))
          for (let pr = 5; pr >= 2; pr--) a.push(Move.of(f, t, pr) | flag);
        else a.push(Move.of(f, t) | flag);
      };
    for (const f of squares(own)) {
      const pc = p.board[f],
        type = pc & 7;
      if (type === 1) {
        const dir = p.side ? -8 : 8,
          t = f + dir;
        if (!p.board[t]) {
          push(f, t);
          if (f >> 3 === (p.side ? 6 : 1) && !p.board[t + dir])
            push(f, t + dir);
        }
        for (const t of squares(Attacks.PAWN[p.side][f] & enemy)) push(f, t);
        if (p.ep >= 0 && Attacks.PAWN[p.side][f] & bit(p.ep))
          push(f, p.ep, Move.EP);
      } else
        for (const t of squares(
          Attacks.of(pc, f, occ) & ~own & ~p.pieces[(p.side ^ 1) * 8 + 6],
        ))
          push(f, t);
    }
    if (!p.inCheck()) {
      const f = p.king(p.side),
        o = occ & ~bit(f),
        c = p.side;
      for (const kingSide of [true, false]) {
        const right = 1 << (c * 2 + (kingSide ? 0 : 1)),
          d = kingSide ? 1 : -1;
        if (!(p.rights & right)) continue;
        if (
          p.board[f + d] ||
          p.board[f + 2 * d] ||
          (!kingSide && p.board[f - 3])
        )
          continue;
        if (!p.attackers(f + d, c ^ 1, o) && !p.attackers(f + 2 * d, c ^ 1, o))
          a.push(Move.of(f, f + 2 * d) | Move.CASTLE);
      }
    }
    const legal = [];
    const side = p.side;
    for (const m of a) {
      p.make(m);
      const ok = !p.attacked(p.king(side), side ^ 1);
      p.undo();
      if (ok) legal.push(m);
    }
    return legal;
  }
  static parse(p, uci) {
    const m = this.legal(p).find((m) => Move.uci(m) === uci);
    if (!m) throw Error("Illegal move: " + uci);
    return m;
  }
  static san(p, m) {
    const legal = this.legal(p);
    if (!legal.includes(m)) throw Error("Illegal move");
    const type = p.board[Move.from(m)] & 7,
      t = Move.to(m),
      f = Move.from(m),
      capture = this.capture(p, m);
    let s;
    if (m & Move.CASTLE) s = t > f ? "O-O" : "O-O-O";
    else {
      let dis = "";
      if (type !== 1) {
        const same = legal.filter(
          (x) =>
            x !== m && Move.to(x) === t && (p.board[Move.from(x)] & 7) === type,
        );
        if (same.length) {
          if (same.every((x) => (Move.from(x) & 7) !== (f & 7)))
            dis = Move.square(f)[0];
          else if (same.every((x) => Move.from(x) >> 3 !== f >> 3))
            dis = Move.square(f)[1];
          else dis = Move.square(f);
        }
      }
      s =
        (type === 1
          ? capture
            ? Move.square(f)[0]
            : ""
          : " PNBRQK"[type] + dis) +
        (capture ? "x" : "") +
        Move.square(t) +
        (Move.promotion(m) ? "=" + " PNBRQK"[Move.promotion(m)] : "");
    }
    p.make(m);
    if (p.inCheck()) s += this.legal(p).length ? "+" : "#";
    p.undo();
    return s;
  }
  static perft(p, d) {
    if (!d) return 1;
    const m = this.legal(p);
    if (d === 1) return m.length;
    let n = 0;
    for (const x of m) {
      p.make(x);
      n += this.perft(p, d - 1);
      p.undo();
    }
    return n;
  }
}
