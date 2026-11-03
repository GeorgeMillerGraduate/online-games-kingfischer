# Implementation status and source map

## Scope of this release

This delivers the first runnable text-in/text-out engine. It does **not** complete all milestones A–I in ORIGINAL-SPECIFICATION.md. Modern NNUE is implemented rather than replaced with a handcrafted evaluator. The full Stockfish search, parallel search and Syzygy parity remain unfinished.

## Source dependency map

UCI parses commands into Engine operations. Engine owns the game position, network, TT and history tables and starts one Search worker. Search calls MovePicker/MoveGenerator, Position make/undo, Evaluation and AccumulatorStack. Evaluation consumes the NNUE network's feature-transformer accumulators and bucketed layers. Network loading is independent of search. TimeManager budgets searches. The future GUI belongs outside these modules and talks through UCI.

| Subsystem | Official source inspected | Java implementation and fidelity |
| --- | --- | --- |
| Types and board | types.h, position.h/.cpp | Packed primitive moves, 64-square board and piece/color bitboards; incremental piece/pawn hash; fixed undo records. Different move encoding and hash random values. |
| Attacks | attacks.h/.cpp, bitboard.h/.cpp | Precomputed leaper masks and nearest-blocker ray trimming. Portable Java alternative to Stockfish's optimized sliding tables/SIMD paths. |
| Legal move generation | movegen.h/.cpp, position.cpp | All orthodox moves. Uses make/unmake king-safety filtering rather than Stockfish's pin/check-specialized fast filtering. No Chess960. |
| Repetition | position.cpp | Real historical keys, legal-en-passant canonicalization, twofold within search and threefold at game root; no cuckoo upcoming-repetition detector. |
| TT | tt.h/.cpp | Full-key four-entry clusters in primitive arrays; depth/age replacement, bound and mate-score conversion, halfmove-sensitive search key. Different packing/replacement from Stockfish's three-entry clusters. One writer. |
| Move picker | movepick.h/.cpp | TT / sound captures+promotions / quiet history / losing-capture priority bands. Selection on demand. Legal moves are generated eagerly; not the exact staged Stockfish generator. |
| History | history.h | Main, capture, continuation, pawn and pawn-key correction histories. Stockfish-style bounded gravity updates, reduced table scope/sizes. No low-ply, separate minor/nonpawn correction or continuation-correction tables. |
| SEE | position.cpp see_ge | Java legal least-valuable-attacker swap-off, dynamic occupancy, x-rays, promotions and king safety. Different implementation from the threshold-oriented C++ routine. |
| Root search | search.cpp | Iterative deepening, aspiration windows, PVS, completed-iteration fallback, legal PV output, MultiPV. Root ordering/time-stability logic is not identical. |
| Main search | search.cpp | TT cutoffs, mate distance bounds, draw handling, qsearch, razoring, reverse futility, null move with deep verification, internal iterative reduction, ProbCut, singular extension, history/futility/capture SEE pruning, history-sensitive LMR. Relationships and some constants adapted from the pinned source; conservative depth gates and reduced search context intentionally differ. |
| Qsearch | search.cpp | Stand pat only out of check; captures/promotions, all legal check evasions, terminal legality before stand pat, SEE pruning, TT and PVs. Different futility policy. |
| Features | nnue/features/half_ka_v2_hm.*, full_threats.*, pp_3wide.* | Scalar ports of the current feature sets and orientations. All three are active, including pawn-pair and friendly-piece threat relationships. |
| Network reader | nnue/network.cpp, nnue_common.h, nnue_feature_transformer.h | Current version/hash/shape validation, signed LEB128 blocks, little-endian quantized weights, all eight layer stacks; rejects truncation/trailing data. No random-weight fallback. |
| Accumulators | nnue/nnue_accumulator.*, nnue_feature_transformer.h | Lazy incremental short accumulators and int PSQT accumulators. Sorted active-feature lists are diffed against the nearest evaluated ancestor; only changed vectors are added/subtracted. Undo restores the parent frame. Does not port dirty-threat propagation, Finny king caches or SIMD. |
| Inference | nnue_architecture.h and nnue/layers/*.h | 1024-wide transformer, paired clipped-product activation, 32/32 hidden layers, squared/clipped activations, skip outputs, 8 material buckets, integer output rescaling. Scalar file-order weights, without SIMD memory permutation. |
| Evaluation | evaluate.cpp, uci.cpp score normalization | NNUE PSQT + positional, complexity/material/rule-50 scaling, centipawn polynomial. Zero optimism; search adds its own smaller correction history. The WDL model is not advertised because this engine is not strength-calibrated to Stockfish. |
| Time | timeman.cpp, search.cpp | Clock/increment/moves-to-go allocation adapted from Stockfish. Monotonic deadlines, overhead, nodes, stop, ponder. No score-stability/node-effort budgeting or nodestime. |
| UCI and engine | uci.*, ucioption.*, engine.* | Standard supported command/response syntax, asynchronous one-worker lifecycle, explicit errors for unsupported options. Diagnostic output and the option set differ. |
| Parallelism | thread.h/.cpp, search.h | One search thread plus input thread. No Lazy SMP, NUMA binding, shared histories or multiworker voting. Threads advertises min=max=1. |
| Syzygy | syzygy API layout | Not implemented or advertised. No stub returning invented tablebase results. |
| Benchmark | benchmark.*, perft.h | Three-position deterministic benchmark and perft/divide. Does not claim Stockfish's bench signature. |

## Known boundaries

- This is orthodox chess only. Six-field FENs are checked and must describe legal king counts, piece ranks, and castling/en-passant state. Full retrograde legality is not verified.
- Position history is capped at 4,096 plies; search is capped at 240 plies internally and 128 nominal depth. An input that exceeds the history capacity is rejected.
- Public Position.copy() carries repetition history for forward search, but is not an API for undoing moves made before the copy. The worker only undoes its own search moves.
- NNUE feature enumeration/sorting and eager legal-move filtering cost more than Stockfish's specialized C++ paths. Primitive arrays are reused in the node loop; there is no per-node Position or Move object allocation.
- Scalar inference was matched on the included 1,872-position non-check evaluation corpus. That is evidence on the corpus, not exhaustive verification of every integer edge case or every compatible trained network.
- Search heuristics have not been Elo-tested or self-play tuned. Stockfish evaluation weights do not automatically confer Stockfish strength on a different search implementation.
- Syzygy, multiworker search, Chess960, WDL display, skill/Elo controls, NUMA controls and nodestime are not implemented.
- Basic SEE tests and search PV legality tests pass; large SEE-specific differential validation and tactical/endgame strength suites remain future work.
- Developer perft and bench are synchronous. Do not request huge perft depths expecting the UCI stop command to cancel them.

## Next engineering work

1. Expand tactical/mate/endgame and SEE-specific oracle coverage; measure search divergence at fixed nodes with SearchTrace.
2. Port dirty-feature updates and king accumulator caches to reduce feature-enumeration cost while retaining the scalar full-refresh oracle.
3. Port the remaining histories, correction contexts and search relationships one subsystem at a time, with controlled match testing.
4. Add controlled multiworker search, Chess960 and Syzygy, each behind its own correctness gate.
5. Profile throughput/allocation under Java Flight Recorder or JMH, then consider optional Vector API acceleration with scalar-equivalence tests.
6. Add the separate JavaFX GUI once the chosen engine interface is accepted.
