package com.pmfb.gonogo.engine.config;

import java.util.ArrayList;
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
        List<ProfileDomain> strongDomains,
        List<ProfileDomain> moderateDomains,
        List<ProfileDomain> limitedDomains,
        List<EducationItem> education,
        List<String> targetRoleHints,
        List<String> differentiators,
        CandidateProfileIndex index
) {
    public CandidateProfileConfig {
        id = id == null ? "" : id.trim();
        name = name == null ? "" : name.trim();
        title = title == null ? "" : title.trim();
        location = location == null ? "" : location.trim();
        productionSkills = productionSkills == null ? List.of() : List.copyOf(productionSkills);
        learningSkills = learningSkills == null ? List.of() : List.copyOf(learningSkills);
        gapSkills = gapSkills == null ? List.of() : List.copyOf(gapSkills);
        strongDomains = strongDomains == null ? List.of() : List.copyOf(strongDomains);
        moderateDomains = moderateDomains == null ? List.of() : List.copyOf(moderateDomains);
        limitedDomains = limitedDomains == null ? List.of() : List.copyOf(limitedDomains);
        education = education == null ? List.of() : List.copyOf(education);
        targetRoleHints = targetRoleHints == null ? List.of() : List.copyOf(targetRoleHints);
        differentiators = differentiators == null ? List.of() : List.copyOf(differentiators);
        index = index == null
                ? CandidateProfileIndex.from(
                        productionSkills,
                        learningSkills,
                        gapSkills,
                        strongDomains,
                        moderateDomains,
                        limitedDomains,
                        education,
                        targetRoleHints,
                        differentiators
                )
                : index;
    }

    public CandidateProfileConfig(
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
        this(
                id,
                name,
                title,
                location,
                totalExperienceYears,
                productionSkills,
                learningSkills,
                gapSkills,
                toDomains(strongDomains, ProfileDomainStrength.STRONG),
                toDomains(moderateDomains, ProfileDomainStrength.MODERATE),
                toDomains(limitedDomains, ProfileDomainStrength.LIMITED),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    public CandidateProfileConfig(
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
            List<String> limitedDomains,
            List<String> educationKeywords,
            List<String> targetRoleHints,
            List<String> differentiators
    ) {
        this(
                id,
                name,
                title,
                location,
                totalExperienceYears,
                productionSkills,
                learningSkills,
                gapSkills,
                toDomains(strongDomains, ProfileDomainStrength.STRONG),
                toDomains(moderateDomains, ProfileDomainStrength.MODERATE),
                toDomains(limitedDomains, ProfileDomainStrength.LIMITED),
                toEducationItems(educationKeywords),
                targetRoleHints,
                differentiators,
                null
        );
    }

    public List<String> educationKeywords() {
        List<String> values = new ArrayList<>();
        for (EducationItem item : education) {
            addIfPresent(values, item.degree());
            addIfPresent(values, item.institution());
            addIfPresent(values, item.note());
        }
        return List.copyOf(values);
    }

    private static List<ProfileDomain> toDomains(List<String> domainIds, ProfileDomainStrength strength) {
        if (domainIds == null) {
            return List.of();
        }
        List<ProfileDomain> domains = new ArrayList<>();
        for (String domainId : domainIds) {
            if (domainId == null) {
                continue;
            }
            String trimmed = domainId.trim();
            if (!trimmed.isBlank()) {
                domains.add(ProfileDomain.of(trimmed, strength));
            }
        }
        return List.copyOf(domains);
    }

    private static List<EducationItem> toEducationItems(List<String> educationKeywords) {
        if (educationKeywords == null) {
            return List.of();
        }
        List<EducationItem> education = new ArrayList<>();
        for (String value : educationKeywords) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                education.add(new EducationItem(trimmed, "", ""));
            }
        }
        return List.copyOf(education);
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isBlank()) {
            values.add(trimmed);
        }
    }
}
