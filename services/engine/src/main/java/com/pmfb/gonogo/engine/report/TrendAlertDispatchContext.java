package com.pmfb.gonogo.engine.report;

import java.nio.file.Path;

public record TrendAlertDispatchContext(
        String personaId,
        String candidateProfileId,
        String generatedAt,
        Path batchJsonPath,
        Path weeklyDigestPath
) {
}
