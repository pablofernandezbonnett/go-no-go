package com.pmfb.gonogo.engine.config;

import java.util.List;

public record PersonaConfig(
        String id,
        String description,
        List<String> priorities,
        List<String> hardNo,
        List<String> acceptableIf
) {
    public PersonaConfig {
        priorities = List.copyOf(priorities);
        hardNo = List.copyOf(hardNo);
        acceptableIf = List.copyOf(acceptableIf);
    }
}
