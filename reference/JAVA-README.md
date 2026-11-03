# KingFischer2 0.2.0

KingFischer2 is a Java 17 chess engine, graphical chess application, and puzzle trainer packaged as a NetBeans-oriented Maven project.

The project contains its own Java chess engine, a graphical chess board, human-vs-engine play, and a chess puzzle system using the Lichess open puzzle database.

KingFischer2 searches its own moves and does not invoke Stockfish at runtime.

> **Release status:** KingFischer2 is an actively developed chess project featuring a working Java chess engine, NNUE evaluation, selective search, a graphical chess interface, and Lichess puzzle support.
>
> It is **not a complete Stockfish port**, and does not promise identical best moves, depth, speed, Elo, or support for every Stockfish option.
>
> See [Implementation Status](docs/PORT-STATUS.md) for further technical details.

---

# Features

KingFischer2 currently includes:

- Java chess engine
- JavaFX graphical chess interface
- Human-vs-engine chess
- Legal move generation
- Selective alpha-beta search
- NNUE evaluation
- UCI protocol support
- FEN position support
- Transposition table
- MultiPV analysis
- Time and node search limits
- Perft and divide testing
- Engine benchmarking
- Lichess chess puzzle mode
- Puzzle filtering by rating
- Puzzle filtering by theme
- Puzzle progress tracking
- Windows executable
- NetBeans/Maven source project

---

# Quick Start on Windows

A prebuilt Windows executable is included:

```text
run_chess_engine.exe
```

This is the simplest way to start KingFischer2 on Windows.

1. Download or clone the complete repository.
2. Keep the project files and `networks` directory together.
3. Double-click:

```text
run_chess_engine.exe
```

The application can then launch the KingFischer2 chess environment.

The underlying chess engine can also be operated using standard UCI commands.

---

# Windows Executable

The supplied Windows executable is:

```text
run_chess_engine.exe
```

It is intended for:

```text
64-bit Windows
```

The underlying chess application and chess engine are written in Java.

Keep the supplied `networks` directory with the engine files. It contains the trained NNUE network used by the evaluation system.

The NNUE network is approximately **94 MiB**.

---

# Graphical Chess Interface

KingFischer2 includes a graphical chess interface built in Java.

The GUI provides a visual chess board and controls around the underlying chess engine.

The graphical application supports normal chess interaction while keeping the chess engine itself separate from the presentation layer.

The GUI code includes components for:

- Chess board display
- Chess-piece images
- Move handling
- Game state
- Engine-controlled players
- Board themes
- Game setup
- Puzzle setup
- Puzzle play

The application therefore functions as more than a command-line UCI engine.

---

# Chess Puzzle Mode

KingFischer2 includes a chess puzzle trainer based on the **Lichess open puzzle database**.

The puzzle database contains millions of real chess positions with ratings and tactical themes.

The database itself is **not included in this GitHub repository** because the extracted CSV is over 1 GB and exceeds GitHub's normal individual-file size limit.

## Download the Lichess Puzzle Database

The official Lichess database can be downloaded here:

**https://database.lichess.org/#puzzles**

Locate:

```text
lichess_db_puzzle.csv.zst
```

under the **Puzzles** section.

Lichess distributes the database as a compressed Zstandard (`.zst`) file.

Extract it to obtain:

```text
lichess_db_puzzle.csv
```

---

# Installing the Puzzle Database

Place the extracted puzzle database in the root KingFischer2 directory.

For example:

```text
KingFischer2/
│
├── lichess_db_puzzle.csv
├── pom.xml
├── run_chess_engine.exe
├── networks/
├── src/
├── docs/
└── ...
```

The filename should be:

```text
lichess_db_puzzle.csv
```

The CSV is deliberately excluded from Git version control because of its size.

Do **not** rename the file unless the puzzle database path in the application is also changed.

---

# Lichess Puzzle Format

The Lichess puzzle database uses CSV records containing fields including:

```text
PuzzleId
FEN
Moves
Rating
RatingDeviation
Popularity
NbPlays
Themes
GameUrl
OpeningTags
DailyDate
```

Puzzle moves are represented using UCI move notation.

The database provides the starting position, solution moves, puzzle rating, tactical themes and other metadata used by the KingFischer2 puzzle system.

---

# Puzzle Filtering

KingFischer2 can select puzzles according to user-defined criteria.

The puzzle setup interface supports filtering by **rating range** and **puzzle theme**.

This allows training sessions such as:

```text
Rating: 1500–2000
Theme: Mate in 2
```

or:

