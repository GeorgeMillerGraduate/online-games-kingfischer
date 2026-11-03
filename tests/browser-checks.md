# Chrome UI checks — 31 October 2026

Observed on the locally hosted app through the browser UI:

- Classic analysis: the final three-second start-position search completed depth 4 with 28,704 visited nodes; three candidate positions and a legal PV were displayed.
- Preview: Explore line changed the viewed board and displayed “Preview only”; Return to live board restored the actual position. Subsequent e2-e4 was applied to the live game.
- Player vs engine: entering e2-e4 as White produced an automatic Black reply, Nc6, with Black set to effort level 2.
- Engine vs engine: White level 1 and Black level 2 played more than 18 moves; Pause stopped the match. This is an interaction check, not a playing-strength benchmark.
- Original NNUE: Load bundled NNUE completed its architecture validation. A three-second search completed depth 3 with 7,808 nodes and candidate suggestions e4/c4/d4.
- Promotion: loading a white pawn on a7 and moving to a8 displayed four promotion buttons. Selecting Knight created the knight and ended K+N versus K as an insufficient-material draw.
- FEN loading: the promotion fixture and Kiwipete fixture were accepted through the position tools.
- Depth-step button: launches a bounded new search and does not automatically play a move in analysis. On the tactical Kiwipete position a short budget could not complete depth one; the final app displayed three explicitly labelled static previews and legal Explore-line previews rather than blank candidate boards.

The browser automation download listener timed out while checking PGN. No application JavaScript error was reported. Export construction is implemented and inspected; successful download capture is not asserted here. No mobile hardware or Safari/Firefox interaction testing was performed. The responsive CSS includes narrower layouts; they are not a claim of device testing.

These observations are separate from the repeatable Node/Java tests in results.json and nnue-results.json. Timing varies by device and browser.
