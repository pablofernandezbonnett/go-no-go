package com.pmfb.gonogo.engine.config;

import java.util.Locale;

public enum ProfileDomainStrength {
    STRONG,
    MODERATE,
    LIMITED;

    public String serialized() {
        return name().toLowerCase(Locale.ROOT);
    }
}