```text
Rating: 1000–1500
Theme: Fork
```

The Lichess database contains a wide range of tactical themes, allowing the puzzle trainer to focus on particular areas of chess.

Examples include:

- Advanced pawn
- Advantage
- Attraction
- Capturing defender
- Crushing
- Defensive move
- Deflection
- Discovered attack
- Double check
- Endgame
- Equality
- Exposed king
- Fork
- Hanging piece
- Interference
- Kingside attack
- Mate
- Mate in 1
- Mate in 2
- Mate in 3
- Mate in 4
- Mate in 5 or more
- Middlegame
- Opening
- Pin
- Promotion
- Queenside attack
- Sacrifice
- Skewer
- Trapped piece
- Underpromotion
- X-ray attack

The exact available tags are determined by the Lichess puzzle database.

---

# Puzzle Progress

KingFischer2 contains puzzle-session and puzzle-progress functionality.

The puzzle subsystem is separated into dedicated classes responsible for:

- Reading the Lichess CSV
- Representing individual puzzles
- Filtering puzzles
- Selecting puzzles
- Managing puzzle sessions
- Recording puzzle progress
- Connecting puzzle data to the graphical interface

This keeps the puzzle system separate from the core chess engine.

---

# Lichess Database Licence

The Lichess open database exports are released under the **Creative Commons CC0 licence**.

The puzzle data is therefore downloaded separately from Lichess rather than being bundled into the KingFischer2 repository.

KingFischer2 is not affiliated with or endorsed by Lichess.

The original puzzle database and current download are available from:

**https://database.lichess.org/#puzzles**

---

# Other Operating Systems

A precompiled executable is currently provided **only for Windows**.

There are currently no packaged macOS or Linux executables.

Because KingFischer2 itself is written in Java, the source code can be compiled on other platforms using a suitable Java IDE or Maven environment.

The project is primarily configured and tested using:

- **NetBeans**
- **Maven**
- **Java 17**

Users on Linux, macOS, or other Java-compatible platforms should compile the source code themselves.

Although other Java IDEs should be capable of importing the Maven project, **NetBeans is the primary development environment for KingFischer2**.

---

# Open and Compile in NetBeans

KingFischer2 is primarily designed as a **NetBeans Maven project**.

## Requirements

- NetBeans
- JDK 17 or newer
- Maven, or the Maven installation bundled with NetBeans

## Opening the Project

1. Open **NetBeans**.
2. Select **File → Open Project**.
3. Select the `KingFischer2` directory containing `pom.xml`.
4. Set the project Java platform to **JDK 17 or newer**.
5. Select **Clean and Build**.
6. Select **Run Project**.

The supplied `nbactions.xml` configures the project for NetBeans.

Because this is a Maven project, NetBeans recognises the project through:

```text
pom.xml
```

An old-style Ant `nbproject` directory is not required.

Dependencies required for building and testing are downloaded by Maven.

---

# Compile from Source

The project can also be compiled directly with Maven if Java and Maven are installed.

From the KingFischer2 project directory:

```bash
mvn clean verify
```

The compiled JAR will be created under:

```text
target/
```

The engine can then be run using Java.

For example:

```bash
java -Xmx768m -jar target/KingFischer2.jar
```

Run the engine from the project directory so that required external resources, including the NNUE network, can be located.

The exact JAR filename is determined by the Maven configuration in `pom.xml`.

---

# UCI Engine

KingFischer2 implements the **Universal Chess Interface (UCI)** protocol.

This allows the underlying engine to be controlled independently of the graphical interface.

For example:

```text
uci
isready
position startpos moves e2e4 e7e5 g1f3
go depth 6
```

Wait for:

```text
bestmove ...
```

The engine emits standard UCI search information while searching:

```text
info depth ... score ... nodes ... pv ...
```

Move choices and scores depend on the position and search parameters, so this README does not promise a particular move.

---

# Basic UCI Usage

## Initialise UCI

```text
uci
```

The engine should eventually respond:

```text
uciok
```

## Check Readiness

```text
isready
```

Response:

```text
readyok
```

## Set a Position

Starting position:

```text
position startpos
```

With moves:

```text
position startpos moves e2e4 e7e5 g1f3
```

## Start a Search

```text
go depth 6
```

The engine eventually returns:

```text
bestmove ...
```

---

# Search Examples

## Search to Depth 10

```text
go depth 10
```

## Search 100,000 Nodes

```text
go nodes 100000
```

## Think for One Second

```text
go movetime 1000
```

## Think for Five Seconds

