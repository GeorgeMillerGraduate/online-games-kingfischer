// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.puzzles;

import java.util.*;

/** A blank theme means any theme. Rating bounds are inclusive. */
public record PuzzleFilter(String theme, int minimum, int maximum) {
    public PuzzleFilter {
        theme = theme == null ? "" : theme;
        if (minimum < 0 || maximum > 10000 || minimum > maximum)
            throw new IllegalArgumentException("Use a rating range between 0 and 10000, minimum first.");
    }
    private static final Map<String,String> GROUPS = new LinkedHashMap<>();
    static {
        group("Game phase", "opening middlegame endgame");
        group("Endgames", "pawnEndgame knightEndgame bishopEndgame rookEndgame queenEndgame queenRookEndgame");
        group("Tactical motifs", "advancedPawn attackingF2F7 capturingDefender discoveredAttack doubleCheck exposedKing fork hangingPiece kingsideAttack pin queensideAttack sacrifice skewer trappedPiece");
        group("Advanced tactics", "attraction clearance collinearMove discoveredCheck defensiveMove deflection interference intermezzo quietMove xRayAttack zugzwang");
        group("Checkmates", "mate mateIn1 mateIn2 mateIn3 mateIn4 mateIn5");
        group("Mate patterns", "anastasiaMate arabianMate backRankMate balestraMate blindSwineMate bodenMate cornerMate doubleBishopMate dovetailMate epauletteMate hookMate killBoxMate pillsburysMate morphysMate operaMate swallowstailMate triangleMate vukovicMate smotheredMate");
        group("Special moves", "castling enPassant promotion underPromotion");
        group("Goals", "equality advantage crushing");
        group("Puzzle length", "oneMove short long veryLong");
        group("Game origin", "master masterVsMaster superGM");
    }
    private static void group(String group, String tags) {
        for (String tag : tags.split(" ")) GROUPS.put(tag, group);
    }
    public static String category(String tag) { return GROUPS.getOrDefault(tag, "Other themes"); }
    public static String label(String tag) {
        return switch (tag) {
            case "" -> "All themes / Random mix";
            case "mate" -> "Checkmate";
            case "mateIn1" -> "Mate in 1";
            case "mateIn2" -> "Mate in 2";
            case "mateIn3" -> "Mate in 3";
            case "mateIn4" -> "Mate in 4";
            case "mateIn5" -> "Mate in 5 or more";
            case "oneMove" -> "One move";
            case "short" -> "Short (2 moves)";
            case "long" -> "Long (3 moves)";
            case "veryLong" -> "Very long (4+ moves)";
            case "capturingDefender" -> "Capture the defender";
            case "attackingF2F7" -> "Attacking f2 or f7";
            case "xRayAttack" -> "X-Ray attack";
            case "queenRookEndgame" -> "Queen and rook endgame";
            case "superGM" -> "Super GM games";
            case "master" -> "Master games";
            case "masterVsMaster" -> "Master vs Master games";
            case "anastasiaMate" -> "Anastasia's mate";
            case "bodenMate" -> "Boden's mate";
            case "pillsburysMate" -> "Pillsbury's mate";
            case "morphysMate" -> "Morphy's mate";
            case "swallowstailMate" -> "Swallow's tail mate";
            case "vukovicMate" -> "Vuković mate";
            default -> {
                String s = tag.replaceAll("([a-z])([A-Z])", "$1 $2");
                yield Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
            }
        };
    }
}
