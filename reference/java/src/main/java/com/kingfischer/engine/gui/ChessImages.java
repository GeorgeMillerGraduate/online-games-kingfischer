// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;

public final class ChessImages {

    private final Map<Integer, Image> cache = new HashMap<>();

    public Image piece(int code) {
        if (cache.containsKey(code)) {
            return cache.get(code);
        }

        int type = code & 7;
        if (type < 1 || type > 6) {
            return null;
        }

        boolean white = code < 8;

        String[] names = {
            "", "pawn", "knight", "bishop", "rook", "queen", "king"
        };

        String[] originalNames = {
            "", "Pawn", "Knight", "Bish", "Rook", "Queen", "King"
        };

        String modernName
                = (white ? "white_" : "black_") + names[type] + ".png";

        String originalName
                = originalNames[type] + (white ? "W" : "B") + ".png";

        String[] folders = {
            "/com/kingfischer/engine/pieces/",
            "/images/"
        };

        String[] filenames = {
            modernName,
            originalName,
            type == 4 && !white ? "rookB.png" : originalName
        };

        for (String folder : folders) {
            for (String filename : filenames) {
                try (InputStream input
                        = ChessImages.class.getResourceAsStream(folder + filename)) {

                    if (input == null) {
                        continue;
                    }

                    Image image = new Image(input);

                    if (!image.isError()) {
                        cache.put(code, image);
                        return image;
                    }
                } catch (IOException ignored) {
                    // Try the next filename or resource folder.
                }
            }
        }

        System.err.println("Missing chess piece PNG: " + modernName);
        cache.put(code, null);
        return null;
    }
}
