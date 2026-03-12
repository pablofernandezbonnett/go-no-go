package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.job.JobInput;
import java.util.List;

record EvaluateInputAnalysis(
        String generatedAt,
        String personaId,
        String candidateProfileId,
        SourceDetails source,
        JobInput jobInput,
        EvaluationResult evaluation,
        List<String> normalizationWarnings
) {
    EvaluateInputAnalysis {
        normalizationWarnings = List.copyOf(normalizationWarnings);
    }

    record SourceDetails(
            String kind,
            String url,
            String file,
            String rawText
    ) {
    }
}
