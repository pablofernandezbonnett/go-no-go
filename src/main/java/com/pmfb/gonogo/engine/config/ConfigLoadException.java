package com.pmfb.gonogo.engine.config;

import java.util.List;

public final class ConfigLoadException extends RuntimeException {
    private final List<String> errors;

    public ConfigLoadException(List<String> errors) {
        super("Configuration loading failed.");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
