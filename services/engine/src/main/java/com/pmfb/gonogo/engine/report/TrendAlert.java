package com.pmfb.gonogo.engine.report;

public record TrendAlert(
        String severity,
        String code,
        String message
) {
}
