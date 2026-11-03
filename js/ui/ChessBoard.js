// SPDX-License-Identifier: GPL-3.0-or-later
import { Move } from "../engine/Move.js";
import { MoveGenerator as G } from "../engine/MoveGenerator.js";
const names = ["", "pawn", "knight", "bishop", "rook", "queen", "king"];
/** Responsive Lichess-style SVG board. No external server or image CDN. */
export class ChessBoard {
  constructor(
    element,
    { interactive = false, onMove = () => {}, mini = false } = {},
  ) {
    this.el = element;
    this.interactive = interactive;
    this.onMove = onMove;
    this.mini = mini;
    this.flipped = false;
    this.selected = null;
    this.lastMove = 0;
    this.squares = new Map();
    this.build();
    if (interactive) this.bind();
  }
  build() {
    this.el.replaceChildren();
    this.squares.clear();
    this.el.classList.add("chessboard");
    this.el.setAttribute("role", "group");
    this.el.setAttribute(
      "aria-label",
      this.mini ? "Candidate chess position" : "Interactive chessboard",
    );
    for (let row = 0; row < 8; row++)
      for (let col = 0; col < 8; col++) {
        const s = this.flipped ? row * 8 + 7 - col : (7 - row) * 8 + col,
          cell = document.createElement(this.interactive ? "button" : "div");
        cell.className =
          "square " + (((s & 7) + (s >> 3)) % 2 ? "light" : "dark");
        cell.dataset.square = s;
        if (this.interactive) {
          cell.type = "button";
          cell.setAttribute("aria-label", Move.square(s));
        }
        if (col === 0 && !this.mini) {
          const rank = document.createElement("span");
          rank.className = "rank";
          rank.textContent = (s >> 3) + 1;
          cell.append(rank);
        }
        if (row === 7 && !this.mini) {
          const file = document.createElement("span");
          file.className = "file";
          file.textContent = Move.square(s)[0];
          cell.append(file);
        }
        this.el.append(cell);
        this.squares.set(s, cell);
      }
    this.arrow = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    this.arrow.setAttribute("viewBox", "0 0 800 800");
    this.arrow.classList.add("board-arrow");
    this.el.append(this.arrow);
  }
  bind() {
    let from = null,
      start = null,
      moved = false;
    this.el.addEventListener("pointerdown", (e) => {
      const cell = e.target.closest("[data-square]");
      if (!cell || !this.enabled || !this.p) return;
      from = +cell.dataset.square;
      start = [e.clientX, e.clientY];
      moved = false;
    });
    this.el.addEventListener("pointermove", (e) => {
      if (start && Math.hypot(e.clientX - start[0], e.clientY - start[1]) > 10)
        moved = true;
    });
    window.addEventListener("pointerup", (e) => {
      if (from === null) return;
      const cell = document
        .elementFromPoint(e.clientX, e.clientY)
        ?.closest("[data-square]");
      if (moved && cell && this.el.contains(cell)) {
        const to = +cell.dataset.square;
        this.tryMove(from, to);
        this.suppressClick = true;
      }
      from = null;
      start = null;
    });
    this.el.addEventListener("click", (e) => {
      if (this.suppressClick) {
        this.suppressClick = false;
        return;
      }
      const cell = e.target.closest("[data-square]");
      if (!cell || !this.enabled) return;
      const s = +cell.dataset.square;
      if (this.selected !== null && this.tryMove(this.selected, s)) return;
      const pc = this.p.board[s];
      this.selected = pc && pc >>> 3 === this.p.side ? s : null;
      this.highlights();
    });
  }
  tryMove(f, t) {
    const choices = this.legal.filter(
      (m) => Move.from(m) === f && Move.to(m) === t,
    );
    if (!choices.length) return false;
    this.selected = null;
    this.onMove(choices);
    return true;
  }
  set(p, { enabled = false, lastMove = 0, arrow = 0 } = {}) {
    this.p = p;
    this.enabled = enabled;
    this.legal = this.interactive && enabled ? G.legal(p) : [];
    this.lastMove = lastMove;
    this.selected = null;
    for (const [s, cell] of this.squares) {
      cell.querySelector("img")?.remove();
      const pc = p.board[s];
      if (pc) {
        const img = document.createElement("img");
        img.src = `assets/pieces/${pc >>> 3 ? "b" : "w"}${" PNBRQK"[pc & 7]}.svg`;
        img.alt = "";
        img.draggable = false;
        cell.append(img);
      }
      cell.setAttribute(
        "aria-label",
        `${Move.square(s)}${pc ? ", " + (pc >>> 3 ? "black " : "white ") + names[pc & 7] : ", empty"}`,
      );
    }
    this.highlights();
    this.drawArrow(arrow);
  }
  highlights() {
    for (const [s, cell] of this.squares) {
      cell.classList.toggle(
        "last",
        !!this.lastMove &&
          (Move.from(this.lastMove) === s || Move.to(this.lastMove) === s),
      );
      cell.classList.toggle("selected", s === this.selected);
      cell.classList.toggle(
        "legal",
        this.selected !== null &&
          this.legal.some(
            (m) => Move.from(m) === this.selected && Move.to(m) === s,
          ),
      );
      cell.classList.toggle(
        "check",
        !!this.p && this.p.inCheck() && this.p.king(this.p.side) === s,
      );
    }
  }
  drawArrow(m) {
    this.arrow.replaceChildren();
    if (!m) return;
    const coord = (s) =>
        this.flipped
          ? [(7 - (s & 7)) * 100 + 50, (s >> 3) * 100 + 50]
          : [(s & 7) * 100 + 50, (7 - (s >> 3)) * 100 + 50],
      a = coord(Move.from(m)),
      b = coord(Move.to(m)),
      angle = Math.atan2(b[1] - a[1], b[0] - a[0]),
      end = [b[0] - Math.cos(angle) * 22, b[1] - Math.sin(angle) * 22],
      left = [
        b[0] - Math.cos(angle - 0.55) * 65,
        b[1] - Math.sin(angle - 0.55) * 65,
      ],
      right = [
        b[0] - Math.cos(angle + 0.55) * 65,
        b[1] - Math.sin(angle + 0.55) * 65,
      ];
    const line = document.createElementNS(this.arrow.namespaceURI, "line");
    for (const [k, v] of Object.entries({
      x1: a[0],
      y1: a[1],
      x2: end[0],
      y2: end[1],
      stroke: "#247f80",
      "stroke-width": 19,
      "stroke-linecap": "round",
      opacity: 0.7,
    }))
      line.setAttribute(k, v);
    const poly = document.createElementNS(this.arrow.namespaceURI, "polygon");
    poly.setAttribute("points", `${b} ${left} ${right}`);
    poly.setAttribute("fill", "#247f80");
    poly.setAttribute("opacity", ".8");
    this.arrow.append(line, poly);
  }
  flip() {
    this.flipped = !this.flipped;
    this.build();
    if (this.p)
      this.set(this.p, { enabled: this.enabled, lastMove: this.lastMove });
  }
}
