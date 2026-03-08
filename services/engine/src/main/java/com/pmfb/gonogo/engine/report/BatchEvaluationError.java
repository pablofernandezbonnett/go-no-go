package com.pmfb.gonogo.engine.report;

import java.util.List;

public record BatchEvaluationError(
        String sourceFile,
        List<String> errors
) {
    public BatchEvaluationError {
        errors = List.copyOf(errors);
    }
}
