package com.pmfb.gonogo.engine.report;

import java.util.List;

public record RunTrendSnapshot(
        String generatedAt,
        String persona,
        int evaluated,
        int goCount,
        int goWithCautionCount,
        int noGoCount,
        int newCount,
        int updatedCount,
        int unchangedCount,
        int removedCount,
        double averageScore,
        double averageLanguageFrictionIndex,
        double averageCompanyReputationIndex,
        List<RunTrendCompanyStats> companies
) {
    public RunTrendSnapshot {
        companies = List.copyOf(companies);
    }
}
