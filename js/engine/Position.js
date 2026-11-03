// SPDX-License-Identifier: GPL-3.0-or-later
import { Attacks, bit, lsb, popcount, squares } from "./Attacks.js";
import { Zobrist as Z } from "./Zobrist.js";
import { Move } from "./Move.js";
const chars = " PNBRQK  pnbrqk",
  castleMask = (s) => ({ 0: 13, 4: 12, 7: 14, 56: 7, 60: 3, 63: 11 })[s] ?? 15;
/** Direct orthodox position/make-unmake port; a Position has exactly one owner. */
export class Position {
  static START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
  constructor(fen = Position.START) {
    this.board = new Uint8Array(64);
    this.pieces = Array(16).fill(0n);
    this.colors = [0n, 0n];
    this.side = 0;
    this.rights = 0;
    this.ep = -1;
    this.halfmove = 0;
    this.fullmove = 1;
    this.key = 0n;
    this.pawnKey = 0n;
    this.states = [];
    this.keys = [];
    this.nullPly = -1;
    this.load(fen);
  }
  get ply() {
    return this.states.length;
  }
  occupied() {
    return this.colors[0] | this.colors[1];
  }
  king(c) {
    return lsb(this.pieces[c * 8 + 6]);
  }
  attackers(s, c, o = this.occupied(), removed = 0n) {
    const k = c * 8;
    return (
      ((Attacks.PAWN[c ^ 1][s] & this.pieces[k + 1]) |
        (Attacks.KNIGHT[s] & this.pieces[k + 2]) |
        (Attacks.KING[s] & this.pieces[k + 6]) |
        (Attacks.bishop(s, o) & (this.pieces[k + 3] | this.pieces[k + 5])) |
        (Attacks.rook(s, o) & (this.pieces[k + 4] | this.pieces[k + 5]))) &
      ~removed
    );
  }
  attacked(s, c) {
    return this.attackers(s, c) !== 0n;
  }
  inCheck() {
    return this.attacked(this.king(this.side), this.side ^ 1);
  }
  put(s, p) {
    const old = this.board[s],
      b = bit(s);
    if (old) {
      this.pieces[old] ^= b;
      this.colors[old >>> 3] ^= b;
      this.key ^= Z.PIECE[old][s];
      if ((old & 7) === 1) this.pawnKey ^= Z.PIECE[old][s];
    }
    this.board[s] = p;
    if (p) {
      this.pieces[p] ^= b;
      this.colors[p >>> 3] ^= b;
      this.key ^= Z.PIECE[p][s];
      if ((p & 7) === 1) this.pawnKey ^= Z.PIECE[p][s];
    }
  }
  epFile() {
    if (this.ep < 0) return -1;
    const cap = this.ep + (this.side ? 8 : -8);
    if (this.board[this.ep] || this.board[cap] !== (this.side ^ 1) * 8 + 1)
      return -1;
    for (const f of squares(
      Attacks.PAWN[this.side ^ 1][this.ep] & this.pieces[this.side * 8 + 1],
    ))
      if (
        !this.attackers(
          this.king(this.side),
          this.side ^ 1,
          (this.occupied() & ~bit(f) & ~bit(cap)) | bit(this.ep),
          bit(cap),
        )
      )
        return this.ep & 7;
    return -1;
  }
  stateHash() {
    const f = this.epFile();
    return (
      Z.CASTLING[this.rights] ^
      (this.side ? Z.SIDE : 0n) ^
      (f < 0 ? 0n : Z.EP[f])
    );
  }
  recomputeKey() {
    let h = this.stateHash();
    for (let s = 0; s < 64; s++)
      if (this.board[s]) h ^= Z.PIECE[this.board[s]][s];
    return h;
  }
  make(m) {
    if (this.ply >= 4095) throw Error("Position history limit");
    const st = {
      m,
      rights: this.rights,
      ep: this.ep,
      halfmove: this.halfmove,
      fullmove: this.fullmove,
      key: this.key,
      pawnKey: this.pawnKey,
      nullPly: this.nullPly,
    };
    this.key ^= this.stateHash();
    if (!m) {
      this.ep = -1;
      this.halfmove++;
      this.nullPly = this.ply + 1;
    } else {
      const f = Move.from(m),
        t = Move.to(m),
        p = this.board[f],
        cap = m & Move.EP ? t + (this.side ? 8 : -8) : t;
      st.piece = p;
      st.captureSquare = cap;
      st.captured = this.board[cap];
      this.put(f, 0);
      if (st.captured) this.put(cap, 0);
      this.put(t, Move.promotion(m) ? this.side * 8 + Move.promotion(m) : p);
      if (m & Move.CASTLE) {
        this.put(t > f ? f + 3 : f - 4, 0);
        this.put(t > f ? f + 1 : f - 1, this.side * 8 + 4);
      }
      this.rights &= castleMask(f) & castleMask(t);
      this.ep = (p & 7) === 1 && Math.abs(t - f) === 16 ? (f + t) / 2 : -1;
      this.halfmove = (p & 7) === 1 || st.captured ? 0 : this.halfmove + 1;
    }
    if (this.side) this.fullmove++;
    this.side ^= 1;
    this.key ^= this.stateHash();
    this.states.push(st);
    this.keys[this.ply] = this.key;
  }
  undo() {
    const st = this.states.pop();
    if (!st) throw Error("No move to undo");
    this.side ^= 1;
    const m = st.m;
    if (m) {
      const f = Move.from(m),
        t = Move.to(m);
      this.put(t, 0);
      this.put(f, st.piece);
      if (m & Move.CASTLE) {
        this.put(t > f ? f + 1 : f - 1, 0);
        this.put(t > f ? f + 3 : f - 4, this.side * 8 + 4);
      }
      if (st.captured) this.put(st.captureSquare, st.captured);
    }
    for (const k of [
      "rights",
      "ep",
      "halfmove",
      "fullmove",
      "key",
      "pawnKey",
      "nullPly",
    ])
      this[k] = st[k];
  }
  repetition(searchPly = 0) {
    let n = 0;
    for (
      let i = this.ply - 2,
        limit = Math.max(0, this.ply - this.halfmove, this.nullPly);
      i >= limit;
      i -= 2
    )
      if (this.keys[i] === this.key && (i > this.ply - searchPly || ++n >= 2))
        return true;
    return false;
  }
  insufficientMaterial() {
    if (
      this.pieces[1] |
      this.pieces[9] |
      this.pieces[4] |
      this.pieces[12] |
      this.pieces[5] |
      this.pieces[13]
    )
      return false;
    const m =
      this.pieces[2] | this.pieces[10] | this.pieces[3] | this.pieces[11];
    return (
      popcount(m) <= 1 ||
      (!(this.pieces[2] | this.pieces[10]) &&
        (!(m & 0x55aa55aa55aa55aan) || !(m & 0xaa55aa55aa55aa55n)))
    );
  }
  nonPawnMaterial() {
    return !!(
      this.colors[this.side] &
      ~(this.pieces[this.side * 8 + 1] | this.pieces[this.side * 8 + 6])
    );
  }
  copy() {
    const p = new Position(this.fen());
    p.states = this.states.map((s) => ({ ...s }));
    p.keys = this.keys.slice();
    p.nullPly = this.nullPly;
    return p;
  }
  load(fen) {
    const a = fen.trim().split(/\s+/),
      ranks = a[0].split("/");
    if (a.length !== 6 || ranks.length !== 8)
      throw Error("FEN needs 8 ranks and 6 fields");
    for (let r = 0; r < 8; r++) {
      let f = 0;
      for (const c of ranks[r]) {
        if (/[1-8]/.test(c)) f += +c;
        else {
          const pc = chars.indexOf(c);
          if (pc < 1 || f >= 8) throw Error("Invalid FEN piece");
          this.put((7 - r) * 8 + f++, pc);
        }
      }
      if (f !== 8) throw Error("FEN rank must have 8 squares");
    }
    if (!/^[wb]$/.test(a[1])) throw Error("Invalid side");
    this.side = a[1] === "b" ? 1 : 0;
    if (a[2] !== "-")
      for (const c of a[2]) {
        const i = "KQkq".indexOf(c);
        if (i < 0 || this.rights & (1 << i))
          throw Error("Invalid castling rights");
        this.rights |= 1 << i;
      }
    this.ep = a[3] === "-" ? -1 : Move.square(a[3]);
    if (!/^\d+$/.test(a[4]) || !/^\d+$/.test(a[5]))
      throw Error("Invalid FEN counters");
    this.halfmove = +a[4];
    this.fullmove = +a[5];
    if (
      this.fullmove < 1 ||
      !Number.isSafeInteger(this.halfmove) ||
      !Number.isSafeInteger(this.fullmove) ||
      popcount(this.pieces[6]) !== 1 ||
      popcount(this.pieces[14]) !== 1 ||
      popcount(this.occupied()) > 32
    )
      throw Error("Invalid FEN kings/counters");
    if ((this.pieces[1] | this.pieces[9]) & 0xff000000000000ffn)
      throw Error("Pawn on promotion rank");
    if (
      this.ep >= 0 &&
      (this.ep >> 3 !== (this.side ? 2 : 5) ||
        this.board[this.ep] ||
        this.board[this.ep + (this.side ? 8 : -8)] !== (this.side ^ 1) * 8 + 1)
    )
      throw Error("Invalid en passant");
    const ks = [4, 4, 60, 60],
      rs = [7, 0, 63, 56];
    for (let i = 0; i < 4; i++)
      if (
        this.rights & (1 << i) &&
        (this.board[ks[i]] !== Math.floor(i / 2) * 8 + 6 ||
          this.board[rs[i]] !== Math.floor(i / 2) * 8 + 4)
      )
        throw Error("Missing castling king/rook");
    if (this.attacked(this.king(this.side ^ 1), this.side))
      throw Error("Side not to move is in check");
    this.key = this.recomputeKey();
    this.keys[0] = this.key;
  }
  fen() {
    const ranks = [];
    for (let r = 7; r >= 0; r--) {
      let s = "",
        n = 0;
      for (let f = 0; f < 8; f++) {
        const p = this.board[r * 8 + f];
        if (!p) n++;
        else {
          if (n) s += n;
          n = 0;
          s += chars[p];
        }
      }
      if (n) s += n;
      ranks.push(s);
    }
    return `${ranks.join("/")} ${this.side ? "b" : "w"} ${[..."KQkq"].filter((_, i) => this.rights & (1 << i)).join("") || "-"} ${this.ep < 0 ? "-" : Move.square(this.ep)} ${this.halfmove} ${this.fullmove}`;
  }
}