```text
go movetime 5000
```

## Continuous Analysis

```text
go infinite
```

Stop continuous analysis with:

```text
stop
```

Exit the engine with:

```text
quit
```

The first search loads the NNUE network. Subsequent searches reuse it.

A successful `bestmove` does **not** automatically update the UCI game position.

Send the next complete:

```text
position ... moves ...
```

command as a normal UCI chess GUI would.

---

# Supported UCI Interface

Supported commands include:

```text
uci
uciok
isready
readyok
setoption
ucinewgame
position
go
stop
ponderhit
quit
info
bestmove
debug
```

`position` accepts either:

```text
position startpos
```

or a complete FEN position.

An optional coordinate-move list may follow.

Example:

```text
position startpos moves e2e4 e7e5 g1f3 b8c6
```

Promotions use standard UCI suffixes:

```text
e7e8q
```

Orthodox castling uses:

```text
e1g1
e1c1
```

---

# Search Limits

Supported search limits include:

- `depth`
- `nodes`
- `movetime`
- `wtime`
- `btime`
- `winc`
- `binc`
- `movestogo`
- `infinite`
- `ponder`
- `mate`
- `searchmoves`

Examples:

```text
go depth 10
go nodes 100000
go movetime 5000
go infinite
```

`mate N` caps the depth at `2N` and searches for a mate within that limit.

Because KingFischer2 uses a selective search, failure to find a mate does not constitute a mathematical proof that no mate exists.

---

# UCI Options

| Option | Supported Setting |
| --- | --- |
| **Hash** | 1–512 MiB requested; power-of-two primitive storage rounds down |
| **Threads** | 1 only in this release |
| **Clear Hash** | Button |
| **Ponder** | Accepted; `go ponder` and `ponderhit` control the lifecycle |
| **MultiPV** | 1–256, capped by the legal root move count |
| **Move Overhead** | 0–5000 ms, default 10 |
| **EvalFile** | Compatible NNUE network path; default `nn-1a298aa575a0.nnue` |
| **SearchTrace** | Actual search counters; default `false` |

`Ponder` currently does not increase the normal thinking-time budget.

Unsupported options produce:

```text
info string Error: Unsupported option ...
```

Unsupported options are not advertised as working.

---

# Development Commands

KingFischer2 includes several additional commands for development and testing.

## Evaluate the Current Position

```text
eval
```

Displays the raw NNUE and scaled evaluation.

## Display FEN

```text
d
```

## Perft

```text
perft N
```

## Divide

```text
divide N
```

## UCI-Style Perft

```text
go perft N
```

## Benchmark

```text
bench [depth]
```

Perft, divide and benchmark commands are synchronous developer operations.

Use reasonable depths.

UCI `stop` applies to ordinary asynchronous `go` searches and not to these synchronous diagnostic commands.

---

# Verification

KingFischer2 includes an automated regression and validation suite.

## JUnit Tests

The existing validation suite includes **29 JUnit tests**, covering areas such as:

- Starting-position perft through depth 6
- **119,060,324 nodes** at starting-position depth 6
- Castling edge cases
- Promotions
- En passant
- State restoration
- Position hashes
- Repetition detection
- Checkmate
- Stalemate
- NNUE incremental updates
- Search node limits

## Move Generation

**2,000 deterministic positions** were compared against the reference Stockfish executable.

For these positions:

- Legal moves matched Stockfish.
- Perft depth 2 matched Stockfish.
- Legal move sets also matched `python-chess`.

## NNUE Evaluation

For **1,872 non-check positions**:

- Raw NNUE totals matched the reference.
- Normalised zero-optimism evaluation matched the reference exactly.

Positions in check are excluded from this evaluation comparison because Stockfish's `eval` command declines to evaluate them.

## UCI Protocol

Executable protocol tests include:

- Depth 7 PV legality
- MultiPV
- Restricted root moves
- Short node limits
- Short time limits
- Readiness during search
- `stop`
- Pondering
- Malformed input recovery
- `quit`

See:

[VALIDATION.md](docs/VALIDATION.md)

for detailed validation results, measured timings, machine-readable results and the exact FEN corpus.

These tests provide a regression baseline.

They are **not** an Elo measurement or proof of correctness for every possible chess position.

---

# Stockfish Reference

KingFischer2 uses Stockfish as an algorithmic and validation reference.

**Official Stockfish repository:**

https://github.com/official-stockfish/Stockfish

Reference commit:

```text
59aae690f91d6f69aac194f447d84b4a2c3be778
```

Development build dated:

