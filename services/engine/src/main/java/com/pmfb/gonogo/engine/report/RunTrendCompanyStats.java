package com.pmfb.gonogo.engine.report;

public record RunTrendCompanyStats(
        String company,
        int jobs,
        double averageScore,
        double averageLanguageFrictionIndex,
        double averageCompanyReputationIndex,
        int goCount,
        int goWithCautionCount,
        int noGoCount
) {
}
