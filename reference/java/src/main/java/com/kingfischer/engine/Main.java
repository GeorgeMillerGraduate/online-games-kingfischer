// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine;

import com.kingfischer.engine.gui.ChessWindow;
import com.kingfischer.engine.uci.UciEngine;
import java.util.Arrays;

/**
 * Desktop by default; --uci preserves text input/output for chess frontends.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--gui")) {
            ChessWindow.launchGui();
            return;
        }
        int first = args[0].equals("--uci") ? 1 : 0;
        try (UciEngine uci = new UciEngine(System.out)) {
            if (first == args.length) {
                uci.loop(System.in);
            } else {
                uci.command(String.join(" ", Arrays.copyOfRange(args, first, args.length)));
                uci.await();
            }
        }
    }
}
