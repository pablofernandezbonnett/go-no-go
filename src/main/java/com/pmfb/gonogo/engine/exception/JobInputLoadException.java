package com.pmfb.gonogo.engine.exception;

import java.util.List;

public final class JobInputLoadException extends RuntimeException {
    private final List<String> errors;

    public JobInputLoadException(List<String> errors) {
        super("Job input loading failed.");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
