// SPDX-License-Identifier: GPL-3.0-or-later
// Scalar NNUE port of the supplied KingFischer2 Java Network/Features/AccumulatorStack.
// Stockfish-derived feature architecture; see STOCKFISH-AUTHORS and NOTICE.md.
import { Attacks, bit, popcount, squares } from "./Attacks.js";
const clamp = (v, l, h) => Math.max(l, Math.min(h, v)),
  trunc = Math.trunc;
export class NetworkReader {
  constructor(buffer) {
    this.bytes = new Uint8Array(buffer);
    this.view = new DataView(buffer);
    this.offset = 0;
    this.remaining = 0;
  }
  take(n) {
    if (n < 0 || this.offset + n > this.bytes.length)
      throw Error("Truncated NNUE");
    const a = new Int8Array(this.bytes.buffer, this.offset, n);
    this.offset += n;
    return a;
  }
  u8() {
    if (this.offset >= this.bytes.length) throw Error("Truncated NNUE");
    return this.bytes[this.offset++];
  }
  i32() {
    if (this.offset + 4 > this.bytes.length) throw Error("Truncated NNUE");
    const v = this.view.getInt32(this.offset, true);
    this.offset += 4;
    return v;
  }
  expect(n, name) {
    if (this.i32() !== (n | 0)) throw Error(name + " mismatch");
  }
  lebArray(n, short = false) {
    if (new TextDecoder().decode(this.take(17)) !== "COMPRESSED_LEB128")
      throw Error("Missing LEB128 header");
    this.remaining = this.i32() >>> 0;
    const a = short ? new Int16Array(n) : new Int32Array(n);
    for (let i = 0; i < n; i++) {
      let v = 0,
        shift = 0,
        b;
      do {
        if (this.remaining-- <= 0 || shift >= 35)
          throw Error("Malformed LEB128");
        b = this.u8();
        v += (b & 127) * 2 ** shift;
        shift += 7;
      } while (b & 128);
      if (b & 64) v -= 2 ** shift;
      if (
        v < -2147483648 ||
        v > 2147483647 ||
        (short && (v < -32768 || v > 32767))
      )
        throw Error("NNUE integer overflow");
      a[i] = v;
    }
    if (this.remaining !== 0) throw Error("NNUE block size mismatch");
    return a;
  }
  raw(n) {
    return Int32Array.from({ length: n }, () => this.i32());
  }
}
export class Features {
  static PSQ = 22528;
  static THREATS = 59808;
  static PAIRS = 4560;
  static targets = [0, 4, 10, 8, 8, 10, 0, 0, 0, 4, 10, 8, 8, 10, 0, 0];
  static map = [
    [-1, 0, -1, 1, -1, -1],
    [0, 1, 2, 3, 4, -1],
    [0, 1, 2, 3, -1, -1],
    [0, 1, 2, 3, -1, -1],
    [0, 1, 2, 3, 4, -1],
    [-1, -1, -1, -1, -1, -1],
  ];
  static base = new Int32Array(16);
  static span = new Int32Array(16);
  static off = Array.from({ length: 16 }, () => []);
  static pseudo = Array.from({ length: 16 }, () => []);
  static init() {
    let total = 0;
    for (let pc = 1; pc <= 14; pc++) {
      if (!(pc & 7) || (pc & 7) === 7) continue;
      this.base[pc] = total;
      for (let s = 0; s < 64; s++) {
        this.off[pc][s] = this.span[pc];
        this.pseudo[pc][s] = Attacks.of(pc, s, 0n);
        if ((pc & 7) !== 1 || (s >= 8 && s <= 55))
          this.span[pc] += popcount(this.pseudo[pc][s]);
      }
      total += this.targets[pc] * this.span[pc];
    }
    if (total !== this.THREATS) throw Error("Threat feature layout mismatch");
  }
  static psq(c, s, pc, k) {
    const kk = k ^ (56 * c),
      bucket = (7 - (kk >>> 3)) * 4 + Math.min(kk & 7, 7 - (kk & 7)),
      type = pc & 7,
      index = type === 6 ? 10 : 2 * (type - 1) + ((pc >>> 3) ^ c);
    return bucket * 704 + index * 64 + (s ^ ((k & 7) < 4 ? 7 : 0) ^ (56 * c));
  }
  static threat(c, pc, s, t, target, k) {
    const o = ((k & 7) < 4 ? 0 : 7) ^ (56 * c),
      a = pc ^ (c * 8),
      b = target ^ (c * 8),
      f = s ^ o,
      to = t ^ o,
      type = a & 7,
      map = this.map[type - 1][(b & 7) - 1];
    if (
      map < 0 ||
      (f < to && type === (b & 7) && ((a ^ b) === 8 || type !== 1))
    )
      return -1;
    return (
      this.base[a] +
      ((b >>> 3) * (this.targets[a] / 2) + map) * this.span[a] +
      this.off[a][f] +
      popcount(this.pseudo[a][f] & (bit(to) - 1n))
    );
  }
  static pair(c, pc, s, other, t, k) {
    const o = ((k & 7) < 4 ? 0 : 7) ^ (56 * c),
      a = 48 * ((pc >>> 3) ^ c) + (s ^ o) - 8,
      b = 48 * ((other >>> 3) ^ c) + (t ^ o) - 8,
      hi = Math.max(a, b);
    return this.THREATS + (hi * (hi - 1)) / 2 + Math.min(a, b);
  }
  static active(p, c) {
    const out = [],
      k = p.king(c),
      occ = p.occupied();
    for (const s of squares(occ)) {
      const pc = p.board[s];
      out.push(this.psq(c, s, pc, k));
      if ((pc & 7) < 6)
        for (const t of squares(Attacks.of(pc, s, occ) & occ)) {
          const idx = this.threat(c, pc, s, t, p.board[t], k);
          if (idx >= 0) out.push(this.PSQ + idx);
        }
    }
    const pawns = [...squares(p.pieces[1] | p.pieces[9])];
    for (let i = 0; i < pawns.length; i++)
      for (let j = i + 1; j < pawns.length; j++) {
        const a = pawns[i],
          b = pawns[j];
        if (Math.abs((a & 7) - (b & 7)) <= 1)
          out.push(this.PSQ + this.pair(c, p.board[a], a, p.board[b], b, k));
      }
    return out.sort((a, b) => a - b);
  }
}
Features.init();
export class Network {
  static transformerHash() {
    let h = 0;
    for (const x of [0x2e6b9d04, 0x86f2b1dd, 0x7f234cb8])
      h = ((h << 1) | (h >>> 31)) ^ x;
    return h ^ 2048;
  }
  static architectureHash() {
    const affine = (h, n) => (0xcc03dae4 + n) ^ ((h >>> 1) | (h << 31));
    let h = 0xec42e90d ^ 2048;
    h = (affine(h, 32) + 0x538d24c7) | 0;
    h = (affine(h, 32) + 0x538d24c7) | 0;
    return affine(h, 1);
  }
  constructor(buffer) {
    const r = new NetworkReader(buffer);
    r.expect(0x6a448afa, "NNUE version");
    r.expect(
      Network.transformerHash() ^ Network.architectureHash(),
      "NNUE architecture",
    );
    const n = r.i32();
    if (n < 0 || n > 1048576) throw Error("Invalid description");
    this.description = new TextDecoder().decode(r.take(n));
    r.expect(Network.transformerHash(), "Transformer");
    this.biases = r.lebArray(1024, true);
    this.threatWeights = r.take(Features.THREATS * 1024);
    this.threatPsqt = r.lebArray(Features.THREATS * 8);
    this.pairWeights = r.take(Features.PAIRS * 1024);
    this.pairPsqt = r.lebArray(Features.PAIRS * 8);
    this.psqWeights = r.lebArray(Features.PSQ * 1024, true);
    this.psqPsqt = r.lebArray(Features.PSQ * 8);
    this.layers = [];
    for (let b = 0; b < 8; b++) {
      r.expect(Network.architectureHash(), "Layer " + b);
      this.layers.push({
        b0: r.raw(32),
        w0: r.take(32 * 1024),
        b1: r.raw(32),
        w1: r.take(32 * 64),
        b2: r.raw(1),
        w2: r.take(128),
      });
    }
    if (r.offset !== r.bytes.length) throw Error("Trailing NNUE bytes");
  }
  update(acc, psqt, feature, sign) {
    let w, q, idx;
    if (feature < Features.PSQ) {
      w = this.psqWeights;
      q = this.psqPsqt;
      idx = feature;
    } else {
      idx = feature - Features.PSQ;
      if (idx < Features.THREATS) {
        w = this.threatWeights;
        q = this.threatPsqt;
      } else {
        idx -= Features.THREATS;
        w = this.pairWeights;
        q = this.pairPsqt;
      }
    }
    for (let i = 0, off = idx * 1024; i < 1024; i++)
      acc[i] += sign * w[off + i];
    for (let b = 0; b < 8; b++) psqt[b] += sign * q[idx * 8 + b];
  }
  positional(bucket, x, temp, concat) {
    const l = this.layers[bucket];
    for (let o = 0; o < 32; o++) {
      let sum = l.b0[o];
      for (let i = 0, off = o * 1024; i < 1024; i++)
        sum = (sum + l.w0[off + i] * x[i]) | 0;
      temp[o] = sum;
      concat[o] = Math.min(127, Math.floor((sum * sum) / 2097152));
      concat[32 + o] = clamp(sum >> 7, 0, 127);
    }
    const skip = (temp[30] - temp[31]) | 0;
    for (let o = 0; o < 32; o++) {
      let sum = l.b1[o];
      for (let i = 0, off = o * 64; i < 64; i++)
        sum = (sum + l.w1[off + i] * concat[i]) | 0;
      concat[64 + o] = Math.min(127, Math.floor((sum * sum) / 524288));
      concat[96 + o] = clamp(sum >> 6, 0, 127);
    }
    let sum = (l.b2[0] + skip) | 0;
    for (let i = 0; i < 128; i++) sum = (sum + l.w2[i] * concat[i]) | 0;
    return trunc((trunc((sum * 9600) / 16384) | 0) / 16) | 0;
  }
}
export class AccumulatorStack {
  constructor(net) {
    this.net = net;
    this.frames = [];
    this.top = 0;
    this.evaluations = 0;
    this.featureUpdates = 0;
    this.refreshes = 0;
    this.transformed = new Int32Array(1024);
    this.temp = new Int32Array(32);
    this.concat = new Int32Array(128);
  }
  push() {
    this.top++;
    if (this.frames[this.top]) this.frames[this.top].valid = false;
  }
  pop() {
    if (this.top === 0) throw Error("Accumulator underflow");
    this.top--;
  }
  reset() {
    this.top = 0;
    for (const f of this.frames) f.valid = false;
  }
  ensure(p) {
    let f = this.frames[this.top];
    if (!f)
      f = this.frames[this.top] = {
        acc: [new Int16Array(1024), new Int16Array(1024)],
        psqt: [new Int32Array(8), new Int32Array(8)],
        features: [[], []],
        valid: false,
      };
    if (f.valid && f.key === p.key) return f;
    let ancestor = null;
    for (let i = this.top - 1; i >= 0; i--)
      if (this.frames[i]?.valid) {
        ancestor = this.frames[i];
        break;
      }
    for (let c = 0; c < 2; c++) {
      const features = Features.active(p, c);
      f.features[c] = features;
      if (!ancestor) {
        f.acc[c].set(this.net.biases);
        f.psqt[c].fill(0);
        this.refreshes++;
        for (const x of features) {
          this.net.update(f.acc[c], f.psqt[c], x, 1);
          this.featureUpdates++;
        }
      } else {
        f.acc[c].set(ancestor.acc[c]);
        f.psqt[c].set(ancestor.psqt[c]);
        const old = ancestor.features[c];
        let i = 0,
          j = 0;
        while (i < old.length || j < features.length) {
          const a = old[i] ?? Infinity,
            b = features[j] ?? Infinity;
          if (a === b) {
            i++;
            j++;
          } else if (a < b) {
            this.net.update(f.acc[c], f.psqt[c], a, -1);
            i++;
            this.featureUpdates++;
          } else {
            this.net.update(f.acc[c], f.psqt[c], b, 1);
            j++;
            this.featureUpdates++;
          }
        }
      }
    }
    f.key = p.key;
    f.valid = true;
    return f;
  }
  raw(p) {
    const f = this.ensure(p);
    this.evaluations++;
    const b = trunc((popcount(p.occupied()) - 1) / 4),
      psqt =
        trunc(
          trunc(((f.psqt[p.side][b] - f.psqt[p.side ^ 1][b]) | 0) / 2) / 16,
        ) | 0;
    for (let c = 0; c < 2; c++) {
      const a = f.acc[p.side ^ c];
      for (let j = 0; j < 512; j++)
        this.transformed[c * 512 + j] = trunc(
          (clamp(a[j], 0, 255) * clamp(a[j + 512], 0, 255)) / 512,
        );
    }
    return {
      psqt,
      positional: this.net.positional(
        b,
        this.transformed,
        this.temp,
        this.concat,
      ),
    };
  }
}
