package com.pmfb.gonogo.engine.config;

import java.util.Locale;

/** A verified JLPT level declared by a candidate profile. */
public enum JapaneseProficiency {
    UNSPECIFIED(0),
    N5(1),
    N4(2),
    N3(3),
    N2(4),
    N1(5);

    private final int rank;

    JapaneseProficiency(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static JapaneseProficiency fromProfileValue(String rawValue) {
        if (rawValue == null) {
            return UNSPECIFIED;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT).replace("JLPT", "").trim();
        return switch (normalized) {
            case "N5" -> N5;
            case "N4" -> N4;
            case "N3" -> N3;
            case "N2" -> N2;
            case "N1" -> N1;
            default -> UNSPECIFIED;
        };
    }
}
