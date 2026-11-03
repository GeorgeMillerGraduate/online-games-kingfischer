// SPDX-License-Identifier: GPL-3.0-or-later
import { Move } from "../engine/Move.js";
import { MoveGenerator as G } from "../engine/MoveGenerator.js";
const NS = "http://www.w3.org/2000/svg",
  nfmt = (n) => Math.round(n).toLocaleString("en-GB");
export const scoreText = (v) =>
  Math.abs(v) > 31000
    ? (v < 0 ? "−" : "") + "M" + Math.ceil((32000 - Math.abs(v)) / 2)
    : (v > 0 ? "+" : "") + (v / 100).toFixed(2);
function svg(tag, attrs = {}, text) {
  const e = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
  if (text !== undefined) e.textContent = text;
  return e;
}
/** Bounded, sampled search data. SVG graphics never invent unexplored branches. */
export class Notebook {
  constructor() {
    this.tree = document.querySelector("#tree");
    this.chart = document.querySelector("#score-chart");
  }
  clear() {
    this.tree.setAttribute(
      "aria-label",
      "Real searched branches; no search yet",
    );
    this.chart.setAttribute("aria-label", "Score chart awaiting search");
    document.querySelector("#tree-note").textContent =
      "Search branches appear as the engine explores. The full tree is too large to draw.";
    document.querySelector("#trace-list").textContent =
      "No internal nodes sampled yet. Scores here use the side to move at each sampled node.";
    this.tree.replaceChildren(
      svg(
        "text",
        {
          x: 240,
          y: 110,
          "text-anchor": "middle",
          fill: "#8a7765",
          "font-size": 13,
          "font-family": "Georgia",
        },
        "A search begins with a question.",
      ),
    );
    this.chart.replaceChildren(
      svg("line", { x1: 35, y1: 75, x2: 460, y2: 75, stroke: "#ddcdb9" }),
      svg(
        "text",
        {
          x: 240,
          y: 100,
          "text-anchor": "middle",
          fill: "#8a7765",
          "font-size": 11,
        },
        "Completed depths will appear here.",
      ),
    );
    for (const id of ["nodes", "qnodes", "tt-hits", "cutoffs"])
      document.getElementById(id).textContent = "0";
    document.querySelector("#nps").textContent = "—";
    document.querySelector("#reductions").textContent = "0 / 0";
    document.querySelector("#hashfull").textContent =
      "Current-generation sample occupancy: 0‰";
    document.querySelector("#tt-detail").textContent =
      "Full 64-bit keys · four-way clusters · awaiting search";
    document.querySelector("#move-order").replaceChildren(
      Object.assign(document.createElement("li"), {
        textContent: "Run a search to inspect the order.",
      }),
    );
  }
  evaluation(detail, side = 0) {
    const nnue = detail.unit !== "centipawns",
      sign = nnue ? (side ? -1 : 1) : 1;
    document.querySelector("#evaluation-bars").replaceChildren();
    for (const [name, value] of Object.entries(detail.terms)) {
      const v = value * sign,
        row = document.createElement("div");
      row.className = "term";
      const label = document.createElement("span");
      label.textContent = name;
      const track = document.createElement("span");
      track.className = "track";
      const bar = document.createElement("i");
      bar.className = "bar" + (v < 0 ? " negative" : "");
      const w = Math.min(50, Math.abs(v) / (nnue ? 30 : 12));
      bar.style.width = w + "%";
      bar.style.left = (v < 0 ? 50 - w : 50) + "%";
      track.append(bar);
      const number = document.createElement("span");
      number.className = "number";
      number.textContent = nnue ? Math.round(v) : scoreText(v);
      row.append(label, track, number);
      document.querySelector("#evaluation-bars").append(row);
    }
    document.querySelector("#evaluation-note").textContent = nnue
      ? "Actual NNUE terms, in internal units; shown from White’s view. The main score is normalised to centipawns."
      : "Classic evaluation of the search root / live board. Values are centipawns.";
  }
  update(data, p) {
    const sign = p.side ? -1 : 1;
    document.querySelector("#nodes").textContent = nfmt(data.nodes);
    document.querySelector("#qnodes").textContent = nfmt(data.qnodes);
    document.querySelector("#nps").textContent = nfmt(data.nps);
    document.querySelector("#tt-hits").textContent = nfmt(data.ttHits);
    document.querySelector("#cutoffs").textContent = nfmt(data.cutoffs);
    document.querySelector("#reductions").textContent =
      `${data.lmr} / ${data.nullCuts}`;
    document.querySelector("#hashfull").textContent =
      `Current-generation sample occupancy: ${data.hashfull}‰`;
    if (data.evaluation) this.evaluation(data.evaluation, p.side);
    const v = data.score * sign;
    document.querySelector("#position-score").textContent = scoreText(v);
    document.querySelector("#gauge-score").textContent = scoreText(v);
    document.querySelector("#eval-fill").style.height =
      50 + 45 * Math.tanh(v / 500) + "%";
    this.drawTree(data, p);
    this.drawChart(data.history, sign);
    const ol = document.querySelector("#move-order");
    ol.replaceChildren();
    for (const r of data.lines || []) {
      const li = document.createElement("li"),
        extra = document.createElement("span");
      li.textContent =
        G.san(p, r.move) +
        " " +
        (r.unsearched ? "—" : scoreText(r.score * sign));
      extra.textContent = r.staticPreview
        ? "static preview only"
        : r.unsearched
          ? "not searched"
          : `${r.reason} · ${nfmt(r.nodes)} nodes`;
      li.append(extra);
      ol.append(li);
    }
    const trace = document.querySelector("#trace-list");
    trace.replaceChildren();
    for (const t of data.trace.slice(-12)) {
      const row = document.createElement("div");
      row.textContent = `${t.cutoff ? "× cutoff" : "· searched"} | ${t.parent} → ${t.move} | ${scoreText(t.score)} | ${t.reason}`;
      trace.append(row);
    }
    if (!data.trace.length)
      trace.textContent = "No internal nodes sampled at this depth.";
    if (data.ttEntry) {
      const t = data.ttEntry;
      document.querySelector("#tt-detail").textContent =
        `${t.bound} · depth ${t.depth}\nMove ${t.move} · ${scoreText(t.score * sign)}\nKey ${t.key}`;
    }
  }
  drawTree(data, p) {
    const rootLabel = `${p.side ? "Black" : "White"} · ${data.staticPreview ? "static preview" : "depth " + data.depth}`;
    this.tree.replaceChildren();
    const box = (x, y, w, label, color = "#f5eddf") => {
        this.tree.append(
          svg("rect", {
            x: x - w / 2,
            y: y - 13,
            width: w,
            height: 27,
            rx: 2,
            fill: color,
            stroke: "#c5b49d",
          }),
          svg(
            "text",
            {
              x,
              y: y + 5,
              "text-anchor": "middle",
              fill: color === "#2c7b7b" ? "#fff" : "#583c29",
              "font-size": 12,
              "font-family": "Georgia",
            },
            label,
          ),
        );
      },
      edge = (x, y, xx, yy, color = "#a58e76", dash = false) =>
        this.tree.append(
          svg("path", {
            d: `M${x},${y + 14}L${xx},${yy - 14}`,
            fill: "none",
            stroke: color,
            "stroke-width": 1.3,
            ...(dash ? { "stroke-dasharray": "4 4" } : {}),
          }),
        );
    box(240, 25, 170, rootLabel);
    const candidates = (data.lines || []).filter((r) => !r.unsearched);
    if (!candidates.length) {
      this.tree.append(
        svg(
          "text",
          {
            x: 240,
            y: 115,
            "text-anchor": "middle",
            fill: "#8a7765",
            "font-size": 12,
          },
          data.current
            ? "Completing the first depth…"
            : "Waiting for completed candidates",
        ),
      );
      return;
    }
    candidates.slice(0, 3).forEach((r, i) => {
      const x = 80 + i * 160;
      edge(240, 25, x, 86, i === 0 ? "#2c7b7b" : "#a58e76");
      box(x, 86, 85, G.san(p, r.move), i === 0 ? "#2c7b7b" : "#f5eddf");
      if (r.pv[1]) {
        edge(x, 86, x, 147);
        box(x, 147, 70, Move.uci(r.pv[1]));
      }
      edge(x, r.pv[1] ? 147 : 86, x, 215);
      box(x, 215, 78, scoreText(r.score * (p.side ? -1 : 1)));
      const cut = data.trace.find(
        (t) =>
          t.ply === 1 &&
          t.parent === Move.uci(r.move) &&
          t.cutoff &&
          t.move !== Move.uci(r.pv[1] || 0),
      );
      if (cut) {
        edge(x, 86, x + 48, 125, "#ab512b", true);
        this.tree.append(
          svg(
            "text",
            {
              x: x + 45,
              y: 128,
              "text-anchor": "middle",
              fill: "#ab512b",
              "font-size": 10,
            },
            cut.move + " ×",
          ),
        );
      }
    });
    const cuts = data.trace.filter((t) => t.cutoff).length;
    document.querySelector("#tree-note").textContent = data.staticPreview
      ? "Static first-move previews while the first quiescence search completes. These are not completed-depth scores."
      : `${data.trace.length} sampled internal results; ${cuts} observed cutoffs in this bounded sample. The lines above are actual completed candidate variations.`;
    this.tree.setAttribute(
      "aria-label",
      `Three actual candidate lines, ${data.trace.length} internal results sampled`,
    );
  }
  drawChart(history, sign) {
    this.chart.replaceChildren();
    const x0 = 35,
      y0 = 115,
      w = 420,
      h = 90;
    for (let i = 0; i < 3; i++) {
      const y = 25 + i * 45;
      this.chart.append(
        svg("line", { x1: x0, y1: y, x2: 455, y2: y, stroke: "#e4d8c8" }),
      );
    }
    if (!history?.length) return;
    const max = Math.max(
      100,
      ...history.map((r) => Math.min(1000, Math.abs(r.score))),
    );
    const coords = history.map((r, i) => [
      x0 + (history.length === 1 ? w / 2 : (i * w) / (history.length - 1)),
      y0 -
        h / 2 -
        (Math.max(-max, Math.min(max, r.score * sign)) * h) / (max * 2),
    ]);
    this.chart.append(
      svg("polyline", {
        points: coords.map((p) => p.join(",")).join(" "),
        fill: "none",
        stroke: "#2c7b7b",
        "stroke-width": 2,
      }),
    );
    coords.forEach(([x, y], i) => {
      this.chart.append(
        svg("circle", { cx: x, cy: y, r: 3.5, fill: "#2c7b7b" }),
        svg(
          "text",
          {
            x,
            y: 138,
            "text-anchor": "middle",
            "font-size": 9,
            fill: "#8a7765",
          },
          history[i].depth,
        ),
      );
    });
    this.chart.setAttribute(
      "aria-label",
      "Score by completed depth: " +
        history
          .map((r) => `${r.depth}: ${scoreText(r.score * sign)}`)
          .join(", "),
    );
  }
}
