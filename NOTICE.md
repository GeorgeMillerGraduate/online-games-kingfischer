# Attribution and distribution notice

JengaFish 0.1.0 is an unofficial Java chess-engine derivative/reimplementation based in part on Stockfish. It is not an official Stockfish release.

Stockfish: a UCI chess playing engine derived from Glaurung 2.1.
Copyright (C) 2004–2026 The Stockfish developers (see STOCKFISH-AUTHORS).
Original project: https://github.com/official-stockfish/Stockfish
Reference commit: 59aae690f91d6f69aac194f447d84b4a2c3be778.

The Java code is supplied under GNU GPL version 3 or, at your option, any later version. See COPYING.txt for the full licence and absence of warranty. Preserve attribution and make the corresponding source available when distributing derivative binaries as required by that licence.

The directly adapted parts are principally NNUE feature indexing, network serialization and quantized inference, NNUE evaluation scaling, centipawn normalization, history gravity updates, selected search relationships, and time allocation. Other Java-specific implementations and departures are identified in docs/PORT-STATUS.md. No claim is made that the entire engine is a faithful line-by-line translation.

The supplied network nn-1a298aa575a0.nnue is the default trained network for the pinned Stockfish revision, obtained from the official Fishtest network service:
https://tests.stockfishchess.org/api/nn/nn-1a298aa575a0.nnue

SHA-256: 1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2

The embedded description reads: Network trained with the https://github.com/official-stockfish/nnue-pytorch trainer.
Stockfish's README acknowledges neural networks trained on data provided by the Leela Chess Zero project under the Open Database License. See the original project's README and training repository for that provenance; no training dataset is included here.

JUnit, Maven, and formatting tools used during development are not bundled into the executable. The Java runtime is not included.
