// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.uci;

import com.kingfischer.engine.engine.Engine;
import com.kingfischer.engine.search.SearchLimits;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * UCI protocol shell. Standard output contains protocol responses only.
 */
public final class UciEngine implements AutoCloseable {

    private final PrintWriter out;
    private final Engine engine;

    public UciEngine(OutputStream out) {
        this.out = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        engine = new Engine(this::line);
    }

    private synchronized void line(String s) {
        out.println(s);
    }

    public boolean command(String input) {
        String s = input.trim();
        if (s.isEmpty()) {
            return true;
        }
        String[] a = s.split("\\s+", 2);
        try {
            switch (a[0]) {
                case "uci" ->
                    engine.identify();
                case "isready" ->
                    line("readyok");
                case "debug" -> {
                }
                case "setoption" -> {
                    if (a.length < 2 || !a[1].startsWith("name ")) {
                        throw new IllegalArgumentException("Expected setoption name");
                    }
                    String rest = a[1].substring(5);
                    int split = rest.indexOf(" value ");
                    engine.setOption(
                            split < 0 ? rest.trim() : rest.substring(0, split).trim(),
                            split < 0 ? "" : rest.substring(split + 7).trim());
                }
                case "ucinewgame" ->
                    engine.newGame();
                case "position" ->
                    engine.setPosition(s);
                case "go" -> {
                    if (a.length > 1 && a[1].startsWith("perft ")) {
                        engine.perft(Integer.parseInt(a[1].substring(6).trim()), true);
                    } else {
                        engine.go(SearchLimits.parse(s));
                    }
                }
                case "stop" ->
                    engine.stop();
                case "ponderhit" ->
                    engine.ponderHit();
                case "quit" -> {
                    engine.close();
                    return false;
                }
                case "eval" ->
                    engine.evaluate();
                case "d" ->
                    line("Fen: " + engine.position().fen());
                case "perft", "divide" ->
                    engine.perft(Integer.parseInt(a[1]), a[0].equals("divide"));
                case "bench" ->
                    engine.benchmark(a.length > 1 ? Integer.parseInt(a[1]) : 4);
                default ->
                    line("info string Unknown command: " + a[0]);
            }
        } catch (Exception e) {
            line("info string Error: " + e.getMessage());
        }
        return true;
    }

    public void loop(InputStream input) throws IOException {
        try (BufferedReader reader
                = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String s;
            while ((s = reader.readLine()) != null && command(s)) {
            }
            ;
        } finally {
            engine.close();
        }
    }

    public void await() {
        engine.await();
    }

    public void close() {
        engine.close();
    }
}
