// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.chess;

/**
 * Packed move: from 6 bits, to 6 bits, promotion 3 bits, special flags.
 */
public final class Move {

    public static final int EP = 1 << 15, CASTLE = 1 << 16;

    private Move() {
    }

    public static int of(int from, int to) {
        return from | to << 6;
    }

    public static int from(int m) {
        return m & 63;
    }

    public static int to(int m) {
        return (m >>> 6) & 63;
    }

    public static int promotion(int m) {
        return (m >>> 12) & 7;
    }

    public static String square(int s) {
        return "" + (char) ('a' + (s & 7)) + (char) ('1' + (s >>> 3));
    }

    public static int square(String s) {
        if (s.length() != 2
                || s.charAt(0) < 'a'
                || s.charAt(0) > 'h'
                || s.charAt(1) < '1'
                || s.charAt(1) > '8') {
            throw new IllegalArgumentException("Invalid square: " + s);
        }
        return s.charAt(0) - 'a' + 8 * (s.charAt(1) - '1');
    }

    public static String uci(int m) {
        return m == 0
                ? "0000"
                : square(from(m))
                + square(to(m))
                + (promotion(m) == 0 ? "" : " pnbrqk".charAt(promotion(m)));
    }
}
