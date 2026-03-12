package com.pmfb.gonogo.engine.config;

public record RuntimeSettingsConfig(
        FetchWebRuntimeConfig fetchWeb,
        EvaluationRuntimeConfig evaluation
) {
    public static RuntimeSettingsConfig defaults() {
        return new RuntimeSettingsConfig(
                FetchWebRuntimeConfig.defaults(),
                EvaluationRuntimeConfig.defaults()
        );
    }

    public RuntimeSettingsConfig {
        fetchWeb = fetchWeb == null ? FetchWebRuntimeConfig.defaults() : fetchWeb;
        evaluation = evaluation == null ? EvaluationRuntimeConfig.defaults() : evaluation;
    }
}
