// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.search;

import java.util.*;

public final class SearchLimits {

    public int depth = 128, mate, movesToGo;
    public long nodes = Long.MAX_VALUE, moveTime = -1;
    public final long[] time = {-1, -1}, increment = {0, 0};
    public boolean infinite, ponder;
    public final List<String> searchMoves = new ArrayList<>();
    public boolean restrictMoves;
    private static final Set<String> TOKENS
            = Set.of(
                    "depth",
                    "nodes",
                    "movetime",
                    "wtime",
                    "btime",
                    "winc",
                    "binc",
                    "movestogo",
                    "infinite",
                    "ponder",
                    "searchmoves",
                    "mate");

    public static SearchLimits parse(String line) {
        SearchLimits l = new SearchLimits();
        String[] a = line.trim().split("\\s+");
        for (int i = 1; i < a.length; i++) {
            String t = a[i];
            if (t.equals("infinite")) {
                l.infinite = true;
                continue;
            }
            if (t.equals("ponder")) {
                l.ponder = true;
                continue;
            }
            if (t.equals("searchmoves")) {
                l.restrictMoves = true;
                while (i + 1 < a.length && !TOKENS.contains(a[i + 1])) {
                    l.searchMoves.add(a[++i]);
                }
                continue;
            }
            if (!TOKENS.contains(t)) {
                continue;
            }
            if (++i == a.length) {
                throw new IllegalArgumentException("Missing go value");
            }
            long v = Long.parseLong(a[i]);
            if (v < 0 || v > 1_000_000_000_000L) {
                throw new IllegalArgumentException("Invalid go limit");
            }
            switch (t) {
                case "depth" ->
                    l.depth = (int) Math.max(1, Math.min(128, v));
                case "nodes" ->
                    l.nodes = Math.max(1, v);
                case "movetime" ->
                    l.moveTime = v;
                case "wtime" ->
                    l.time[0] = v;
                case "btime" ->
                    l.time[1] = v;
                case "winc" ->
                    l.increment[0] = v;
                case "binc" ->
                    l.increment[1] = v;
                case "movestogo" ->
                    l.movesToGo = (int) Math.min(1000, v);
                case "mate" -> {
                    l.mate = (int) Math.min(64, v);
                    l.depth = Math.max(1, l.mate * 2);
                }
                default -> {
                }
            }
        }
        return l;
    }
}
