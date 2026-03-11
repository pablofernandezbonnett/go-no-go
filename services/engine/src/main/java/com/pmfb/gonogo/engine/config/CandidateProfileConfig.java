package com.pmfb.gonogo.engine.config;

import java.util.List;

public record CandidateProfileConfig(
        String id,
        String name,
        String title,
        String location,
        int totalExperienceYears,
        List<String> productionSkills,
        List<String> learningSkills,
        List<String> gapSkills,
        List<String> strongDomains,
        List<String> moderateDomains,
        List<String> limitedDomains
) {
    public CandidateProfileConfig {
        productionSkills = List.copyOf(productionSkills);
        learningSkills = List.copyOf(learningSkills);
        gapSkills = List.copyOf(gapSkills);
        strongDomains = List.copyOf(strongDomains);
        moderateDomains = List.copyOf(moderateDomains);
        limitedDomains = List.copyOf(limitedDomains);
    }
}
