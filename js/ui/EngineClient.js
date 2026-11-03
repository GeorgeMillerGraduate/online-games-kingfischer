// SPDX-License-Identifier: GPL-3.0-or-later
/** Request IDs reject stale results. Main-thread watchdog bounds unresponsive searches. */
export class EngineClient {
  constructor(onMessage) {
    this.onMessage = onMessage;
    this.sequence = 0;
    this.networkFile = null;
    this.networkReady = false;
    this.boot();
  }
  boot() {
    this.worker = new Worker(new URL("../engine/worker.js", import.meta.url), {
      type: "module",
    });
    this.worker.onmessage = ({ data }) => {
      if (data.type === "ready" && this.networkFile) this.reloadNetwork();
      if (data.type === "network-ready") this.networkReady = true;
      if (data.id !== undefined && data.id !== this.activeId) return;
      if (data.type === "info") this.latest = data.snapshot;
      if (data.type === "done" || data.type === "error") {
        clearTimeout(this.watchdog);
        this.busy = false;
      }
      this.onMessage(data);
    };
    this.worker.onerror = (e) => {
      clearTimeout(this.watchdog);
      this.busy = false;
      this.onMessage({
        type: "error",
        message:
          e.message || "Engine worker could not start. Use HTTP, not file://.",
      });
    };
  }
  async loadNetwork(file) {
    this.networkReady = false;
    this.networkFile = file;
    await this.reloadNetwork();
  }
  async reloadNetwork() {
    try {
      const buffer = await this.networkFile.arrayBuffer();
      this.worker.postMessage({ type: "load-network", buffer }, [buffer]);
    } catch (e) {
      this.onMessage({ type: "error", message: e.message });
    }
  }
  search(payload) {
    if (this.busy) throw Error("Search already in progress");
    if (payload.useNNUE && !this.networkReady)
      throw Error("Wait for NNUE loading to finish");
    this.busy = true;
    this.activeId = ++this.sequence;
    this.latest = null;
    this.worker.postMessage({ type: "search", id: this.activeId, ...payload });
    this.watchdog = setTimeout(() => {
      const last = this.latest;
      this.cancel();
      this.onMessage(
        last
          ? {
              type: "done",
              snapshot: {
                ...last,
                finished: true,
                reason: "Watchdog used last completed depth",
              },
            }
          : {
              type: "error",
              message:
                "Search timed out before its first snapshot; try classic evaluation or a longer budget.",
            },
      );
    }, payload.options.timeMs + 2000);
  }
  cancel() {
    clearTimeout(this.watchdog);
    this.activeId = ++this.sequence;
    this.busy = false;
    this.worker.terminate();
    this.networkReady = false;
    this.boot();
  }
}
