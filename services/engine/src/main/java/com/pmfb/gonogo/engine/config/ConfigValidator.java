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
    private static final Set<String> REQUIRED_PERSONA_HARD_NO =
            Set.of("consulting_company", "onsite_only");
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

            Set<String> hardNoSet = normalizeSet(persona.hardNo());
            for (String required : REQUIRED_PERSONA_HARD_NO) {
                if (!hardNoSet.contains(required)) {
                    errors.add(context + ".hard_no must include '" + required + "'");
                }
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
