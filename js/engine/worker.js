// SPDX-License-Identifier: GPL-3.0-or-later
import { Position } from "./Position.js";
import { MoveGenerator as G } from "./MoveGenerator.js";
import { Evaluator } from "./Evaluator.js";
import { Network } from "./Network.js";
import { Search } from "./Search.js";
import { TranspositionTable } from "./TranspositionTable.js";
import { HistoryTables } from "./HistoryTables.js";
let network = null,
  busy = false,
  lastMode = null;
const tt = new TranspositionTable(8),
  history = new HistoryTables();
self.onmessage = async ({ data: d }) => {
  try {
    if (d.type === "load-network") {
      if (busy) throw Error("Pause before loading a network");
      postMessage({
        type: "network-progress",
        message: "Decoding the original NNUE…",
      });
      network = new Network(d.buffer);
      tt.clear();
      postMessage({ type: "network-ready", description: network.description });
      return;
    }
    if (d.type === "search") {
      if (busy) throw Error("Worker already searching");
      busy = true;
      if (lastMode !== d.useNNUE) {
        tt.clear();
        lastMode = d.useNNUE;
      }
      const p = new Position(d.initialFen);
      for (const uci of d.moves || []) p.make(G.parse(p, uci));
      const evaluator = new Evaluator(d.useNNUE ? network : null);
      if (d.useNNUE && !network) throw Error("Load the original NNUE first");
      const search = new Search(p, evaluator, tt, history);
      const result = await search.run(d.options, (snapshot) =>
        postMessage({ type: "info", id: d.id, snapshot }),
      );
      postMessage({ type: "done", id: d.id, snapshot: result });
    }
  } catch (e) {
    postMessage({
      type: "error",
      id: d.id,
      message: e.message,
      stack: e.stack,
    });
  } finally {
    if (d.type === "search") busy = false;
  }
};
postMessage({ type: "ready" });
