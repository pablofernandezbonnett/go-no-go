package com.pmfb.gonogo.engine.report;

import java.util.List;

public record WeeklyDigestItem(
        String sourceFile,
        String company,
        String title,
        String verdict,
        int score,
        int rawScore,
        int rawScoreMin,
        int rawScoreMax,
        int languageFrictionIndex,
        int companyReputationIndex,
        List<String> hardRejectReasons,
        List<String> positiveSignals,
        List<String> riskSignals,
        List<String> reasoning
) {
    public WeeklyDigestItem {
        hardRejectReasons = List.copyOf(hardRejectReasons);
        positiveSignals = List.copyOf(positiveSignals);
        riskSignals = List.copyOf(riskSignals);
        reasoning = List.copyOf(reasoning);
    }
}
