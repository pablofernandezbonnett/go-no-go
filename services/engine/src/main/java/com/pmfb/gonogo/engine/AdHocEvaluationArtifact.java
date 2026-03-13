package com.pmfb.gonogo.engine;

record AdHocEvaluationArtifact(
        String personaId,
        String candidateProfileId,
        Source source
) {
    record Source(
            String kind,
            String url,
            String rawText
    ) {
    }
}
