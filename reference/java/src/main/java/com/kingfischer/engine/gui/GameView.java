// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import java.util.*;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/**
 * Website-style play surface. Live position and history browsing stay separate.
 */
public final class GameView extends BorderPane implements AutoCloseable {

    private final EnginePlayer engine = new EnginePlayer();
    private final Consumer<SetupDialog.Settings> newGame;
    private GameModel game = new GameModel();
    private SetupDialog.Settings settings;
    private final BoardPanel board = new BoardPanel(this::clicked);
    private final VBox moves = new VBox(0);
    private final ScrollPane moveScroll = new ScrollPane(moves);
    private final Label status = Ui.label("", "status-title"),
            detail = Ui.label("", "muted"),
            topName = Ui.label("", "player-name"),
            bottomName = Ui.label("", "player-name");
    private final Label topDetail = Ui.label("", "small-muted"),
            bottomDetail = Ui.label("", "small-muted"),
            review = Ui.label("", "review-label");
    private final Button retry
            = Ui.button(
                    "Retry engine",
                    "secondary",
                    () -> {
                        failed = false;
                        schedule();
                    });
    private final Button undo = Ui.button("Undo turn", "secondary", this::undo),
            resign = Ui.button("Resign", "text-button", this::resign);
    private int selected = -1, viewPly = -1;
    private boolean flipped, thinking, failed, closed;

    public GameView(SetupDialog.Settings settings, Consumer<SetupDialog.Settings> newGame) {
        this.settings = settings;
        this.newGame = newGame;
        flipped = settings.human() == 1;
        getStyleClass().add("game-root");
        HBox nav
                = new HBox(
                        24,
                        Ui.label("♞  KingFischer 2.0", "brand"),
                        Ui.label("PLAY", "nav-active"),
                        Ui.label("Your personal chess room", "nav-caption"),
                        Ui.spacer(),
                        Ui.label("●  Local engine", "online-badge"));
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("nav");
        setTop(nav);
        VBox boardColumn = new VBox(12);
        boardColumn.setMinWidth(360);
        StackPane boardArea = new StackPane(board);
        boardArea.setMinHeight(280);
        VBox.setVgrow(boardArea, Priority.ALWAYS);
        board.setMinSize(0, 0);
        board
                .prefWidthProperty()
                .bind(Bindings.min(boardArea.widthProperty(), boardArea.heightProperty()));
        board.prefHeightProperty().bind(board.prefWidthProperty());
        board.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox upper = player(topName, topDetail), lower = player(bottomName, bottomDetail);
        upper.maxWidthProperty().bind(board.prefWidthProperty());
        lower.maxWidthProperty().bind(board.prefWidthProperty());
        boardColumn.setAlignment(Pos.TOP_CENTER);
        boardColumn.getChildren().addAll(upper, boardArea, lower);
        HBox.setHgrow(boardColumn, Priority.ALWAYS);
        VBox side = new VBox(16);
        side.setPrefWidth(310);
        side.setMinWidth(285);
        side.setMaxWidth(330);
        VBox gameCard = new VBox(9, Ui.label("CASUAL  /  UNTIMED", "eyebrow"), status, detail, retry);
        gameCard.getStyleClass().add("card");
        status.setWrapText(true);
        detail.setWrapText(true);
        Label moveTitle = Ui.label("Moves", "section-title");
        HBox moveHead = new HBox(moveTitle, Ui.spacer(), Ui.label("ALGEBRAIC", "small-muted"));
        moveHead.setAlignment(Pos.CENTER_LEFT);
        moveHead.setPadding(new Insets(18, 18, 12, 18));
        moveScroll.setFitToWidth(true);
        moveScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        moveScroll.setMinHeight(110);
        VBox.setVgrow(moveScroll, Priority.ALWAYS);
        HBox browse
                = new HBox(
                        6,
                        Ui.button("|‹", "history-nav", () -> browse(0)),
                        Ui.button("‹", "history-nav", () -> browse(Math.max(0, currentPly() - 1))),
                        Ui.button("›", "history-nav", () -> browse(Math.min(game.size(), currentPly() + 1))),
                        Ui.button("›|", "history-nav", () -> browse(game.size())),
                        Ui.spacer(),
                        Ui.button("Live", "live-button", () -> browse(game.size())));
        browse.setPadding(new Insets(10));
        browse.setAlignment(Pos.CENTER_LEFT);
        VBox moveCard = new VBox(moveHead, moveScroll, browse);
        moveCard.getStyleClass().add("move-card");
        VBox.setVgrow(moveCard, Priority.ALWAYS);
        HBox controls
                = new HBox(
                        8,
                        undo,
                        Ui.button(
                                "Flip board",
                                "secondary",
                                () -> {
                                    board.stopAnimation();
                                    flipped = !flipped;
                                    refresh();
                                }));
        undo.setId("undo-turn");
        undo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(undo, Priority.ALWAYS);
        ComboBox<BoardTheme> themes = new ComboBox<>();
        themes.getItems().setAll(BoardTheme.values());
        themes.setValue(settings.theme());
        themes.setMaxWidth(Double.MAX_VALUE);
        themes.setOnAction(
                e -> {
                    this.settings
                    = new SetupDialog.Settings(
                            this.settings.human(), this.settings.strength(), themes.getValue());
                    refresh();
                });
        Button fresh = Ui.button("+  New game", "primary", () -> newGame.accept(this.settings));
        fresh.setMaxWidth(Double.MAX_VALUE);
        Button copy
                = Ui.button(
                        "Copy PGN",
                        "text-button",
                        () -> {
                            ClipboardContent c = new ClipboardContent();
                            c.putString(game.pgn(settings.human()));
                            Clipboard.getSystemClipboard().setContent(c);
                            detail.setText("Game copied as standard PGN.");
                        });
        HBox bottom = new HBox(10, copy, Ui.spacer(), resign);
        side.getChildren().addAll(gameCard, moveCard, review, controls, themes, fresh, bottom);
        HBox body = new HBox(26, boardColumn, side);
        body.setPadding(new Insets(24, 30, 16, 30));
        setCenter(body);
        Label footer = Ui.label("KingFischer 2.0   ·   A little focus. A better move.", "footer");
        footer.setPadding(new Insets(0, 30, 14, 30));
        setBottom(footer);
        setOnKeyPressed(
                e -> {
                    if (e.getTarget() instanceof ComboBoxBase<?>) {
                        return;
                    }
                    if (e.getCode() == KeyCode.LEFT) {
                        browse(Math.max(0, currentPly() - 1));
                        e.consume();
                    } else if (e.getCode() == KeyCode.RIGHT) {
                        browse(Math.min(game.size(), currentPly() + 1));
                        e.consume();
                    }
                });
        refresh();
    }

