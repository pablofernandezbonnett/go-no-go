package com.pmfb.gonogo.engine.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record CandidateProfileIndex(
        Set<String> productionSkillIds,
        Set<String> learningSkillIds,
        Set<String> gapSkillIds,
        Set<String> strongDomainIds,
        Set<String> moderateDomainIds,
        Set<String> limitedDomainIds,
        String narrativeTextNormalized
) {
    public CandidateProfileIndex {
        productionSkillIds = productionSkillIds == null ? Set.of() : Set.copyOf(productionSkillIds);
        learningSkillIds = learningSkillIds == null ? Set.of() : Set.copyOf(learningSkillIds);
        gapSkillIds = gapSkillIds == null ? Set.of() : Set.copyOf(gapSkillIds);
        strongDomainIds = strongDomainIds == null ? Set.of() : Set.copyOf(strongDomainIds);
        moderateDomainIds = moderateDomainIds == null ? Set.of() : Set.copyOf(moderateDomainIds);
        limitedDomainIds = limitedDomainIds == null ? Set.of() : Set.copyOf(limitedDomainIds);
        narrativeTextNormalized = narrativeTextNormalized == null ? "" : narrativeTextNormalized;
    }

    public static CandidateProfileIndex from(
            List<String> productionSkills,
            List<String> learningSkills,
            List<String> gapSkills,
            List<ProfileDomain> strongDomains,
            List<ProfileDomain> moderateDomains,
            List<ProfileDomain> limitedDomains,
            List<EducationItem> education,
            List<String> targetRoleHints,
            List<String> differentiators
    ) {
        return new CandidateProfileIndex(
                CandidateProfileTaxonomy.canonicalizeSkillIds(productionSkills),
                CandidateProfileTaxonomy.canonicalizeSkillIds(learningSkills),
                CandidateProfileTaxonomy.canonicalizeSkillIds(gapSkills),
                CandidateProfileTaxonomy.normalizeDomainIds(strongDomains),
                CandidateProfileTaxonomy.normalizeDomainIds(moderateDomains),
                CandidateProfileTaxonomy.normalizeDomainIds(limitedDomains),
                normalizeNarrative(education, targetRoleHints, differentiators)
        );
    }

    private static String normalizeNarrative(
            List<EducationItem> education,
            List<String> targetRoleHints,
            List<String> differentiators
    ) {
        List<String> parts = new ArrayList<>();
        if (education != null) {
            for (EducationItem item : education) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                addIfPresent(parts, item.degree());
                addIfPresent(parts, item.institution());
                addIfPresent(parts, item.note());
            }
        }
        if (targetRoleHints != null) {
            for (String value : targetRoleHints) {
                addIfPresent(parts, value);
            }
        }
        if (differentiators != null) {
            for (String value : differentiators) {
                addIfPresent(parts, value);
            }
        }
        return String.join("\n", parts).toLowerCase(Locale.ROOT);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null) {
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                parts.add(trimmed);
            }
        }
    }
}
