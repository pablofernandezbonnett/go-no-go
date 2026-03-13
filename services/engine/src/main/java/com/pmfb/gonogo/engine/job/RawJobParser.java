package com.pmfb.gonogo.engine.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RawJobParser {
    private static final String OPTIONAL_LIST_PREFIX_REGEX = "^\\s*[•・*\\-]?\\s*";
    private static final List<Pattern> TITLE_PATTERNS = List.of(
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "(title|role|position)\\s*:\\s*(.+)$"),
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "job\\s+title\\s*:\\s*(.+)$")
    );
    private static final List<Pattern> COMPANY_PATTERNS = List.of(
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "(company|company name|employer)\\s*:\\s*(.+)$")
    );
    private static final List<Pattern> LOCATION_PATTERNS = List.of(
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "(location|office|based in)\\s*:\\s*(.+)$"),
            Pattern.compile(OPTIONAL_LIST_PREFIX_REGEX + "(勤務地|勤務場所)\\s*[:：]\\s*(.+)$")
    );
    private static final List<Pattern> SALARY_LABEL_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)" + OPTIONAL_LIST_PREFIX_REGEX
                            + "(salary|compensation|pay range|annual salary range|annual salary|年収|給与)"
                            + "\\s*[:：]\\s*(.+)$"
            )
    );
    private static final List<Pattern> REMOTE_POLICY_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)" + OPTIONAL_LIST_PREFIX_REGEX
                            + "(remote policy|work style|working style|work mode|remote/hybrid)\\s*[:：]\\s*(.+)$"
            )
    );
    private static final List<String> ONSITE_ONLY_KEYWORDS = List.of(
            "onsite",
            "on-site",
            "on site",
            "onsite only",
            "office attendance required",
            "work from office",
            "in office",
            "office-based",
            "office based"
    );
    private static final Pattern CURRENCY_RANGE_PATTERN = Pattern.compile(
            "(?i)(?:JPY|USD|EUR|¥|\\$|€)\\s*\\d[\\d,]*(?:\\.\\d+)?(?:\\s*(?:million|m))?"
                    + "(?:\\s*(?:-|~|to)\\s*(?:JPY|USD|EUR|¥|\\$|€)?\\s*\\d[\\d,]*(?:\\.\\d+)?"
                    + "(?:\\s*(?:million|m))?)?"
    );

    public RawJobExtractionResult parse(String rawText, String companyOverride, String titleOverride) {
        List<String> warnings = new ArrayList<>();
        List<String> lines = splitLines(rawText);
        String lowered = normalize(rawText);

        String company = normalizeOverride(companyOverride);
        if (company.isBlank()) {
            company = detectFirstMatchingValue(lines, COMPANY_PATTERNS).orElse("");
        }
        if (company.isBlank()) {
            company = "Unknown Company";
            warnings.add("Company name not found in raw text; using 'Unknown Company'.");
        }

        String title = normalizeOverride(titleOverride);
        if (title.isBlank()) {
            title = detectFirstMatchingValue(lines, TITLE_PATTERNS).orElse("");
        }
        if (title.isBlank()) {
            title = inferTitleFromHeading(lines).orElse("Unknown Role");
            warnings.add("Title not found in labeled fields; using inferred/fallback title.");
        }

        String location = detectFirstMatchingValue(lines, LOCATION_PATTERNS).orElseGet(
                () -> inferLocation(lowered)
        );
        if (location.isBlank()) {
            location = "Unspecified";
            warnings.add("Location not found; using 'Unspecified'.");
        }

        String salaryRange = detectSalary(lines).orElse("TBD");
        if ("TBD".equals(salaryRange)) {
            warnings.add("Salary range not found; using 'TBD'.");
        }

        String remotePolicy = detectFirstMatchingValue(lines, REMOTE_POLICY_PATTERNS)
                .map(this::normalizeRemotePolicyLabelValue)
                .orElseGet(() -> inferRemotePolicy(lowered));
        if ("Unspecified".equals(remotePolicy)) {
            warnings.add("Remote policy not found; using 'Unspecified'.");
        }

        String description = sanitizeDescription(rawText);
        if (description.isBlank()) {
            description = "No description provided.";
            warnings.add("Description was empty; using placeholder.");
        }

        JobInput jobInput = new JobInput(company, title, location, salaryRange, remotePolicy, description);
        return new RawJobExtractionResult(jobInput, warnings);
    }

    private Optional<String> detectSalary(List<String> lines) {
        Optional<String> fromLabel = detectFirstMatchingValue(lines, SALARY_LABEL_PATTERNS);
        if (fromLabel.isPresent()) {
            return fromLabel;
        }

        for (String line : lines) {
            Matcher matcher = CURRENCY_RANGE_PATTERN.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group().trim());
            }
        }
        return Optional.empty();
    }

    private Optional<String> detectFirstMatchingValue(List<String> lines, List<Pattern> patterns) {
        for (String line : lines) {
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String value = matcher.group(matcher.groupCount()).trim();
                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> inferTitleFromHeading(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                String heading = trimmed.replaceFirst("^#+\\s*", "").trim();
                if (!heading.isBlank()) {
                    return Optional.of(heading);
                }
            }
        }
        return Optional.empty();
    }

    private String inferLocation(String loweredText) {
        if (loweredText.contains("tokyo")) {
            return "Tokyo";
        }
        if (loweredText.contains("osaka")) {
            return "Osaka";
        }
        if (loweredText.contains("kyoto")) {
            return "Kyoto";
        }
        if (loweredText.contains("japan")) {
            return "Japan";
        }
        if (loweredText.contains("remote")) {
            return "Remote";
        }
        return "";
    }

    private String inferRemotePolicy(String loweredText) {
        boolean hasRemote = loweredText.contains("remote")
                || loweredText.contains("work from home")
                || loweredText.contains("wfh");
        boolean hasHybrid = loweredText.contains("hybrid");
        boolean hasOnsite = containsAny(loweredText, ONSITE_ONLY_KEYWORDS);

        if (hasHybrid || (hasRemote && hasOnsite)) {
            return "Hybrid";
        }
        if (hasRemote) {
            return "Remote";
        }
        if (hasOnsite) {
            return "Onsite-only";
        }
        return "Unspecified";
    }

    private String normalizeRemotePolicyLabelValue(String rawValue) {
        String lowered = normalize(rawValue);
        boolean hasRemote = lowered.contains("remote") || lowered.contains("wfh");
        boolean hasHybrid = lowered.contains("hybrid");
        boolean hasOnsite = containsAny(lowered, ONSITE_ONLY_KEYWORDS) || "office".equals(lowered);
        if (hasHybrid || (hasRemote && hasOnsite)) {
            return "Hybrid";
        }
        if (hasRemote) {
            return "Remote";
        }
        if (hasOnsite) {
            return "Onsite-only";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "Unspecified" : normalized;
    }

    private List<String> splitLines(String text) {
        return List.of(text.split("\\R"));
    }

    private String sanitizeDescription(String text) {
        return text == null ? "" : text.trim();
    }

    private String normalizeOverride(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
