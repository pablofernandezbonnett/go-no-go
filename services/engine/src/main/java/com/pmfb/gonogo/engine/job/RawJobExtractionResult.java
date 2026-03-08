package com.pmfb.gonogo.engine.job;

import java.util.List;

public record RawJobExtractionResult(
        JobInput jobInput,
        List<String> warnings
) {
    public RawJobExtractionResult {
        warnings = List.copyOf(warnings);
    }
}
