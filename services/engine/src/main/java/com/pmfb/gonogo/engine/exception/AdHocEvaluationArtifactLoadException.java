package com.pmfb.gonogo.engine.exception;

import java.util.List;

public final class AdHocEvaluationArtifactLoadException extends RuntimeException {
    private final List<String> errors;

    public AdHocEvaluationArtifactLoadException(List<String> errors) {
        super("Ad-hoc evaluation artifact loading failed.");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
