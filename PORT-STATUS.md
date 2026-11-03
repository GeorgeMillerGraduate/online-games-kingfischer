# KingFischer JS — port status and validation

Date: 31 October 2026. Browser edition 1.0.0.

This report describes the files in this ZIP, not a promised future version. The input was the uploaded KingFischer2-main Java project, commit recorded by its ZIP as e092601ebd54a58664f220d5337bd377a86d36bd. That source is a modern Stockfish-derived NNUE engine, not a classic piece-square-table-only minimax program.

## Direct ports / close structural ports

| Component | Browser implementation | Validation |
|---|---|---|
| Move packing | Identical packed from/to/promotion/EP/castle fields and UCI notation | Legal-move differential and perft |
| Attacks | BigInt bitboards, leaper masks and equivalent occupancy rays | Legal-move differential |
| Position | Incremental pieces/colours, make/undo, castling, promotion, legal en passant, counters, repetition and insufficient material | Perft suites; 1,513 Java comparisons; random make/undo |
| Zobrist | Identical SplitMix64 seed/order, EP legality canonicalisation | 1,513 exact Java hash comparisons |
| Static exchange | Legal least-valuable-attacker swap-off, x-rays, promotion and king safety | Used in tested search; no exhaustive standalone SEE oracle claim |
| Transposition table | Four-way clustered full-key typed arrays, aged replacement, bound/depth, mate normalisation, halfmove-sensitive search keys | Focused bound/key/mate tests; used in searches |
| NNUE reader | Strict header/architecture checks, signed LEB128, byte/int/short weights, eight layer stacks | Original supplied 98,511,183-byte network loads |
| NNUE features | HalfKAv2_hm, FullThreats and PP_3Wide feature layout and orientation | Exact raw/evaluation comparisons on 72 Java oracle positions |
| NNUE inference | Scalar quantised layers, integer wrapping and lazy ancestor feature-list diffs | Exact PSQT, positional and final evaluation; 45 incremental/undo positions |
| Evaluation normalisation | Original complexity/material/rule-50 transformations and displayed centipawn normalisation | Final internal evaluation matched on the 72 sampled positions |

## Search: working adaptation, not identical Java search

Implemented: iterative deepening, full-window root MultiPV, alpha-beta negamax with principal-variation search below the root, legal checks/evasions, mate-distance bounds, draw detection, quiescence, legal SEE move ordering and conservative losing-capture pruning, clustered TT cutoffs, main/capture gravity histories, killer moves, conservative null-move pruning and late-move reductions with re-search. An initial, explicitly labelled static first-move ranking supplies the fallback. Completed search iterations replace it; interrupted iterations do not. Classic evaluation uses a bounded 32,768-entry position-score cache. Quiescence also probes/stores bound-aware TT entries, following the Java structure.

The browser evaluates **every legal root move at each completed depth**, so searched candidates come from the same completed depth. Before depth one completes, up to three static first-move previews are shown with explicit “static only” labels; they are not added to the depth chart. This is deliberately more expensive than playing-strength-oriented single-PV root search. It yields between root moves and throttles UI snapshots independently. Most deep search work stays in a single Worker.

Not ported in this edition: Java aspiration windows, full continuation/pawn/correction histories, original numeric LMR formula, razoring/reverse-futility/history/futility pruning, IIR, ProbCut, singular extensions and deep null verification. The main/capture histories and LMR/null logic are smaller adaptations, not exact translations of their full Java equivalents. Root MultiPV snapshots, browser cancellation/watchdog, killer ordering and UI inspection are browser additions.

Tactical positions can use the whole short budget in first-iteration quiescence. In that case the UI retains the static preview and reports depth zero, rather than suggesting a completed search. This is a speed/strength limit, not evidence that a static score has solved the position.

