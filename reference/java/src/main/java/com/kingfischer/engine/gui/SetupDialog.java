// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import java.util.function.Consumer;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

/**
 * Illustrated welcome screen; UI text remains real, accessible text.
 */
public final class SetupDialog {

    public record Settings(int human, int strength, BoardTheme theme) {

    }

    public static Parent content(Settings initial, Consumer<Settings> start, Runnable cancel) {
        return content(initial, start, cancel, null);
    }

    public static Parent content(Settings initial, Consumer<Settings> start, Runnable cancel, Runnable puzzles) {
        HBox root = new HBox();
        root.getStyleClass().add("welcome");
        StackPane hero = new StackPane();
        hero.setMinWidth(350);
        hero.setMinHeight(0);
        hero.setPrefWidth(405);
        hero.setMaxWidth(460);
        HBox.setHgrow(hero, Priority.ALWAYS);
        ImageView art
                = new ImageView(new Image(SetupDialog.class.getResource("welcome.png").toExternalForm()));
        art.setManaged(false);
        art.setPreserveRatio(false);
        art.fitWidthProperty().bind(hero.widthProperty());
        art.fitHeightProperty().bind(hero.heightProperty());
        art.viewportProperty()
                .bind(
                        javafx.beans.binding.Bindings.createObjectBinding(
                                () -> {
                                    double iw = art.getImage().getWidth(),
                                    ih = art.getImage().getHeight(),
                                    ratio = hero.getWidth() / Math.max(1, hero.getHeight());
                                    double cw = Math.min(iw, ih * ratio),
                                    ch = Math.min(ih, iw / Math.max(.01, ratio));
                                    return new javafx.geometry.Rectangle2D((iw - cw) / 2, (ih - ch) / 2, cw, ch);
                                },
                                hero.widthProperty(),
                                hero.heightProperty()));
        VBox words
                = new VBox(
                        14,
                        Ui.label("K I N G F I S C H E R", "hero-title"),
                        Ui.label("Java based chess engine", "hero-copy"),
                        Ui.label("Play a game or practise chess puzzles.", "hero-copy"));
        words.setPadding(new Insets(38));
        words.setMouseTransparent(true);
        words.setAlignment(Pos.TOP_LEFT);
        hero.getChildren().addAll(art, words);
        VBox form = new VBox(18);
        form.getStyleClass().add("setup-form");
        form.setPrefWidth(440);
        form.setMinWidth(390);
        form.getChildren()
                .addAll(
                        Ui.label("YOUR NEXT GAME", "eyebrow"),
                        Ui.label("Make your move.", "page-title"),
                        Ui.label("Settle in. Choose your side and your challenge.", "muted"));
        ToggleGroup sides = new ToggleGroup();
        ToggleButton white = new ToggleButton("♔  White\nYou move first"),
                black = new ToggleButton("♚  Black\nEngine moves first");
        white.setId("choose-white");
        black.setId("choose-black");
        white.setToggleGroup(sides);
        black.setToggleGroup(sides);
        white.getStyleClass().add("side-choice");
        black.getStyleClass().add("side-choice");
        white.setMaxWidth(Double.MAX_VALUE);
        black.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(white, Priority.ALWAYS);
        HBox.setHgrow(black, Priority.ALWAYS);
        sides.selectToggle(initial.human() == 0 ? white : black);
        sides
                .selectedToggleProperty()
                .addListener(
                        (o, a, b) -> {
                            if (b == null) {
                                sides.selectToggle(a);
                            }
                        });
        form.getChildren().addAll(Ui.label("PLAY AS", "eyebrow"), new HBox(12, white, black));
        Label value = Ui.label(Integer.toString(initial.strength()), "strength-value");
        HBox strengthTitle = new HBox(Ui.label("ENGINE STRENGTH", "eyebrow"), Ui.spacer(), value);
        strengthTitle.setAlignment(Pos.CENTER_LEFT);
        Slider slider = new Slider(100, 3500, initial.strength());
        slider.setId("strength");
        slider.setBlockIncrement(100);
        slider.valueProperty().addListener((o, a, b) -> value.setText(Integer.toString(b.intValue())));
        HBox ends
                = new HBox(
                        Ui.label("100 · Gentle", "muted"), Ui.spacer(), Ui.label("3500 · Full focus", "muted"));
        form.getChildren()
                .addAll(
                        strengthTitle,
                        slider,
                        ends,
                        Ui.label("Difficulty setting, not a measured Elo rating.", "small-muted"));
        ComboBox<BoardTheme> themes = new ComboBox<>();
        themes.getItems().setAll(BoardTheme.values());
        themes.setValue(initial.theme());
        themes.setMaxWidth(Double.MAX_VALUE);
        HBox swatches = new HBox(6);
        Runnable swatch
                = () -> {
                    swatches.getChildren().clear();
                    for (int i = 0; i < 8; i++) {
                        Region tile = new Region();
                        tile.setPrefSize(36, 20);
                        tile.setStyle(
                                "-fx-background-color: "
                                + (i % 2 == 0 ? css(themes.getValue().light) : css(themes.getValue().dark))
                                + "; -fx-background-radius: 3;");
                        swatches.getChildren().add(tile);
                    }
                };
        themes.setOnAction(e -> swatch.run());
        swatch.run();
        form.getChildren().addAll(Ui.label("BOARD STYLE", "eyebrow"), themes, swatches);
        Region space = new Region();
        VBox.setVgrow(space, Priority.ALWAYS);
        form.getChildren().add(space);
        Button play
                = Ui.button(
                        "Let's play  →",
                        "primary",
                        ()
                        -> start.accept(
                                new Settings(
                                        white.isSelected() ? 0 : 1, (int) slider.getValue(), themes.getValue())));
        play.setId("start-game");
        play.setDefaultButton(true);
        play.setMaxWidth(Double.MAX_VALUE);
        form.getChildren().add(play);
        if (puzzles != null) {
            Button puzzleButton = Ui.button("Chess puzzles  →", "secondary", puzzles);
            puzzleButton.setId("open-puzzles");
            puzzleButton.setMaxWidth(Double.MAX_VALUE);
            form.getChildren().add(puzzleButton);
        }
        if (cancel != null) {
            Button back = Ui.button("Back to game", "text-button", cancel);
            back.setMaxWidth(Double.MAX_VALUE);
            form.getChildren().add(back);
        } else {
            form.getChildren().add(Ui.label("LOCAL PLAY   ·   NO ACCOUNT NEEDED", "small-muted"));
        }
        root.getChildren().addAll(hero, form);
        return root;
    }

    private static String css(javafx.scene.paint.Color c) {
        return String.format(
                "#%02x%02x%02x",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }
}
