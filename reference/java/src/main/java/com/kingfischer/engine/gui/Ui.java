// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class Ui {

    private Ui() {
    }

    public static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    public static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static void style(Scene scene) {
        scene.getStylesheets().add(Ui.class.getResource("chess.css").toExternalForm());
    }
}
