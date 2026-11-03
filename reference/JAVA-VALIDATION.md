# Validation report

Reference: official Stockfish development commit `59aae690f91d6f69aac194f447d84b4a2c3be778`, compiled locally with `make -j4 build ARCH=x86-64-avx2`. Validation date: 2026-09-11.

Network SHA-256: `1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2`.

## JUnit regression suite

29 tests passed under Maven Surefire: 0 failures, 0 errors, 0 skipped; approximately 8.0 seconds. The full report is included as `junit-results.txt`. Four of these cases are mate-in-two positions independently verified with Stockfish at depth 8.

Verified initial-position perft: 20; 400; 8,902; 197,281; 4,865,609; 119,060,324 at depths 1–6.

Other exact counts: Kiwipete depth 4 = 4,085,603; rook/pawn endgame perft position depth 5 = 674,624; promotion/castling position depth 4 = 422,333; promotion/check position depth 4 = 2,103,487. Exact FENs are in EngineTest.java.

Randomized undo tests cover 30 sequences up to 100 plies each, comparing FEN, complete piece bitboards, occupancies, pawn keys and recomputed position hashes. NNUE tests compare incremental states against full refresh through forward and reverse sequences and separately for castling, en passant and promotion.

## Differential corpus

`differential-results.fens` contains 2,000 deterministic game positions, seed 20260911. Every position's legal root moves and perft depth 2 agree with the compiled Stockfish reference; root moves additionally agree with python-chess. Raw NNUE totals and normalized zero-optimism evaluation agree on all 1,872 positions where Stockfish's eval command accepts evaluation (side to move not in check).

This corpus consists of seeded random legal play; it is not 2,000 curated tactical puzzles or an Elo measurement. Check positions are still included in the legal-move/perft comparison.

Reproduce after installing python-chess and compiling the pinned reference:

```text
python tools/differential.py --stockfish /path/to/stockfish --jar JengaFish.jar --positions 2000
```

## Protocol lifecycle

`uci-results.json` records actual process tests. Handshake, legal depth-7 PVs, MultiPV, searchmoves, nodes=1, time limits, ponderhit, infinite-wait behavior, invalid-input recovery and quit during search all passed. On the recorded run, readiness during search took under 1 ms, stop returned in 4 ms, and `go movetime 150` returned in 143 ms (10 ms overhead configured).

```text
python tools/uci_smoke.py --jar JengaFish.jar
```

Timings include the specific host/JIT state and will differ on other machines. The initial network load is warmed up before the latency tests.

## Limits of the result

No Elo estimate, Stockfish move-for-move search parity, parallel scaling result, Syzygy result or speed equivalence is claimed. See PORT-STATUS.md for differences. The included default weights are real trained Stockfish NNUE weights; normal operation never calls the reference binary.

## Benchmark sample

A three-position `bench 5` run searched 4,541 nodes in 259 ms (17,532 nodes/s) in this environment. Search node definitions and policies differ from Stockfish, so this is a reproducible JengaFish baseline only.

The final Maven `verify` build succeeded. The shipped JAR was rechecked through the process-level UCI suite, and launched successfully from a different working directory with its adjacent network folder. Windows/NetBeans GUI interaction itself was not executed on this Linux validation host.
