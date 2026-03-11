package com.pmfb.gonogo.engine.report;

import java.util.List;

public record WeeklyDigestData(
        String sourceReport,
        String sourceGeneratedAt,
        String generatedAt,
        String persona,
        String candidateProfileId,
        List<WeeklyDigestItem> items,
        List<BatchEvaluationError> errors
) {
    public WeeklyDigestData {
        items = List.copyOf(items);
        errors = List.copyOf(errors);
    }
}
