package com.pmfb.gonogo.engine.config;

import java.util.List;

public record ProfileDomain(
        String id,
        ProfileDomainStrength strength,
        List<String> evidence
) {
    public ProfileDomain {
        id = id == null ? "" : id.trim();
        strength = strength == null ? ProfileDomainStrength.MODERATE : strength;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static ProfileDomain of(String id, ProfileDomainStrength strength) {
        return new ProfileDomain(id, strength, List.of());
    }
}
