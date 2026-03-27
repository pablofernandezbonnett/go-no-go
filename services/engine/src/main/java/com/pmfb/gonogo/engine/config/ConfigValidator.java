package com.pmfb.gonogo.engine.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private static final Set<String> ALLOWED_COMPANY_PROFILE_TAGS = Set.of(
            "expat_friendly",
            "engineering_brand",
            "strong_wlb",
            "stable_public",
            "product_leader",
            "reputation_strong"
    );
    private static final Set<String> ALLOWED_COMPANY_RISK_TAGS = Set.of(
            "language_friction_high",
            "overtime_risk",
            "reputation_risk",
            "layoff_risk"
    );

    public List<String> validate(EngineConfig config) {
        List<String> errors = new ArrayList<>();
        validateCompanies(config.companies(), errors);
        validatePersonas(config.personas(), errors);
        validateBlacklist(config.blacklistedCompanies(), errors);
        validateCandidateProfiles(config.candidateProfiles(), errors);
        validateRuntimeSettings(config.runtimeSettings(), errors);
        validateDecisionSignals(config.decisionSignals(), errors);
        return errors;
    }

    private void validateCompanies(List<CompanyConfig> companies, List<String> errors) {
        if (companies.isEmpty()) {
            errors.add("At least one company is required in config/companies.yaml");
            return;
        }

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < companies.size(); i++) {
            CompanyConfig company = companies.get(i);
            String context = "companies[" + i + "]";

            validateId(company.id(), context + ".id", errors);

            String normalizedId = normalize(company.id());
            if (!normalizedId.isEmpty() && !ids.add(normalizedId)) {
                errors.add("Duplicate company id: " + company.id());
            }

            String normalizedName = normalize(company.name());
            if (!normalizedName.isEmpty() && !names.add(normalizedName)) {
                errors.add("Duplicate company name: " + company.name());
            }

            if (!isValidHttpUrl(company.careerUrl())) {
                errors.add(context + ".career_url must be a valid http/https URL");
            }
            if (!isValidHttpUrl(company.corporateUrl())) {
                errors.add(context + ".corporate_url must be a valid http/https URL");
            }

            checkDuplicates(company.profileTags(), context + ".profile_tags", errors);
            checkDuplicates(company.riskTags(), context + ".risk_tags", errors);
            validateAllowedTags(
                    company.profileTags(),
                    ALLOWED_COMPANY_PROFILE_TAGS,
                    context + ".profile_tags",
                    errors
            );
            validateAllowedTags(
                    company.riskTags(),
                    ALLOWED_COMPANY_RISK_TAGS,
                    context + ".risk_tags",
                    errors
            );
        }
    }

    private void validatePersonas(List<PersonaConfig> personas, List<String> errors) {
        if (personas.isEmpty()) {
            errors.add("At least one persona is required in config/personas.yaml");
            return;
        }

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < personas.size(); i++) {
            PersonaConfig persona = personas.get(i);
            String context = "personas[" + i + "]";

            validateId(persona.id(), context + ".id", errors);

            String normalizedId = normalize(persona.id());
            if (!normalizedId.isEmpty() && !ids.add(normalizedId)) {
                errors.add("Duplicate persona id: " + persona.id());
            }

            if (persona.priorities().isEmpty()) {
                errors.add(context + ".priorities must contain at least one item");
            }
            if (persona.hardNo().isEmpty()) {
                errors.add(context + ".hard_no must contain at least one item");
            }
            if (persona.minimumSalaryYen() < 0) {
                errors.add(context + ".minimum_salary_yen must be non-negative");
            }

            checkDuplicates(persona.priorities(), context + ".priorities", errors);
            checkDuplicates(persona.hardNo(), context + ".hard_no", errors);
            checkDuplicates(persona.acceptableIf(), context + ".acceptable_if", errors);
        }
    }

    private void validateBlacklist(
            List<BlacklistedCompanyConfig> blacklistedCompanies,
            List<String> errors
    ) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < blacklistedCompanies.size(); i++) {
            BlacklistedCompanyConfig item = blacklistedCompanies.get(i);
            String context = "blacklisted_companies[" + i + "]";

            if (item.name().isBlank()) {
                errors.add(context + ".name cannot be blank");
            }
            if (item.reason().isBlank()) {
                errors.add(context + ".reason cannot be blank");
            }

            String normalizedName = normalize(item.name());
            if (!normalizedName.isEmpty() && !names.add(normalizedName)) {
                errors.add("Duplicate blacklisted company name: " + item.name());
            }
        }
    }

    private void validateCandidateProfiles(
            List<CandidateProfileConfig> candidateProfiles,
            List<String> errors
    ) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < candidateProfiles.size(); i++) {
            CandidateProfileConfig profile = candidateProfiles.get(i);
            String context = "candidate_profiles[" + i + "]";

            validateId(profile.id(), context + ".id", errors);

            String normalizedId = normalize(profile.id());
            if (!normalizedId.isEmpty() && !ids.add(normalizedId)) {
                errors.add("Duplicate candidate profile id: " + profile.id());
            }
            if (profile.name().isBlank()) {
                errors.add(context + ".name cannot be blank");
            }
            if (profile.title().isBlank()) {
                errors.add(context + ".title cannot be blank");
            }
            if (profile.totalExperienceYears() < 0) {
                errors.add(context + ".total_experience_years must be non-negative");
            }

            checkDuplicates(profile.productionSkills(), context + ".production_skills", errors);
            checkDuplicates(profile.learningSkills(), context + ".learning_skills", errors);
            checkDuplicates(profile.gapSkills(), context + ".gap_skills", errors);
            checkDuplicateDomainIds(profile.strongDomains(), context + ".strong_domains", errors);
            checkDuplicateDomainIds(profile.moderateDomains(), context + ".moderate_domains", errors);
            checkDuplicateDomainIds(profile.limitedDomains(), context + ".limited_domains", errors);
            validateEducation(profile.education(), context + ".education", errors);
        }
    }

    private void checkDuplicateDomainIds(
            List<ProfileDomain> domains,
            String context,
            List<String> errors
    ) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < domains.size(); i++) {
            ProfileDomain domain = domains.get(i);
            String id = normalize(domain.id());
            if (id.isBlank()) {
                errors.add(context + "[" + i + "].id cannot be blank");
                continue;
            }
            if (!seen.add(id)) {
                errors.add(context + " contains duplicate domain id '" + domain.id() + "'");
            }
        }
    }

    private void validateEducation(
            List<EducationItem> education,
            String context,
            List<String> errors
    ) {
        for (int i = 0; i < education.size(); i++) {
            EducationItem item = education.get(i);
            if (item.isBlank()) {
                errors.add(context + "[" + i + "] cannot be blank");
            }
        }
    }

    private void validateRuntimeSettings(
            RuntimeSettingsConfig runtimeSettings,
            List<String> errors
    ) {
        if (runtimeSettings == null) {
            return;
        }

        FetchWebRuntimeConfig fetchWeb = runtimeSettings.fetchWeb();
        if (fetchWeb == null) {
            errors.add("runtime.fetch_web must be defined");
            return;
        }

        if (fetchWeb.timeoutSeconds() < 5) {
            errors.add("runtime.fetch_web.timeout_seconds must be at least 5");
        }
        if (fetchWeb.userAgent().isBlank()) {
            errors.add("runtime.fetch_web.user_agent cannot be blank");
        }
        if (fetchWeb.retries() < 0) {
            errors.add("runtime.fetch_web.retries must be non-negative");
        }
        if (fetchWeb.backoffMillis() < 0) {
            errors.add("runtime.fetch_web.backoff_millis must be non-negative");
        }
        if (fetchWeb.requestDelayMillis() < 0) {
            errors.add("runtime.fetch_web.request_delay_millis must be non-negative");
        }
        if (fetchWeb.maxConcurrency() < 1) {
            errors.add("runtime.fetch_web.max_concurrency must be at least 1");
        }
        if (fetchWeb.maxConcurrencyPerHost() < 1) {
            errors.add("runtime.fetch_web.max_concurrency_per_host must be at least 1");
        }
        if (!Set.of("strict", "warn", "off").contains(normalize(fetchWeb.robotsMode()))) {
            errors.add("runtime.fetch_web.robots_mode must be one of: strict, warn, off");
        }
        if (fetchWeb.cacheTtlMinutes() < 1) {
            errors.add("runtime.fetch_web.cache_ttl_minutes must be at least 1");
        }

        EvaluationRuntimeConfig evaluation = runtimeSettings.evaluation();
        if (evaluation == null) {
            errors.add("runtime.evaluation must be defined");
            return;
        }
        if (evaluation.maxConcurrency() < 1) {
            errors.add("runtime.evaluation.max_concurrency must be at least 1");
        }
    }

    private void validateDecisionSignals(
            DecisionSignalsConfig decisionSignals,
            List<String> errors
    ) {
        if (decisionSignals == null) {
            errors.add("decision-signals config must be defined");
            return;
        }

        DecisionSignalsConfig.LanguageConfig language = decisionSignals.language();
        if (language == null) {
            errors.add("decision-signals.language must be defined");
        } else {
            validateDecisionSignalList(language.requiredKeywords(), "decision-signals.language.required_keywords", errors);
            validateDecisionSignalList(
                    language.frictionSoftKeywords(),
                    "decision-signals.language.friction_soft_keywords",
                    errors
            );
            validateDecisionSignalList(
                    language.mediumHighFrictionKeywords(),
                    "decision-signals.language.medium_high_friction_keywords",
                    errors
            );
            validateDecisionSignalList(
                    language.highFrictionKeywords(),
                    "decision-signals.language.high_friction_keywords",
                    errors
            );
            validateDecisionSignalList(
                    language.assignmentDependentKeywords(),
                    "decision-signals.language.assignment_dependent_keywords",
                    errors
            );
            validateDecisionSignalList(
                    language.optionalOrExemptKeywords(),
                    "decision-signals.language.optional_or_exempt_keywords",
                    errors
            );
            validateDecisionSignalList(
                    language.englishFriendlyKeywords(),
                    "decision-signals.language.english_friendly_keywords",
                    errors
            );
            validateDecisionSignalList(
                    language.englishSupportEnvironmentKeywords(),
                    "decision-signals.language.english_support_environment_keywords",
                    errors
            );
            if (language.englishSupportMaxIndex() < 0 || language.englishSupportMaxIndex() > 100) {
                errors.add("decision-signals.language.english_support_max_index must be between 0 and 100");
            }
            if (language.assignmentDependentBaseIndex() < 0 || language.assignmentDependentBaseIndex() > 100) {
                errors.add("decision-signals.language.assignment_dependent_base_index must be between 0 and 100");
            }
            if (language.assignmentDependentMinIndex() < 0 || language.assignmentDependentMinIndex() > 100) {
                errors.add("decision-signals.language.assignment_dependent_min_index must be between 0 and 100");
            }
            if (language.assignmentDependentMinIndex() > language.assignmentDependentBaseIndex()) {
                errors.add(
                        "decision-signals.language.assignment_dependent_min_index must be <= assignment_dependent_base_index"
                );
            }
        }

        DecisionSignalsConfig.WorkLifeBalanceConfig workLifeBalance = decisionSignals.workLifeBalance();
        if (workLifeBalance == null) {
            errors.add("decision-signals.work_life_balance must be defined");
        } else {
            validateDecisionSignalList(
                    workLifeBalance.overtimeRiskKeywords(),
                    "decision-signals.work_life_balance.overtime_risk_keywords",
                    errors
            );
            validateDecisionSignalList(
                    workLifeBalance.holidayPolicyRiskKeywords(),
                    "decision-signals.work_life_balance.holiday_policy_risk_keywords",
                    errors
            );
        }

        DecisionSignalsConfig.MobilityConfig mobility = decisionSignals.mobility();
        if (mobility == null) {
            errors.add("decision-signals.mobility must be defined");
        } else {
            validateDecisionSignalList(
                    mobility.locationMobilityRiskKeywords(),
                    "decision-signals.mobility.location_mobility_risk_keywords",
                    errors
            );
        }

        DecisionSignalsConfig.JobPostQualityConfig jobPostQuality = decisionSignals.jobPostQuality();
        if (jobPostQuality == null) {
            errors.add("decision-signals.job_post_quality must be defined");
        } else {
            validateDecisionSignalList(
                    jobPostQuality.genericMarketingRiskKeywords(),
                    "decision-signals.job_post_quality.generic_marketing_risk_keywords",
                    errors
            );
            if (jobPostQuality.genericMarketingRiskHitMin() < 1) {
                errors.add("decision-signals.job_post_quality.generic_marketing_risk_hit_min must be at least 1");
            }
            validateDecisionSignalList(
                    jobPostQuality.conditionsSectionKeywords(),
                    "decision-signals.job_post_quality.conditions_section_keywords",
                    errors
            );
            validateDecisionSignalList(
                    jobPostQuality.vagueConditionsRiskKeywords(),
                    "decision-signals.job_post_quality.vague_conditions_risk_keywords",
                    errors
            );
            if (jobPostQuality.vagueConditionsRiskHitMin() < 1) {
                errors.add("decision-signals.job_post_quality.vague_conditions_risk_hit_min must be at least 1");
            }
            validateDecisionSignalList(
                    jobPostQuality.concreteConditionsKeywords(),
                    "decision-signals.job_post_quality.concrete_conditions_keywords",
                    errors
            );
        }
    }

    private void validateId(String value, String field, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " cannot be blank");
            return;
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            errors.add(field + " must match pattern " + ID_PATTERN.pattern());
        }
    }

    private boolean isValidHttpUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void checkDuplicates(List<String> values, String field, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty() && !seen.add(normalized)) {
                errors.add(field + " contains duplicate value '" + value + "'");
            }
        }
    }

    private void validateAllowedTags(
            List<String> tags,
            Set<String> allowed,
            String field,
            List<String> errors
    ) {
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (!allowed.contains(normalized)) {
                errors.add(field + " contains unknown tag '" + tag + "'");
            }
        }
    }

    private void validateDecisionSignalList(
            List<String> values,
            String field,
            List<String> errors
    ) {
        if (values == null || values.isEmpty()) {
            errors.add(field + " must contain at least one item");
            return;
        }
        checkDuplicates(values, field, errors);
    }

    private Set<String> normalizeSet(List<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String entry = normalize(value);
            if (!entry.isEmpty()) {
                normalized.add(entry);
            }
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
