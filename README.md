# KingFischer JS — JengaCode chess workbench

A self-contained browser app: JavaScript classes, a module Worker, locally bundled jQuery and SVG pieces. No Java installation, account, backend service, CDN or build step is needed to play.

## Run locally

Extract the entire ZIP. On Windows, double-click **START-CHROME.bat** (Python 3 is required only for the local HTTP server). Or run `python preview_server.py`, then open the address printed in the terminal. Do not double-click index.html: browsers restrict module workers under file://.

## Install on jenga-code.com

Upload this folder's contents, including `js`, `assets`, `vendor` and `networks`, to `public_html/apps/kingfischer/`. Open `https://jenga-code.com/apps/kingfischer/` after uploading. This is an installation address, not a claim that this package is already deployed. Add that URL to your application directory when ready. The page uses its own local `style.css` and requires no changes to the existing chess app.

The bundled network is `networks/nn-1a298aa575a0.nnue` (98,511,183 bytes). It is deliberately not downloaded on page load. Choose **Load bundled NNUE** or select that file locally. Classic mode works immediately without it. Both modes run entirely in the browser. Loading NNUE can consume roughly 180–250 MiB at peak depending on browser overhead; scalar JavaScript inference is slower than a native SIMD engine.

## Controls

- Click or drag a legal piece. Promotions present a four-piece chooser.
- Player vs engine: choose your colour. The engine replies automatically; Start engine may play for the current side.
- Engine vs engine: set each side's effort level and press Start engine. Pause stops the match.
- Analyse a position: move either side or load FEN, then search; suggestions are not automatically played.
- Maximum seconds is a per-search ceiling, not a game clock. A depth/node limit can finish earlier. The main-thread safety watchdog has a further two-second grace period for an unresponsive worker.
- One depth runs a fresh search capped one depth beyond the previous completed result; it is not a single-node debugger.
- Stop & use best takes the last completed-depth move; before any completed depth, it uses the best available static first-move ranking (or an initial legal fallback). Pause cancels without playing.
- The three smaller boards show completed candidate first moves. Before depth one completes they show explicitly labelled static previews, not fully searched scores. Explore line and PV buttons preview a position without changing the game. Return to live board resumes the actual position.
- FEN loading, undo and PGN download are under Moves, positions & exports.

The engine has 10 effort levels, not verified Elo ratings. Level ceilings are depths 1,2,3,4,5,6,8,10,16,64 and node budgets 500,1500,5000,15000,40000,100000,250000,600000,1500000,unlimited. Every level also obeys the time budget. Higher levels are not guaranteed to find a different move within a very short deadline.

## Engine fidelity

Read `PORT-STATUS.md`. Position rules, move packing, Zobrist hashing, legal SEE, clustered TT and the original NNUE feature/inference pipeline are ports of the uploaded Java source. Browser search is a documented adaptation with a subset of its heuristics. The classic evaluator is new and clearly labelled. This is neither Stockfish running behind a new name nor a claim of identical Java search strength.

## Source layout

- `js/engine/`: Position, Move, Attacks, Zobrist, MoveGenerator, StaticExchange, MovePicker, HistoryTables, TranspositionTable, Evaluator, Network/NetworkReader/Features/AccumulatorStack, Search, worker.
- `js/ui/`: ChessBoard, EngineClient, Notebook.
- `js/app.js`: jQuery orchestration, modes, move history and exports.
- `tests/`: executable Node tests and recorded validation results, plus Java oracle helper and input positions.
- `reference/java/`: supplied Java source for provenance and inspection. It is not required by the browser.
- `assets/`: original generated illustration and locally served, attributed cburnett SVG pieces.

Run `node tests/run.js` to test the rules and search. Run `node tests/check-nnue.js` for the recorded Java differential comparison and incremental NNUE checks. Node 20+ recommended. Python 3 is enough to preview; Node is for developers testing the source only.

## Privacy and limitations

Positions and local NNUE files stay on the device. No telemetry, adverts, online games, puzzles, opening service or external engine is used. No game auto-save is implied: download PGN to retain a game. Orthodox chess only; no Chess960. Draws use casual automatic threefold/fifty-move adjudication, not a full tournament claim UI. See `CREDITS.html` and the included GPL notices before redistributing.
