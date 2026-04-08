package com.pmfb.gonogo.engine.decision;

import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.CandidateProfileTaxonomy;
import com.pmfb.gonogo.engine.job.JobInput;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HumanReadingSynthesizer {
    private static final Pattern YEARS_EXPERIENCE_PATTERN =
            Pattern.compile("(?i)(\\d+)\\+?\\s+years?(?: of)?\\s+(?:hands-on )?(?:experience|exp)");
    private static final List<String> STRUCTURED_CONDITIONS_KEYWORDS = List.of(
            "salary",
            "bonus",
            "holiday",
            "holidays",
            "paid leave",
            "annual leave",
            "insurance",
            "allowance",
            "commuting expenses",
            "remote work allowance",
            "flex-time",
            "flextime",
            "social insurance",
            "pension",
            "福利厚生",
            "休日休暇",
            "勤務時間",
            "勤務地",
            "勤務形態",
            "給与"
    );
    private static final Map<String, List<String>> AFFINITY_THEMES = Map.of(
            "gaming", List.of("game", "games", "gaming", "entertainment", "player", "unity", "unreal"),
            "commerce", List.of("retail", "commerce", "ecommerce", "e-commerce", "checkout", "payment", "fashion"),
            "mobile", List.of("mobile", "ios", "android", "smartphone", "app"),
            "platform", List.of("platform", "performance", "reliability", "scalability")
    );
    private static final List<String> SENIOR_ROLE_KEYWORDS = List.of(
            "senior",
            "staff",
            "lead",
            "principal",
            "architect"
    );

    HumanReading synthesize(
            JobInput job,
            CandidateProfileConfig candidateProfile,
            Verdict verdict,
            int languageFrictionIndex,
            int companyReputationIndex,
            List<String> hardRejectReasons,
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        String combinedText = normalize(job.title() + "\n" + job.description() + "\n" + job.companyName());
        boolean adjacentDomainAffinity = hasAdjacentDomainAffinity(combinedText, candidateProfile);
        boolean structuredConditions = hasStructuredConditionsEvidence(combinedText);

        HumanReadingLevel accessFit = deriveAccessFit(languageFrictionIndex, positiveSignals, riskSignals);
        HumanReadingLevel executionFit = deriveExecutionFit(positiveSignals, riskSignals);
        HumanReadingLevel domainFit = deriveDomainFit(positiveSignals, riskSignals, adjacentDomainAffinity);
        HumanReadingLevel opportunityQuality = deriveOpportunityQuality(
                hardRejectReasons,
                companyReputationIndex,
                positiveSignals,
                riskSignals,
                structuredConditions
        );
        HumanReadingLevel interviewRoi = deriveInterviewRoi(
                hardRejectReasons,
                accessFit,
                executionFit,
                opportunityQuality
        );

        List<String> whyStillInteresting = buildWhyStillInteresting(
                job,
                candidateProfile,
                combinedText,
                positiveSignals,
                accessFit,
                executionFit,
                domainFit,
                opportunityQuality,
                adjacentDomainAffinity,
                structuredConditions
        );
        List<String> whyWasteOfTime = buildWhyWasteOfTime(
                verdict,
                hardRejectReasons,
                riskSignals,
                accessFit,
                executionFit,
                opportunityQuality
        );
        String summary = buildSummary(interviewRoi, whyStillInteresting, whyWasteOfTime);

        return new HumanReading(
                accessFit,
                executionFit,
                domainFit,
                opportunityQuality,
                interviewRoi,
                summary,
                whyStillInteresting,
                whyWasteOfTime
        );
    }

    private HumanReadingLevel deriveAccessFit(
            int languageFrictionIndex,
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        if (riskSignals.contains(SignalIds.LANGUAGE_FRICTION_CRITICAL) || languageFrictionIndex >= 85) {
            return HumanReadingLevel.WEAK;
        }
        if (riskSignals.contains(SignalIds.JAPANESE_ASSIGNMENT_DEPENDENCY)
                || riskSignals.contains(SignalIds.LANGUAGE_FRICTION)
                || languageFrictionIndex >= 20) {
            return HumanReadingLevel.MIXED;
        }
        if (positiveSignals.contains(SignalIds.ENGLISH_ENVIRONMENT)
                || positiveSignals.contains(SignalIds.GLOBAL_TEAM_COLLABORATION)
                || positiveSignals.contains(SignalIds.ENGLISH_SUPPORT_ENVIRONMENT)) {
            return HumanReadingLevel.STRONG;
        }
        return HumanReadingLevel.MIXED;
    }

    private HumanReadingLevel deriveExecutionFit(
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        boolean strongFit = positiveSignals.contains(SignalIds.CANDIDATE_STACK_FIT)
                && positiveSignals.contains(SignalIds.CANDIDATE_SENIORITY_FIT)
                && !riskSignals.contains(SignalIds.CANDIDATE_STACK_GAP)
                && !riskSignals.contains(SignalIds.CANDIDATE_SENIORITY_MISMATCH);
        if (strongFit) {
            return HumanReadingLevel.STRONG;
        }

        boolean anyPositiveFit = positiveSignals.contains(SignalIds.CANDIDATE_STACK_FIT)
                || positiveSignals.contains(SignalIds.CANDIDATE_SENIORITY_FIT);
        boolean blockingGap = riskSignals.contains(SignalIds.CANDIDATE_STACK_GAP)
                || riskSignals.contains(SignalIds.CANDIDATE_SENIORITY_MISMATCH);
        if (blockingGap && !anyPositiveFit) {
            return HumanReadingLevel.WEAK;
        }
        return HumanReadingLevel.MIXED;
    }

    private HumanReadingLevel deriveDomainFit(
            Set<String> positiveSignals,
            Set<String> riskSignals,
            boolean adjacentDomainAffinity
    ) {
        if (positiveSignals.contains(SignalIds.CANDIDATE_DOMAIN_FIT)) {
            return HumanReadingLevel.STRONG;
        }
        if (adjacentDomainAffinity) {
            return HumanReadingLevel.MIXED;
        }
        if (riskSignals.contains(SignalIds.CANDIDATE_DOMAIN_GAP)) {
            return HumanReadingLevel.WEAK;
        }
        return HumanReadingLevel.MIXED;
    }

    private HumanReadingLevel deriveOpportunityQuality(
            List<String> hardRejectReasons,
            int companyReputationIndex,
            Set<String> positiveSignals,
            Set<String> riskSignals,
            boolean structuredConditions
    ) {
        int positiveCount = 0;
        if (positiveSignals.contains(SignalIds.SALARY_TRANSPARENCY)) {
            positiveCount++;
        }
        if (positiveSignals.contains(SignalIds.REMOTE_FRIENDLY) || positiveSignals.contains(SignalIds.HYBRID_WORK)) {
            positiveCount++;
        }
        if (positiveSignals.contains(SignalIds.WORK_LIFE_BALANCE)
                || positiveSignals.contains(SignalIds.REAL_FLEXTIME)
                || positiveSignals.contains(SignalIds.LOW_OVERTIME_DISCLOSED)) {
            positiveCount++;
        }
        if (positiveSignals.contains(SignalIds.STABILITY)
                || positiveSignals.contains(SignalIds.COMPANY_REPUTATION_POSITIVE)
                || companyReputationIndex >= 65) {
            positiveCount++;
        }

        boolean lowTrustPost = riskSignals.contains(SignalIds.ANONYMOUS_EMPLOYER_RISK)
                || riskSignals.contains(SignalIds.GENERIC_MARKETING_POST_RISK)
                || riskSignals.contains(SignalIds.VAGUE_CONDITIONS_RISK);
        boolean salaryOpaque = hardRejectReasons.contains("salary information is missing or non-transparent")
                || riskSignals.contains(SignalIds.SALARY_LOW_CONFIDENCE);
        boolean qualityRisk = riskSignals.contains(SignalIds.CONSULTING_RISK)
                || riskSignals.contains(SignalIds.COMPANY_REPUTATION_RISK_HIGH)
                || riskSignals.contains(SignalIds.WORKLOAD_POLICY_RISK)
                || riskSignals.contains(SignalIds.OVERTIME_RISK);

        if (lowTrustPost) {
            return HumanReadingLevel.WEAK;
        }
        if (!salaryOpaque && positiveCount >= 3 && !qualityRisk) {
            return HumanReadingLevel.STRONG;
        }
        if (salaryOpaque && structuredConditions && positiveCount >= 1 && !lowTrustPost) {
            return HumanReadingLevel.MIXED;
        }
        if (qualityRisk && positiveCount == 0) {
            return HumanReadingLevel.WEAK;
        }
        return HumanReadingLevel.MIXED;
    }

    private HumanReadingLevel deriveInterviewRoi(
            List<String> hardRejectReasons,
            HumanReadingLevel accessFit,
            HumanReadingLevel executionFit,
            HumanReadingLevel opportunityQuality
    ) {
        if (!hardRejectReasons.isEmpty()) {
            return HumanReadingLevel.WEAK;
        }
        if (accessFit == HumanReadingLevel.WEAK || opportunityQuality == HumanReadingLevel.WEAK) {
            return HumanReadingLevel.WEAK;
        }
        if (accessFit == HumanReadingLevel.STRONG
                && executionFit == HumanReadingLevel.STRONG
                && opportunityQuality == HumanReadingLevel.STRONG) {
            return HumanReadingLevel.STRONG;
        }
        return HumanReadingLevel.MIXED;
    }

    private List<String> buildWhyStillInteresting(
            JobInput job,
            CandidateProfileConfig candidateProfile,
            String combinedText,
            Set<String> positiveSignals,
            HumanReadingLevel accessFit,
            HumanReadingLevel executionFit,
            HumanReadingLevel domainFit,
            HumanReadingLevel opportunityQuality,
            boolean adjacentDomainAffinity,
            boolean structuredConditions
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        String stackFitReason = buildStackFitReason(combinedText, candidateProfile, positiveSignals);
        if (!stackFitReason.isBlank()) {
            reasons.add(stackFitReason);
        }
        String seniorityFitReason = buildSeniorityFitReason(job, combinedText, candidateProfile, positiveSignals);
        if (!seniorityFitReason.isBlank()) {
            reasons.add(seniorityFitReason);
        }
        String domainFitReason = buildDomainFitReason(combinedText, candidateProfile, positiveSignals);
        if (!domainFitReason.isBlank()) {
            reasons.add(domainFitReason);
        } else if (domainFit == HumanReadingLevel.MIXED && adjacentDomainAffinity) {
            reasons.add("There is adjacent domain potential from your background and education.");
        } else if (executionFit == HumanReadingLevel.STRONG) {
            reasons.add("The execution fit looks strong for your current senior backend profile.");
        }
        if (positiveSignals.contains(SignalIds.ENGLISH_ENVIRONMENT)
                || accessFit == HumanReadingLevel.STRONG) {
            reasons.add("English collaboration evidence is present in the post.");
        }
        if (positiveSignals.contains(SignalIds.REMOTE_FRIENDLY)
                || positiveSignals.contains(SignalIds.HYBRID_WORK)) {
            reasons.add("The work policy looks compatible with your preferences.");
        }
        if (structuredConditions || opportunityQuality == HumanReadingLevel.STRONG) {
            reasons.add("The post includes concrete working-condition details instead of pure marketing.");
        }
        return limit(reasons, 3);
    }

    private List<String> buildWhyWasteOfTime(
            Verdict verdict,
            List<String> hardRejectReasons,
            Set<String> riskSignals,
            HumanReadingLevel accessFit,
            HumanReadingLevel executionFit,
            HumanReadingLevel opportunityQuality
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        boolean salaryOpaque = hardRejectReasons.contains("salary information is missing or non-transparent")
                || riskSignals.contains(SignalIds.SALARY_LOW_CONFIDENCE);
        if (salaryOpaque) {
            reasons.add("Salary is opaque, so interview ROI is hard to justify under this persona.");
        }
        if (riskSignals.contains(SignalIds.LANGUAGE_FRICTION_CRITICAL)
                || accessFit == HumanReadingLevel.WEAK) {
            reasons.add("Japanese looks like a real gate for getting through the process.");
        } else if (riskSignals.contains(SignalIds.JAPANESE_ASSIGNMENT_DEPENDENCY)) {
            reasons.add("Team placement may still depend on Japanese ability.");
        }
        if (riskSignals.contains(SignalIds.CONSULTING_RISK)) {
            reasons.add("It still looks intermediary-driven rather than a direct product opportunity.");
        }
        if (executionFit == HumanReadingLevel.WEAK) {
            reasons.add("The implementation expectations look materially outside your current shipped fit.");
        }
        if (opportunityQuality == HumanReadingLevel.WEAK) {
            reasons.add("The posting quality looks too weak or too vague to invest process energy.");
        }
        if (verdict == Verdict.NO_GO && reasons.isEmpty()) {
            reasons.add("This persona would still screen it out before it becomes interview-worthy.");
        }
        return limit(reasons, 3);
    }

    private String buildSummary(
            HumanReadingLevel interviewRoi,
            List<String> whyStillInteresting,
            List<String> whyWasteOfTime
    ) {
        String firstUpside = whyStillInteresting.isEmpty() ? "" : whyStillInteresting.get(0);
        String firstDownside = whyWasteOfTime.isEmpty() ? "" : whyWasteOfTime.get(0);

        return switch (interviewRoi) {
            case STRONG -> firstUpside.isBlank()
                    ? "Worth serious consideration."
                    : "Worth serious consideration: " + firstUpside;
            case MIXED -> {
                if (!firstUpside.isBlank() && !firstDownside.isBlank()) {
                    yield "Mixed call: " + firstUpside + " Main caution: " + firstDownside;
                }
                if (!firstUpside.isBlank()) {
                    yield "Mixed call: " + firstUpside;
                }
                if (!firstDownside.isBlank()) {
                    yield "Mixed call: " + firstDownside;
                }
                yield "Mixed call overall.";
            }
            case WEAK -> {
                if (!firstDownside.isBlank() && !firstUpside.isBlank()) {
                    yield "Likely low interview ROI: " + firstDownside + " Main upside: " + firstUpside;
                }
                if (!firstDownside.isBlank()) {
                    yield "Likely low interview ROI: " + firstDownside;
                }
                yield "Likely low interview ROI.";
            }
        };
    }

    private String buildStackFitReason(
            String combinedText,
            CandidateProfileConfig candidateProfile,
            Set<String> positiveSignals
    ) {
        if (candidateProfile == null || !positiveSignals.contains(SignalIds.CANDIDATE_STACK_FIT)) {
            return "";
        }
        List<String> matchedSkills = collectMatchedSkillLabels(combinedText, candidateProfile);
        if (matchedSkills.isEmpty()) {
            return "The shipped stack overlap looks direct enough for this role.";
        }
        return "There is direct stack overlap on " + String.join(", ", matchedSkills) + ".";
    }

    private String buildDomainFitReason(
            String combinedText,
            CandidateProfileConfig candidateProfile,
            Set<String> positiveSignals
    ) {
        if (candidateProfile == null || !positiveSignals.contains(SignalIds.CANDIDATE_DOMAIN_FIT)) {
            return "";
        }
        List<String> matchedDomains = collectMatchedDomainLabels(combinedText, candidateProfile);
        if (matchedDomains.isEmpty()) {
            return "The domain lines up directly with your recent experience.";
        }
        return "The post maps well to your background in " + String.join(", ", matchedDomains) + ".";
    }

    private String buildSeniorityFitReason(
            JobInput job,
            String combinedText,
            CandidateProfileConfig candidateProfile,
            Set<String> positiveSignals
    ) {
        if (candidateProfile == null || !positiveSignals.contains(SignalIds.CANDIDATE_SENIORITY_FIT)) {
            return "";
        }
        int requiredYears = extractRequiredYears(combinedText);
        if (requiredYears > 0) {
            return "The seniority bar looks fine: the role asks for "
                    + requiredYears + "+ years and your profile shows "
                    + candidateProfile.totalExperienceYears() + " years.";
        }
        if (isSeniorRoleTitle(job.title())) {
            return "The role is explicitly senior-level and your profile already sits above that bar.";
        }
        return "The seniority level looks compatible with your profile.";
    }

    private boolean hasStructuredConditionsEvidence(String combinedText) {
        return containsAny(combinedText, STRUCTURED_CONDITIONS_KEYWORDS);
    }

    private boolean hasAdjacentDomainAffinity(String combinedText, CandidateProfileConfig candidateProfile) {
        if (candidateProfile == null) {
            return false;
        }
        if (hasGenericShapeAffinity(combinedText, candidateProfile)) {
            return true;
        }
        String profileNarratives = candidateProfile.index().narrativeTextNormalized();
        for (List<String> themeKeywords : AFFINITY_THEMES.values()) {
            if (containsAny(profileNarratives, themeKeywords) && containsAny(combinedText, themeKeywords)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGenericShapeAffinity(String combinedText, CandidateProfileConfig candidateProfile) {
        Set<String> strongGenericIds = CandidateProfileTaxonomy.genericShapeDomainIds(
                candidateProfile.index().strongDomainIds()
        );
        if (countDomainMatches(combinedText, strongGenericIds) >= 1) {
            return true;
        }
        Set<String> moderateGenericIds = CandidateProfileTaxonomy.genericShapeDomainIds(
                candidateProfile.index().moderateDomainIds()
        );
        return countDomainMatches(combinedText, moderateGenericIds) >= 2;
    }

    private int countDomainMatches(String combinedText, Set<String> domainIds) {
        int matches = 0;
        for (String domainId : domainIds) {
            List<String> aliases = CandidateProfileTaxonomy.domainAliases(domainId);
            if (aliases != null && containsAny(combinedText, aliases)) {
                matches++;
            }
        }
        return matches;
    }

    private List<String> collectMatchedSkillLabels(String combinedText, CandidateProfileConfig candidateProfile) {
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String skillId : candidateProfile.index().productionSkillIds()) {
            List<String> aliases = CandidateProfileTaxonomy.skillAliases(skillId);
            if (containsAny(combinedText, aliases)) {
                matched.add(formatSkillLabel(skillId));
            }
        }
        return limitLabels(matched, 3);
    }

    private List<String> collectMatchedDomainLabels(String combinedText, CandidateProfileConfig candidateProfile) {
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String domainId : candidateProfile.index().strongDirectDomainIds()) {
            List<String> aliases = CandidateProfileTaxonomy.domainAliases(domainId);
            if (aliases != null && containsAny(combinedText, aliases)) {
                matched.add(formatDomainLabel(domainId));
            }
        }
        for (String domainId : candidateProfile.index().moderateDirectDomainIds()) {
            List<String> aliases = CandidateProfileTaxonomy.domainAliases(domainId);
            if (aliases != null && containsAny(combinedText, aliases)) {
                matched.add(formatDomainLabel(domainId));
            }
        }
        return limitLabels(matched, 3);
    }

    private List<String> limitLabels(Set<String> labels, int maxItems) {
        List<String> limited = new ArrayList<>(maxItems);
        for (String label : labels) {
            limited.add(label);
            if (limited.size() >= maxItems) {
                break;
            }
        }
        return List.copyOf(limited);
    }

    private int extractRequiredYears(String combinedText) {
        Matcher matcher = YEARS_EXPERIENCE_PATTERN.matcher(combinedText);
        int maxYears = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > maxYears) {
                maxYears = value;
            }
        }
        return maxYears;
    }

    private boolean isSeniorRoleTitle(String title) {
        String normalizedTitle = normalize(title);
        for (String keyword : SENIOR_ROLE_KEYWORDS) {
            if (normalizedTitle.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String formatSkillLabel(String skillId) {
        return switch (skillId) {
            case "aws" -> "AWS";
            case "gcp" -> "GCP";
            case "spring_boot" -> "Spring Boot";
            case "spring" -> "Spring";
            case "rest_api" -> "REST APIs";
            case "sap_hybris" -> "SAP Commerce / Hybris";
            case "sql" -> "SQL / PostgreSQL";
            case "react" -> "React";
            case "typescript" -> "TypeScript";
            case "mongodb" -> "MongoDB";
            default -> skillId.replace('_', ' ').toUpperCase(Locale.ROOT).charAt(0)
                    + skillId.replace('_', ' ').substring(1);
        };
    }

    private String formatDomainLabel(String domainId) {
        return switch (domainId) {
            case "ecommerce_platforms" -> "e-commerce / commerce platforms";
            case "payment_integrations" -> "payment integrations";
            case "payment_adjacent_flows" -> "payment-adjacent flows";
            case "product_platforms" -> "product platforms";
            case "distributed_product_systems" -> "distributed product systems";
            case "international_teams" -> "international teams";
            case "frontend_delivery", "frontend_fullstack" -> "frontend delivery";
            case "cloud_environments_basics", "cloud_basics" -> "cloud environments";
            case "system_design" -> "system design";
            case "event_driven_patterns", "event_driven_architecture" -> "event-driven patterns";
            default -> domainId.replace('_', ' ');
        };
    }

    private List<String> limit(Set<String> reasons, int maxItems) {
        List<String> limited = new ArrayList<>(maxItems);
        for (String reason : reasons) {
            limited.add(reason);
            if (limited.size() >= maxItems) {
                break;
            }
        }
        return List.copyOf(limited);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
