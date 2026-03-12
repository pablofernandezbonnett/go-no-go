package com.pmfb.gonogo.engine.config;

public record EvaluationRuntimeConfig(
        int maxConcurrency
) {
    private static final int DEFAULT_MAX_CONCURRENCY = 4;

    public static EvaluationRuntimeConfig defaults() {
        return new EvaluationRuntimeConfig(DEFAULT_MAX_CONCURRENCY);
    }
}
