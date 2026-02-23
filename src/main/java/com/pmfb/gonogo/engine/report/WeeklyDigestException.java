package com.pmfb.gonogo.engine.report;

import java.util.List;

public final class WeeklyDigestException extends RuntimeException {
    private final List<String> errors;

    public WeeklyDigestException(List<String> errors) {
        super("Weekly digest generation failed.");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
