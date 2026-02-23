package com.pmfb.gonogo.engine.config;

import java.util.List;

public record CompanyConfig(
        String id,
        String name,
        String careerUrl,
        String typeHint,
        String region,
        String notes,
        List<String> profileTags,
        List<String> riskTags
) {
    public CompanyConfig(
            String id,
            String name,
            String careerUrl,
            String typeHint,
            String region,
            String notes
    ) {
        this(id, name, careerUrl, typeHint, region, notes, List.of(), List.of());
    }

    public CompanyConfig {
        profileTags = profileTags == null ? List.of() : List.copyOf(profileTags);
        riskTags = riskTags == null ? List.of() : List.copyOf(riskTags);
    }
}
