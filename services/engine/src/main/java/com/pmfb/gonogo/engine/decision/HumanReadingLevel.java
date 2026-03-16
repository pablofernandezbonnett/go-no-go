package com.pmfb.gonogo.engine.decision;

import java.util.Locale;

public enum HumanReadingLevel {
    STRONG,
    MIXED,
    WEAK;

    public String serialized() {
        return name().toLowerCase(Locale.ROOT);
    }
}