The explainable classic evaluator is **new**, not the Java NNUE. Its material/activity/pawn/king/mobility bars are computed directly from the displayed search root or idle live position. When NNUE is selected, the notebook switches to genuine PSQT/network/complexity/material-scaling/rule-50 terms in internal units. These bars must not be interpreted as neural explanations of individual hidden neurons.

No measured Elo, Java playing-strength parity, Stockfish strength, tablebases or tournament validation is claimed. Separate per-side effort levels constrain depth/nodes/time, not guaranteed ratings.

## Recorded executable tests

`tests/results.json`, `tests/nnue-results.json`, and the executable test scripts are included. Measurements are from this container and are not advertised browser benchmarks.

- Start-position perft: 20, 400, 8,902, **197,281** (depths 1–4).
- Kiwipete perft: 48, 2,039, **97,862** (depths 1–3).
- Rook ending perft: 14, 191, **2,812**.
- Promotion/castling test perft: 6, 264, **9,467**.
- Underpromotion test perft: 44, 1,486, **62,379**.
- All **1,513** positions present in the supplied FEN corpus matched Java legal-move sets and Zobrist hashes. The original Java project's own report says 2,000, but this delivered comparison uses the actual 1,513 lines supplied; its earlier counts are not reused as new test results.
- **72** non-check positions among the first 80 oracle positions matched Java raw PSQT, raw positional output and final evaluation exactly. This is a sample, not exhaustive NNUE equivalence over all positions.
- **45** incremental/undo NNUE positions matched freshly rebuilt accumulators.
- 80-ply make/undo and recomputed-key checks; threefold repetition; pinned EP legality/hash; TT bounds/full-key/mate normalisation.
- Search fixtures: time-bound legal fallback, forced mate in one, check evasion, promotion, legal reported PVs and restoration of the root position after abort/completion.
- Eight consecutive legal plies with different depth ceilings for White and Black.

## Browser checks

The locally hosted page was exercised in Chrome: classic timed search, three candidate previews and return to live board, legal e2-e4 entry, automatic engine-vs-engine play with different side levels, pause, bundled NNUE loading and original-network search. During development, a three-second browser NNUE search completed depth 3 with 7,808 searched nodes. After adding quiescence TT entries and a classic evaluation cache, the final classic browser start-position search completed depth 4 with 28,704 nodes in three seconds. These individual runs are illustrative, not speed guarantees. Short-budget original-NNUE search checks are also recorded in tests/nnue-search-results.json.

Browser promotion selection, player reply, step/cancel and export checks are recorded separately in `tests/browser-checks.md` when completed. A static screenshot alone is not evidence that an engine action works.

## Honest limits

- Single search Worker. BigInt bitboards and scalar NNUE trade native speed for transparent JavaScript portability. No SIMD/WASM or multiple search threads in this edition.
- Network decoding consumes considerable memory. Original network is optional and never silently fetched on page load.
- Time checks occur every 32 search nodes and between roots. Slow evaluation/generation can overshoot slightly; a separate main-thread watchdog allows two seconds of grace before restarting a stuck worker. Hard real-time timing is not guaranteed by browsers.
- Pause terminates active Worker work. If NNUE was loaded, the replacement worker reloads it before another NNUE search; the UI reports loading and rejects a premature search.
- Tree view is a bounded sample plus completed candidate variations. It does not draw every visited node, and “One depth” is not instruction-level stepping.
- Standard chess only; casual automatic repetition/fifty-move draws. No online play, puzzles, PGN import, Chess960, opening book or cloud analysis.
- Generated portrait is an artistic interpretation of Bobby Fischer in Victorian dress, not a photo.

## Reproduce

`node tests/run.js`

`node tests/check-nnue.js`

The Java oracle source and exact TSV output are provided. To rebuild the oracle, compile the non-GUI engine package from `reference/java/src/main/java`, plus `tests/JavaOracle.java`, then run JavaOracle with the original NNUE path and `tests/differential.fens`. Java is a developer cross-check only; end users do not need it.
