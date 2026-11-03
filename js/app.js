// SPDX-License-Identifier: GPL-3.0-or-later
import { Position } from "./engine/Position.js";
import { Move } from "./engine/Move.js";
import { MoveGenerator as G } from "./engine/MoveGenerator.js";
import { Evaluator } from "./engine/Evaluator.js";
import { ChessBoard } from "./ui/ChessBoard.js";
import { EngineClient } from "./ui/EngineClient.js";
import { Notebook, scoreText } from "./ui/Notebook.js";
/** jQuery owns UI events and transitions; chess/worker/render classes stay independent. */
class KingFischerApp {
  constructor() {
    this.initialFen = Position.START;
    this.p = new Position();
    this.uciMoves = [];
    this.records = [];
    this.lastMove = 0;
    this.latest = null;
    this.previewing = false;
    this.busy = false;
    this.auto = false;
    this.token = 0;
    this.rootPosition = null;
    this.searchStarted = 0;
    this.totalMs = 3000;
    this.eval = new Evaluator();
    this.board = new ChessBoard(document.querySelector("#main-board"), {
      interactive: true,
      onMove: (choices) => this.chooseMove(choices),
    });
    this.notebook = new Notebook();
    this.engine = new EngineClient((d) => this.engineMessage(d));
    this.bind();
    this.populateStrengths();
    this.notebook.clear();
    this.render();
    this.emptyCandidates();
    this.tick();
  }
  bind() {
    const $ = window.jQuery;
    $(".time-presets button").on("click", (e) => {
      $("#time-limit").val(e.currentTarget.dataset.time);
      $(".time-presets button").removeClass("chosen");
      $(e.currentTarget).addClass("chosen");
      this.timerLabel();
    });
    $("#time-limit").on("input", () => {
      $(".time-presets button").removeClass("chosen");
      this.timerLabel();
    });
    $("#start").on("click", () => {
      this.auto = $("#mode").val() === "engines";
      this.search(false);
    });
    $("#pause").on("click", () => this.pause());
    $("#use-best").on("click", () => {
      const best = this.latest?.bestMove || G.legal(this.p)[0];
      this.pause();
      if (best) this.play(best);
    });
    $("#step").on("click", () => {
      this.auto = false;
      this.search(true);
    });
    $("#flip").on("click", () => this.board.flip());
    $("#new-game").on("click", () => this.reset(Position.START));
    $("#undo").on("click", () => this.undo());
    $("#return-live").on("click", () => this.returnLive());
    $("#mode,#human-color").on("change", () => {
      this.pause();
      this.returnLive();
      this.render();
      this.message(
        $("#mode").val() === "engines"
          ? "Press Start engine to begin the match. Each side uses its own effort level."
          : $("#mode").val() === "analysis"
            ? "Move either side, then Start engine to analyse. Analysis does not play its suggestion."
            : "Your colour is set. Press Start engine if you want the engine to move first.",
      );
    });
    $("#white-strength,#black-strength,#evaluation-mode").on("change", () => {
      this.pause();
      this.latest = null;
      this.emptyCandidates();
      this.notebook.clear();
      this.render();
    });
    $("#network-file").on("change", (e) => this.loadNetwork(e.target.files[0]));
    $("#load-bundled").on("click", () => this.loadBundled());
    $("#load-fen").on("click", () => {
      try {
        const fen = $("#fen-input").val().trim();
        new Position(fen);
        this.reset(fen);
        this.message("Position loaded. Search to explore it.");
      } catch (e) {
        this.message(e.message, true);
      }
    });
    $("#copy-fen").on("click", async () => {
      try {
        await navigator.clipboard.writeText(this.p.fen());
        this.message("FEN copied.");
      } catch {
        $("#fen-input").val(this.p.fen()).trigger("focus").trigger("select");
        this.message("Select and copy the FEN shown below.");
      }
    });
    $("#export-pgn").on("click", () => this.exportPGN());
    $(".section-nav a").on("click", (e) => {
      $(".section-nav a").removeClass("active");
      $(e.currentTarget).addClass("active");
    });
    window.addEventListener("beforeunload", () =>
      this.engine.worker.terminate(),
    );
  }
  populateStrengths() {
    for (const id of ["white-strength", "black-strength"]) {
      const s = document.getElementById(id);
      for (let i = 1; i <= 10; i++) {
        const o = document.createElement("option");
        o.value = i;
        o.textContent = `Level ${i}${i === 1 ? " · light" : i === 10 ? " · full" : ""}`;
        s.append(o);
      }
      s.value = id === "white-strength" ? "6" : "8";
    }
  }
  message(text, error = false) {
    $("#message").text(text).toggleClass("error", error);
  }
  timerLabel() {
    if (!this.busy)
      $("#timer-readout").text(this.seconds().toFixed(1) + " s / move");
  }
  seconds() {
    const n = Number($("#time-limit").val());
    return Number.isFinite(n) ? Math.max(0.1, Math.min(120, n)) : 3;
  }
  terminal() {
    const legal = G.legal(this.p);
    if (!legal.length)
      return this.p.inCheck()
        ? {
            text: `Checkmate · ${this.p.side ? "White" : "Black"} wins`,
            result: this.p.side ? "1-0" : "0-1",
          }
        : { text: "Draw · stalemate", result: "1/2-1/2" };
    if (this.p.insufficientMaterial())
      return { text: "Draw · insufficient mating material", result: "1/2-1/2" };
    if (this.p.halfmove >= 100)
      return { text: "Draw · fifty-move rule", result: "1/2-1/2" };
    if (this.p.repetition(0))
      return { text: "Draw · threefold repetition", result: "1/2-1/2" };
    return null;
  }
  render() {
    const mode = $("#mode").val(),
      human = +$("#human-color").val(),
      end = this.terminal();
    if (!this.previewing)
      this.board.set(this.p, {
        enabled:
          !this.busy &&
          !this.previewing &&
          !end &&
          (mode === "analysis" || (mode === "player" && this.p.side === human)),
        lastMove: this.lastMove,
        arrow:
          this.latest && this.rootPosition?.fen() === this.p.fen()
            ? this.latest.bestMove
            : 0,
      });
    $("#turn-label").text(
      end
        ? end.text
        : `${this.p.side ? "Black" : "White"} to move${this.p.inCheck() ? " · check" : ""}`,
    );
    $("#human-color").prop("disabled", mode !== "player");
    $("#start").prop("disabled", this.busy || !!end || this.previewing);
    $("#step").prop("disabled", this.busy || !!end || this.previewing);
    $("#pause").prop("disabled", !this.busy && !this.auto);
    $("#use-best").prop("disabled", !this.busy && !this.latest?.bestMove);
    $("#undo").prop("disabled", !this.uciMoves.length);
    $("#evaluation-mode").prop("disabled", this.busy);
    $("#network-file,#load-bundled").prop("disabled", this.busy);
    $("#fen-input").val(this.p.fen());
    if (!this.busy && !this.previewing && !this.latest) {
      const detail = this.eval.breakdown(this.p);
      this.notebook.evaluation(detail);
      if (!this.latest) {
        $("#position-score,#gauge-score").text(scoreText(detail.whiteScore));
        $("#eval-fill").css(
          "height",
          50 + 45 * Math.tanh(detail.whiteScore / 500) + "%",
        );
      }
    }
    document
      .querySelectorAll(".candidate footer button:last-child")
      .forEach((b) => (b.disabled = this.busy || this.auto));
    this.renderMoves();
  }
  renderMoves() {
    if (!this.records.length) {
      $("#move-list").text("No moves yet.");
      return;
    }
    $("#move-list").text(
      this.records
        .map(
          (r) =>
            `${r.side === 0 ? r.number + "." : r === this.records[0] ? r.number + "…" : ""} ${r.san}`,
        )
        .join(" "),
    );
  }
  chooseMove(choices) {
    if (choices.length === 1) {
      this.play(choices[0]);
      return;
    }
    const dialog = document.querySelector("#promotion"),
      container = document.querySelector("#promotion-choices");
    container.replaceChildren();
    for (const m of choices) {
      const b = document.createElement("button"),
        img = document.createElement("img");
      img.src = `assets/pieces/${this.p.side ? "b" : "w"}${" PNBRQK"[Move.promotion(m)]}.svg`;
      img.alt = ["", "", "Knight", "Bishop", "Rook", "Queen"][
        Move.promotion(m)
      ];
      b.append(img);
      b.onclick = () => {
        dialog.close();
        this.play(m);
      };
      container.append(b);
    }
    dialog.showModal();
  }
  play(m) {
    if (!G.legal(this.p).includes(m)) {
      this.message("That move is no longer legal in the live position.", true);
      return;
    }
    this.returnLive(false);
    const san = G.san(this.p, m),
      side = this.p.side,
      number = this.p.fullmove;
    this.records.push({ san, side, number });
    this.uciMoves.push(Move.uci(m));
    this.p.make(m);
    this.lastMove = m;
    this.latest = null;
    this.rootPosition = null;
    this.emptyCandidates();
    this.notebook.clear();
    $("#principal-variation").html(
      '<span class="muted">Search the new position to see its best line.</span>',
    );
    $("#line-depth").text("Awaiting a search");
    $("#engine-state").text("Ready");
    $("#depth-readout").text("Depth —");
    this.render();
    const end = this.terminal();
    if (end) {
      this.auto = false;
      this.message(end.text);
      return;
    }
    const mode = $("#mode").val(),
      should =
        (this.auto && mode === "engines") ||
        (mode === "player" && this.p.side !== +$("#human-color").val());
    if (should) {
      const token = this.token;
      this.nextMove = setTimeout(() => {
        if (token === this.token) this.search(false);
      }, 450);
    } else
      this.message(
        mode === "analysis"
          ? "Position changed. Start engine to analyse."
          : "Your move.",
      );
  }
  async search(step) {
    if (this.busy || this.terminal()) return;
    try {
      this.returnLive();
      const level = +$(
          "#" + (this.p.side ? "black" : "white") + "-strength",
        ).val(),
        depths = [0, 1, 2, 3, 4, 5, 6, 8, 10, 16, 64],
        nodes = [
          0,
          500,
          1500,
          5000,
          15000,
          40000,
          100000,
          250000,
          600000,
          1500000,
          Infinity,
        ];
      const maxDepth = step
        ? Math.min(64, (this.latest?.depth || 0) + 1)
        : depths[level];
      this.rootPosition = this.p.copy();
      this.totalMs = this.seconds() * 1000;
      this.searchStarted = performance.now();
      this.stepSearch = step;
      this.busy = true;
      this.latest = null;
      this.appliedCandidatesDepth = -1;
      this.emptyCandidates();
      this.notebook.clear();
      $("#engine-state").text("Searching");
      $("#search-badge").text("MEASURED · LIVE");
      $("body").addClass("searching");
      this.engine.search({
        initialFen: this.initialFen,
        moves: this.uciMoves,
        useNNUE: $("#evaluation-mode").val() === "nnue",
        options: {
          timeMs: this.totalMs,
          maxDepth,
          nodeLimit: step ? Infinity : nodes[level],
          graphicsHz: +$("#graphics-rate").val(),
        },
      });
      this.render();
      this.message(
        step
          ? `Searching up to depth ${maxDepth}; this will not play a move.`
          : `Searching for ${this.p.side ? "Black" : "White"} · level ${level} · at most ${this.seconds()} seconds.`,
      );
    } catch (e) {
      this.busy = false;
      $("body").removeClass("searching");
      this.render();
      this.message(e.message, true);
    }
  }
  pause() {
    this.token++;
    clearTimeout(this.nextMove);
    this.auto = false;
    if (this.busy) this.engine.cancel();
    this.busy = false;
    $("body").removeClass("searching");
    $("#engine-state").text("Paused");
    $("#time-progress").val(0);
    this.render();
    this.message("Paused. Completed candidate lines remain available.");
  }
  engineMessage(d) {
    if (d.type === "info") {
      this.latest = d.snapshot;
      this.updateSnapshot(d.snapshot);
      return;
    }
    if (d.type === "done") {
      this.latest = d.snapshot;
      this.busy = false;
      $("body").removeClass("searching");
      this.updateSnapshot(d.snapshot);
      $("#engine-state").text("Search complete");
      $("#timer-readout").text(
        (d.snapshot.elapsed / 1000).toFixed(2) + " s elapsed",
      );
      $("#time-progress").val(1);
      this.render();
      this.message(
        `${d.snapshot.reason || "Complete"} · depth ${d.snapshot.depth} · ${d.snapshot.nodes.toLocaleString()} nodes.`,
      );
      const mode = $("#mode").val();
      if (
        !this.stepSearch &&
        (mode === "player" || mode === "engines") &&
        d.snapshot.bestMove &&
        !d.snapshot.terminal
      ) {
        const token = this.token;
        this.nextMove = setTimeout(() => {
          if (token === this.token) this.play(d.snapshot.bestMove);
        }, 250);
      }
      return;
    }
    if (d.type === "error") {
      this.busy = false;
      this.auto = false;
      $("body").removeClass("searching");
      this.render();
      this.message(d.message, true);
      $("#engine-state").text("Engine error");
      return;
    }
    if (d.type === "network-progress") {
      $("#network-status").text(d.message);
      return;
    }
    if (d.type === "network-ready") {
      $("#network-status").text(
        "Original NNUE loaded and architecture validated. Ready for search.",
      );
      $("#evaluation-mode").val("nnue");
      this.message(
        "Original neural-network evaluator ready. It uses more memory and CPU than classic mode.",
      );
      return;
    }
    if (d.type === "ready" && location.protocol === "file:")
      this.message(
        "Open this page through the included launcher or your website; module workers cannot run from file://.",
        true,
      );
  }
  updateSnapshot(s) {
    if (!this.rootPosition || this.rootPosition.fen() !== this.p.fen()) return;
    $("#depth-readout").text(
      `Depth ${s.depth}${s.partial ? " → " + s.workingDepth : ""}`,
    );
    if (s.current) $("#engine-state").text(`Testing ${s.current.move}`);
    this.notebook.update(s, this.rootPosition);
    if (s.depth !== this.appliedCandidatesDepth) {
      this.appliedCandidatesDepth = s.depth;
      this.renderCandidates(s);
      this.renderPV(s);
    }
    $("#use-best").prop("disabled", !s.bestMove);
    if (!this.previewing) this.board.drawArrow(s.bestMove);
  }
  renderPV(s) {
    const el = document.querySelector("#principal-variation");
    el.replaceChildren();
    const r = s.lines?.[0];
    $("#line-depth").text(
      s.depth
        ? `Completed depth ${s.depth}`
        : "Static preview · not a searched depth",
    );
    if (!r || r.unsearched) {
      el.append(
        Object.assign(document.createElement("span"), {
          className: "muted",
          textContent:
            "No completed depth yet; a legal fallback move is available.",
        }),
      );
      return;
    }
    const p = this.rootPosition.copy();
    r.pv.slice(0, 10).forEach((m, i) => {
      if (!G.legal(p).includes(m)) return;
      const label = G.san(p, m);
      p.make(m);
      const fen = p.fen(),
        button = document.createElement("button");
      button.textContent = (i === 0 ? "1. " : "") + label;
      button.title = "Preview after " + label;
      button.onclick = () => this.preview(fen, m);
      el.append(button);
    });
  }
  emptyCandidates() {
    const el = document.querySelector("#candidates");
    el.replaceChildren();
    for (let i = 0; i < 3; i++) {
      const article = document.createElement("article");
      article.className = "candidate" + (!i ? " best" : "");
      const head = document.createElement("header");
      head.textContent = `0${i + 1} · awaiting search`;
      const board = document.createElement("div");
      board.className = "mini";
      article.append(head, board);
      const b = new ChessBoard(board, { mini: true });
      b.set(this.p);
      const line = document.createElement("p");
      line.className = "line-info";
      line.textContent = "A candidate position will appear here.";
      article.append(line);
      el.append(article);
    }
  }
  renderCandidates(s) {
    if (!s.depth && !s.staticPreview) return;
    const el = document.querySelector("#candidates");
    el.replaceChildren();
    s.lines.slice(0, 3).forEach((r, i) => {
      if (r.unsearched) return;
      const p = this.rootPosition.copy();
      if (!G.legal(p).includes(r.move)) return;
      const san = G.san(p, r.move);
      p.make(r.move);
      const card = document.createElement("article");
      card.className = "candidate" + (!i ? " best" : "");
      const head = document.createElement("header"),
        title = document.createElement("span"),
        score = document.createElement("span");
      title.textContent = `${i + 1}. ${san}`;
      score.className = "candidate-score";
      score.textContent = scoreText(
        r.score * (this.rootPosition.side ? -1 : 1),
      );
      head.append(title, score);
      const board = document.createElement("div");
      board.className = "mini";
      const line = document.createElement("p");
      line.className = "line-info";
      line.textContent =
        (r.staticPreview ? "Static only · " : "") +
        r.pv
          .slice(0, 6)
          .map((m) => Move.uci(m))
          .join(" ");
      const foot = document.createElement("footer"),
        explore = document.createElement("button"),
        play = document.createElement("button");
      explore.textContent = "↗ Explore line";
      explore.onclick = () => this.preview(p.fen(), r.move);
      play.textContent = "▶ Play move";
      play.disabled = this.busy || this.auto;
      play.onclick = () => {
        this.pause();
        this.play(r.move);
      };
      foot.append(explore, play);
      card.append(head, board, line, foot);
      el.append(card);
      new ChessBoard(board, { mini: true }).set(p, { lastMove: r.move });
    });
  }
  preview(fen, lastMove) {
    this.previewing = true;
    this.board.set(new Position(fen), { enabled: false, lastMove });
    $("#board-notice").text("Preview only · the live game is unchanged.");
    $("#return-live").prop("hidden", false);
    $("#start,#step").prop("disabled", true);
  }
  returnLive(update = true) {
    this.previewing = false;
    $("#return-live").prop("hidden", true);
    $("#board-notice").text("Click or drag a piece to make a legal move.");
    if (update) this.render();
  }
  reset(fen) {
    this.pause();
    this.initialFen = fen;
    this.p = new Position(fen);
    this.uciMoves = [];
    this.records = [];
    this.lastMove = 0;
    this.latest = null;
    this.rootPosition = null;
    this.returnLive();
    this.notebook.clear();
    this.emptyCandidates();
    $("#principal-variation").html(
      '<span class="muted">A completed search depth will appear here.</span>',
    );
    $("#line-depth").text("Awaiting a search");
    $("#depth-readout").text("Depth —");
    $("#engine-state").text("Ready to play");
    this.timerLabel();
    this.render();
    this.message("New position ready. Make a move or start the engine.");
  }
  undo() {
    this.pause();
    if (!this.uciMoves.length) return;
    this.p.undo();
    this.uciMoves.pop();
    this.records.pop();
    if (
      $("#mode").val() === "player" &&
      this.p.side !== +$("#human-color").val() &&
      this.uciMoves.length
    ) {
      this.p.undo();
      this.uciMoves.pop();
      this.records.pop();
    }
    this.lastMove = this.uciMoves.length ? this.p.states.at(-1)?.m || 0 : 0;
    this.latest = null;
    this.rootPosition = null;
    this.returnLive();
    this.notebook.clear();
    this.emptyCandidates();
    $("#principal-variation").html(
      '<span class="muted">Search again after taking back a move.</span>',
    );
    this.render();
  }
  async loadBundled() {
    this.pause();
    $("#load-bundled").prop("disabled", true);
    try {
      $("#network-status").text("Downloading the bundled neural network…");
      const response = await fetch(
        new URL("../networks/nn-1a298aa575a0.nnue", import.meta.url),
      );
      if (!response.ok)
        throw Error(
          "Bundled NNUE was not found. Upload the networks folder, or choose the .nnue file locally.",
        );
      const blob = await response.blob();
      if (blob.size !== 98511183)
        throw Error("The network download was incomplete or the wrong file.");
      await this.loadNetwork(blob);
    } catch (e) {
      this.message(e.message, true);
      $("#network-status").text(e.message);
    } finally {
      $("#load-bundled").prop("disabled", false);
    }
  }
  async loadNetwork(file) {
    if (!file) return;
    this.pause();
    if (file.size > 160 * 1024 * 1024 || file.size < 1000000) {
      this.message(
        "Choose the supplied nn-1a298aa575a0.nnue file (about 94 MiB).",
        true,
      );
      return;
    }
    $("#network-status").text("Reading network into the engine worker…");
    await this.engine.loadNetwork(file);
  }
  exportPGN() {
    const end = this.terminal(),
      headers = [
        '[Event "JengaCode KingFischer"]',
        '[Site "Browser"]',
        `[White "${$("#mode").val() === "engines" ? "KingFischer level " + $("#white-strength").val() : +$("#human-color").val() === 0 ? "Player" : "KingFischer"}"]`,
        `[Black "${$("#mode").val() === "engines" ? "KingFischer level " + $("#black-strength").val() : +$("#human-color").val() === 1 ? "Player" : "KingFischer"}"]`,
        `[Result "${end?.result || "*"}"]`,
      ];
    if (this.initialFen !== Position.START)
      headers.push('[SetUp "1"]', `[FEN "${this.initialFen}"]`);
    const moves = this.records
      .map(
        (r, i) =>
          `${r.side === 0 ? r.number + "." : i === 0 ? r.number + "..." : ""} ${r.san}`,
      )
      .join(" ");
    const blob = new Blob(
        [
          headers.join("\n") +
            "\n\n" +
            moves +
            " " +
            (end?.result || "*") +
            "\n",
        ],
        { type: "application/x-chess-pgn" },
      ),
      url = URL.createObjectURL(blob),
      a = document.createElement("a");
    a.href = url;
    a.download = "kingfischer-game.pgn";
    a.hidden = true;
    document.body.append(a);
    a.click();
    a.remove();
    this.message("PGN export requested. Check your browser downloads.");
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
  tick() {
    if (this.busy) {
      const ms = performance.now() - this.searchStarted;
      $("#timer-readout").text(
        `${(ms / 1000).toFixed(1)} / ${(this.totalMs / 1000).toFixed(1)} s`,
      );
      $("#time-progress").val(Math.min(1, ms / this.totalMs));
    }
    requestAnimationFrame(() => this.tick());
  }
}
// Wait for the locally bundled, deferred jQuery script, without a CDN dependency.
if (document.readyState === "loading")
  document.addEventListener("DOMContentLoaded", () => new KingFischerApp());
else new KingFischerApp();
