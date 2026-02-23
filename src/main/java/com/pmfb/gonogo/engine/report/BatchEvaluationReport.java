package com.pmfb.gonogo.engine.report;

import java.util.List;

public record BatchEvaluationReport(
        String generatedAt,
        String personaId,
        int totalFiles,
        int evaluatedCount,
        int failedCount,
        int goCount,
        int goWithCautionCount,
        int noGoCount,
        int newCount,
        int updatedCount,
        int unchangedCount,
        int removedCount,
        List<BatchEvaluationItem> items,
        List<RemovedJobItem> removedItems,
        List<BatchEvaluationError> errors
) {
    public BatchEvaluationReport(
            String generatedAt,
            String personaId,
            int totalFiles,
            int evaluatedCount,
            int failedCount,
            int goCount,
            int goWithCautionCount,
            int noGoCount,
            List<BatchEvaluationItem> items,
            List<BatchEvaluationError> errors
    ) {
        this(
                generatedAt,
                personaId,
                totalFiles,
                evaluatedCount,
                failedCount,
                goCount,
                goWithCautionCount,
                noGoCount,
                0,
                0,
                evaluatedCount,
                0,
                items,
                List.of(),
                errors
        );
    }

    public BatchEvaluationReport {
        items = List.copyOf(items);
        removedItems = List.copyOf(removedItems);
        errors = List.copyOf(errors);
    }
}