    private HBox player(Label name, Label description) {
        Label icon = Ui.label("♟", "avatar");
        VBox text = new VBox(3, name, description);
        HBox card = new HBox(12, icon, text, Ui.spacer(), Ui.label("∞", "untimed"));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("player-card");
        return card;
    }

    public void begin() {
        schedule();
    }

    private int currentPly() {
        return viewPly < 0 ? game.size() : viewPly;
    }

    private void browse(int ply) {
        board.stopAnimation();
        viewPly = ply == game.size() ? -1 : ply;
        selected = -1;
        refresh();
    }

    private void clicked(int square) {
        if (thinking
                || failed
                || viewPly >= 0
                || game.result() != null
                || game.position().side != settings.human()) {
            return;
        }
        if (selected >= 0) {
            int[] choices
                    = Arrays.stream(game.legal())
                            .filter(m -> Move.from(m) == selected && Move.to(m) == square)
                            .toArray();
            if (choices.length > 0) {
                int move = choices[0];
                if (choices.length > 1) {
                    ChoiceDialog<String> promotion
                            = new ChoiceDialog<>("Queen", "Queen", "Rook", "Bishop", "Knight");
                    promotion.setTitle("Pawn promotion");
                    promotion.setHeaderText("Choose your new piece");
                    promotion.setContentText("Promote to:");
                    promotion.initOwner(getScene().getWindow());
                    Optional<String> answer = promotion.showAndWait();
                    if (answer.isEmpty()) {
                        return;
                    }
                    int type
                            = switch (answer.get()) {
                        case "Rook" ->
                            4;
                        case "Bishop" ->
                            3;
                        case "Knight" ->
                            2;
                        default ->
                            5;
                    };
                    for (int m : choices) {
                        if (Move.promotion(m) == type) {
                            move = m;
                        }
                    }
                }
                play(move);
                schedule();
                return;
            }
        }
        int pc = game.position().board[square];
        selected = pc != 0 && (pc >>> 3) == settings.human() ? square : -1;
        refresh();
    }

    private void play(int move) {
        int piece = game.position().board[Move.from(move)];
        game.play(move);
        selected = -1;
        refresh();
        if (viewPly < 0) {
            board.animate(move, piece);
        }
    }

