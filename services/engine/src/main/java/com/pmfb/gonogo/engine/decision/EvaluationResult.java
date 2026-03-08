package com.pmfb.gonogo.engine.decision;

import java.util.List;

public record EvaluationResult(
        Verdict verdict,
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
    public EvaluationResult {
        hardRejectReasons = List.copyOf(hardRejectReasons);
        positiveSignals = List.copyOf(positiveSignals);
        riskSignals = List.copyOf(riskSignals);
        reasoning = List.copyOf(reasoning);
    }
}