```text
2026-09-09
```

Reference retrieved:

```text
2026-09-11
```

---

# Relationship to Stockfish

KingFischer2 is an **unofficial Java derivative/reimplementation**.

Adapted algorithms and NNUE-related code retain attribution to the Stockfish developers.

KingFischer2:

- Is implemented in Java.
- Implements its own chess board and move handling.
- Performs its own search.
- Runs its NNUE evaluation from Java.
- Does **not** invoke the Stockfish executable at runtime.
- Does **not** contain a hidden native Stockfish engine.
- Does **not** require Stockfish to be installed to search positions.

It should not be interpreted as an official Stockfish release.

---

# Project Structure

The main Java source is located under:

```text
src/main/java/
```

The project contains separate packages/classes for the core chess engine, graphical interface and puzzle subsystem.

The puzzle subsystem includes components such as:

```text
Puzzle.java
PuzzleCsvReader.java
PuzzleFilter.java
PuzzleProgressStore.java
PuzzleRepository.java
PuzzleSession.java
```

The GUI includes puzzle-specific views including:

```text
PuzzleSetupView.java
PuzzleView.java
```

This separation allows puzzle functionality to use the chess model without being built directly into the engine's search implementation.

---

# External Data and Resources

KingFischer2 uses two significant external resources.

## NNUE Network

Located under:

```text
networks/
```

This is required by the NNUE evaluation system.

## Lichess Puzzle Database

Downloaded separately from:

**https://database.lichess.org/#puzzles**

Extract and place:

```text
lichess_db_puzzle.csv
```

in the KingFischer2 project directory.

The puzzle database is intentionally excluded from the Git repository because of its size.

---

# Project Information

**Project:** KingFischer2  
**Version:** 0.2.0  
**Language:** Java  
**Minimum Java version:** Java 17  
**Build system:** Maven  
**Chess engine interface:** UCI  
**Graphical interface:** Java GUI  
**Primary IDE:** NetBeans  
**Windows executable:** `run_chess_engine.exe`  
**Puzzle source:** Lichess Open Database  
**Project owner/requester:** George Miller  

Java implementation and integration were prepared with AI assistance.

---

# Licence

KingFischer2 is distributed under the **GNU General Public License version 3 or later (GPLv3+)**.

See:

- [`COPYING.txt`](COPYING.txt)
- [`NOTICE.md`](NOTICE.md)
- [`STOCKFISH-AUTHORS`](STOCKFISH-AUTHORS)

The complete Java source code is included under:

```text
src/main/java/
```

The NNUE network required by the supplied engine is included under:

```text
networks/
```

The Lichess puzzle database is **not included** in this repository and must be downloaded separately.

Lichess database exports are released under CC0.

---

# Platform Support

| Platform | Current Support |
| --- | --- |
| **Windows 64-bit** | Prebuilt `run_chess_engine.exe` provided |
| **Windows + NetBeans** | Full source project supported |
| **Linux** | Compile Java source yourself |
| **macOS** | Compile Java source yourself |
| **Other Java platforms** | May work when compiled with Java 17+, but are not currently packaged or tested |

The repository is primarily intended as a **NetBeans-oriented Java project**.

Users on platforms other than Windows should import or compile the Maven source project using a suitable Java development environment.

---

# Future Development

Possible future development includes:

- Network human-vs-human chess
- Engine-vs-engine matches
- Direct testing against external UCI engines
- Self-play
- Multi-threaded search
- Search performance optimisation
- Additional Stockfish search heuristics
- Improved time management
- Elo benchmarking
- Additional UCI options
- Expanded analysis tools
- Improved GUI engine configuration
- Puzzle statistics and training history
- Additional puzzle filtering
- Automated regression tournaments

---

# Credits

KingFischer2 makes use of ideas, algorithms and/or data from major open chess projects.

Special acknowledgement is given to:

- **Stockfish developers** — engine algorithms, NNUE reference implementation and validation reference.
- **Lichess** — open chess puzzle database.
- **python-chess** — additional move-generation validation during development.

KingFischer2 is an independent project and is not an official release of Stockfish or Lichess.

---

# Puzzle Database Download

To enable the complete KingFischer2 puzzle trainer, download the official Lichess puzzle database:

**https://database.lichess.org/#puzzles**

Download:

```text
lichess_db_puzzle.csv.zst
```

Extract it and place:

```text
lichess_db_puzzle.csv
```

in the KingFischer2 project directory.

The puzzle database is intentionally kept outside the GitHub repository because the extracted database is over 1 GB.
