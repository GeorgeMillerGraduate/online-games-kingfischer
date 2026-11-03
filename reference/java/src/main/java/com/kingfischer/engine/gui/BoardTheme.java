// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import javafx.scene.paint.Color;

public enum BoardTheme {
    SAGE("Sage", "#e9eddf", "#77957c"),
    WALNUT("Walnut", "#eddbc0", "#ac805e"),
    MIDNIGHT("Midnight", "#c9d3df", "#61738e");
    public final String title;
    public final Color light, dark;

    BoardTheme(String title, String light, String dark) {
        this.title = title;
        this.light = Color.web(light);
        this.dark = Color.web(dark);
    }

    @Override
    public String toString() {
        return title;
    }
}
