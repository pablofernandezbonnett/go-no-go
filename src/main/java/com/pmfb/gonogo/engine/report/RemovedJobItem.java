package com.pmfb.gonogo.engine.report;

public record RemovedJobItem(
        String jobKey,
        String sourceFile,
        String company,
        String title,
        String location
) {
}