    private void schedule() {
        if (closed) {
            return;
        }
        if (thinking || failed || game.result() != null || game.position().side == settings.human()) {
            refresh();
            return;
        }
        thinking = true;
        refresh();
        engine.think(
                game.position().copy(),
                settings.strength(),
                m -> {
                    thinking = false;
                    try {
                        play(m);
                    } catch (Exception ex) {
                        failure(ex.getMessage());
                    }
                },
                this::failure);
    }

    private void failure(String message) {
        thinking = false;
        failed = true;
        refresh();
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(getScene().getWindow());
        a.setTitle("Engine unavailable");
        a.setHeaderText("The engine could not finish its move");
        a.setContentText(
                message
                + "\n\nKeep the original networks folder in the project, then choose Retry engine.");
        a.show();
    }

    private void undo() {
        engine.cancel();
        board.stopAnimation();
        thinking = failed = false;
        selected = -1;
        viewPly = -1;
        game.undoTurn(settings.human());
        refresh();
        schedule();
    }

    private void resign() {
        if (game.result() != null) {
            return;
        }
        Alert a
                = new Alert(Alert.AlertType.CONFIRMATION, "Resign this game?", ButtonType.YES, ButtonType.NO);
        a.initOwner(getScene().getWindow());
        a.setHeaderText("Give this one to KingFischer 2.0?");
        if (a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            engine.cancel();
            thinking = failed = false;
            game.resign(settings.human());
            selected = -1;
            viewPly = -1;
            refresh();
        }
    }

    private void refresh() {
        int ply = currentPly();
        Position shown = viewPly < 0 ? game.position() : game.at(ply);
        String result = game.result();
        board.show(
                shown,
                settings.theme(),
                flipped,
                selected,
                ply == 0 ? 0 : game.move(ply - 1),
                viewPly < 0
                && !thinking
                && !failed
                && result == null
                && game.position().side == settings.human());
        status.setText(
                result != null
                        ? result
                        : failed
                                ? "Engine needs attention"
                                : thinking
                                        ? "KingFischer 2.0 is thinking…"
                                        : game.position().inCheck() ? "You're in check" : "Your move");
        detail.setText(
                "Strength "
                + settings.strength()
                + "  ·  "
                + (settings.human() == 0 ? "You play White" : "You play Black"));
        retry.setVisible(failed);
        retry.setManaged(failed);
        review.setText(
                viewPly < 0
                        ? "●  Live position"
                        : "Reviewing move " + ((ply + 1) / 2) + "  ·  Select Live to play");
        undo.setDisable(game.size() == 0);
        resign.setDisable(result != null);
        int bottomColor = flipped ? 1 : 0;
        topName.setText(bottomColor == settings.human() ? "KingFischer 2.0" : "You");
        bottomName.setText(bottomColor == settings.human() ? "You" : "KingFischer 2.0");
        topDetail.setText(
                (bottomColor == 0 ? "Black" : "White")
                + "  ·  "
                + (bottomColor == settings.human() ? "Engine opponent" : "Ready to play"));
        bottomDetail.setText(
                (bottomColor == 0 ? "White" : "Black")
                + "  ·  "
                + (bottomColor == settings.human() ? "Take your time" : "Engine opponent"));
        moves.getChildren().clear();
        if (game.size() == 0) {
            Label empty = Ui.label("Your story starts with the first move.", "empty-history");
            empty.setWrapText(true);
            moves.getChildren().add(empty);
        }
        for (int i = 0; i < game.size(); i += 2) {
            HBox row = new HBox(0);
            row.getStyleClass().add(i % 4 == 0 ? "move-row" : "move-row-alt");
            Label number = Ui.label(Integer.toString(i / 2 + 1) + ".", "move-number");
            number.setMinWidth(42);
            row.getChildren().add(number);
            for (int j = i; j < Math.min(i + 2, game.size()); j++) {
                int target = j + 1;
                Button b = Ui.button(game.notation(j), "move-cell", () -> browse(target));
                b.setMaxWidth(Double.MAX_VALUE);
                b.setPrefWidth(115);
                HBox.setHgrow(b, Priority.ALWAYS);
                if (target == ply) {
                    b.getStyleClass().add("current-move");
                }
                row.getChildren().add(b);
            }
            moves.getChildren().add(row);
        }
        if (viewPly < 0) {
            javafx.application.Platform.runLater(() -> moveScroll.setVvalue(1));
        }
    }

    // Package-visible hooks exercise the real controller in GUI tests.
    GameModel model() {
        return game;
    }

    BoardPanel board() {
        return board;
    }

    boolean busy() {
        return thinking;
    }

    void chooseSquare(int square) {
        clicked(square);
    }

    void reviewPly(int ply) {
        browse(ply);
    }

    public void close() {
        closed = true;
        engine.close();
        board.stopAnimation();
    }
}
