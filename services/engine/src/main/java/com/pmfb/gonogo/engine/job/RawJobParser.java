package com.pmfb.gonogo.engine.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RawJobParser {
    private static final String OPTIONAL_LIST_PREFIX_REGEX = "^\\s*[•・*\\-]?\\s*";
    private static final int LEADING_CANDIDATE_LIMIT = 6;
    private static final Pattern COMPANY_NAME_CANDIDATE_PATTERN = Pattern.compile(
            "^[A-Z][\\w&+,.()'/-]*(?:\\s+[A-Z][\\w&+,.()'/-]*){0,5}$"
    );
    private static final Pattern ABOUT_COMPANY_HEADING_PATTERN = Pattern.compile("(?i)^about\\s+(.+)$");
    private static final Pattern LOOKING_FOR_TITLE_PATTERN = Pattern.compile(
            "(?i)looking\\s+for\\s+(?:an?|the)?\\s*(?:experienced|senior|staff|lead|principal|talented)?\\s*"
                    + "([a-z][a-z0-9/+&\\- ]{1,80}?(?:engineer|developer|architect|scientist|manager|designer))\\b"
    );
    private static final List<Pattern> TITLE_PATTERNS = List.of(
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "(title|role|position)\\s*:\\s*(.+)$"),
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "job\\s+title\\s*:\\s*(.+)$"),
            Pattern.compile(OPTIONAL_LIST_PREFIX_REGEX + "(職種|募集職種|ポジション|求人名)\\s*[:：]\\s*(.+)$")
    );
    private static final List<Pattern> COMPANY_PATTERNS = List.of(
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "(company|company name|employer)\\s*:\\s*(.+)$"),
            Pattern.compile(OPTIONAL_LIST_PREFIX_REGEX + "(会社名|企業名|雇用主)\\s*[:：]\\s*(.+)$")
    );
    private static final List<Pattern> LOCATION_PATTERNS = List.of(
            Pattern.compile("(?i)" + OPTIONAL_LIST_PREFIX_REGEX + "(location|office|based in)\\s*:\\s*(.+)$"),
            Pattern.compile(OPTIONAL_LIST_PREFIX_REGEX + "(勤務地|勤務場所)\\s*[:：]\\s*(.+)$")
    );
    private static final List<Pattern> SALARY_LABEL_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)" + OPTIONAL_LIST_PREFIX_REGEX
                            + "(salary|compensation|pay range|annual salary range|annual salary|年収|給与|想定年収|月給|年俸|報酬)"
                            + "\\s*[:：]\\s*(.+)$"
            )
    );
    private static final List<Pattern> REMOTE_POLICY_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)" + OPTIONAL_LIST_PREFIX_REGEX
                            + "(remote policy|work style|working style|work mode|remote/hybrid|employment type|work location)"
                            + "\\s*[:：]\\s*(.+)$"
            ),
            Pattern.compile(OPTIONAL_LIST_PREFIX_REGEX + "(勤務形態|働き方|リモートワーク)\\s*[:：]\\s*(.+)$")
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
            "office based",
            "出社",
            "出社必須"
    );
    private static final List<String> REMOTE_KEYWORDS = List.of(
            "remote",
            "work from home",
            "wfh",
            "remote work system available",
            "remote available",
            "fully remote",
            "在宅",
            "リモート",
            "在宅勤務"
    );
    private static final List<String> NO_REMOTE_KEYWORDS = List.of(
            "no remote",
            "not remote",
            "remote not available",
            "remote unavailable",
            "remote work not available",
            "without remote"
    );
    private static final List<String> HYBRID_KEYWORDS = List.of(
            "hybrid",
            "partially remote",
            "partial remote",
            "remote and office",
            "office and remote",
            "ハイブリッド"
    );
    private static final List<String> REMOTE_POLICY_IGNORED_LINE_KEYWORDS = List.of(
            "interview",
            "technical assignment",
            "hiring process",
            "selection process",
            "group video call"
    );
    private static final List<String> TITLE_CANDIDATE_KEYWORDS = List.of(
            "engineer",
            "developer",
            "architect",
            "scientist",
            "designer",
            "manager",
            "backend",
            "front-end",
            "frontend",
            "full-stack",
            "full stack",
            "mobile",
            "ios",
            "android",
            "platform",
            "sre",
            "qa",
            "server-side",
            "serverside",
            "client-side",
            "clientside",
            "エンジニア",
            "開発",
            "設計",
            "デザイナー",
            "マネージャ",
            "バックエンド",
            "フロントエンド",
            "サーバサイド",
            "クライアントサイド"
    );
    private static final List<String> TITLE_NOISE_KEYWORDS = List.of(
            "requirements",
            "responsibilities",
            "qualifications",
            "benefits",
            "conditions",
            "待遇",
            "福利厚生",
            "勤務地",
            "給与"
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
        String title = normalizeOverride(titleOverride);
        if (title.isBlank()) {
            title = detectFirstMatchingValue(lines, TITLE_PATTERNS).orElse("");
        }
        if (title.isBlank()) {
            title = inferTitle(lines).orElse("Unknown Role");
            warnings.add("Title not found in labeled fields; using inferred/fallback title.");
        }
        if (company.isBlank()) {
            company = inferCompany(lines, title).orElse("Unknown Company");
            warnings.add("Company name not found in labeled fields; using inferred/fallback company.");
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
                .orElseGet(() -> inferRemotePolicy(lines));
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

    private Optional<String> inferTitle(List<String> lines) {
        Optional<String> fromHeading = inferTitleFromHeading(lines);
        if (fromHeading.isPresent()) {
            return fromHeading;
        }
        Optional<String> fromNarrative = inferTitleFromNarrative(lines);
        if (fromNarrative.isPresent()) {
            return fromNarrative;
        }
        List<String> leadingLines = collectLeadingMeaningfulLines(lines);
        for (int i = 0; i < leadingLines.size(); i++) {
            String line = leadingLines.get(i);
            if (looksLikeTitleCandidate(line)) {
                return Optional.of(line);
            }
            if (looksLikeCompanyCandidate(line) && i + 1 < leadingLines.size()) {
                String nextLine = leadingLines.get(i + 1);
                if (looksLikeTitleCandidate(nextLine)) {
                    return Optional.of(nextLine);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> inferTitleFromNarrative(List<String> lines) {
        for (String line : lines) {
            String normalized = normalizeCandidateLine(line);
            if (normalized.isBlank()) {
                continue;
            }
            Matcher matcher = LOOKING_FOR_TITLE_PATTERN.matcher(normalized);
            if (matcher.find()) {
                return Optional.of(toDisplayCase(matcher.group(1)));
            }
        }
        return Optional.empty();
    }

    private Optional<String> inferTitleFromHeading(List<String> lines) {
        for (String line : lines) {
            String trimmed = normalizeCandidateLine(line);
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                String heading = trimmed.replaceFirst("^#+\\s*", "").trim();
                if (!heading.isBlank() && looksLikeTitleCandidate(heading)) {
                    return Optional.of(heading);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> inferCompany(List<String> lines, String title) {
        Optional<String> aboutHeadingCompany = inferCompanyFromAboutHeading(lines);
        if (aboutHeadingCompany.isPresent()) {
            return aboutHeadingCompany;
        }
        List<String> leadingLines = collectLeadingMeaningfulLines(lines);
        for (int i = 0; i < leadingLines.size(); i++) {
            String line = leadingLines.get(i);
            if (line.equals(title)) {
                continue;
            }
            if (looksLikeCompanyCandidate(line)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    private Optional<String> inferCompanyFromAboutHeading(List<String> lines) {
        for (String line : lines) {
            String normalized = normalizeCandidateLine(line);
            if (normalized.isBlank()) {
                continue;
            }
            Matcher matcher = ABOUT_COMPANY_HEADING_PATTERN.matcher(normalized);
            if (!matcher.matches()) {
                continue;
            }
            String candidate = matcher.group(1).trim();
            String lowered = normalize(candidate);
            if (candidate.isBlank() || "the job".equals(lowered) || "job".equals(lowered)) {
                continue;
            }
            if (looksLikeCompanyCandidate(candidate) || candidate.length() <= 80) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String inferLocation(String loweredText) {
        if (loweredText.contains("otemachi") || loweredText.contains("東京") || loweredText.contains("tokyo")) {
            return "Tokyo";
        }
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
        if (loweredText.contains("日本国内")) {
            return "Japan";
        }
        if (containsAny(loweredText, NO_REMOTE_KEYWORDS)) {
            return "";
        }
        if (loweredText.contains("remote")) {
            return "Remote";
        }
        if (containsAny(loweredText, REMOTE_KEYWORDS)) {
            return "Remote";
        }
        return "";
    }

    private String inferRemotePolicy(List<String> lines) {
        boolean hasNoRemote = false;
        boolean hasRemote = false;
        boolean hasHybrid = false;
        boolean hasOnsite = false;

        for (String line : lines) {
            String normalizedLine = normalize(line);
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (containsAny(normalizedLine, REMOTE_POLICY_IGNORED_LINE_KEYWORDS)) {
                continue;
            }
            if (containsAny(normalizedLine, NO_REMOTE_KEYWORDS)) {
                hasNoRemote = true;
            }
            if (containsAny(normalizedLine, REMOTE_KEYWORDS)) {
                hasRemote = true;
            }
            if (containsAny(normalizedLine, HYBRID_KEYWORDS)) {
                hasHybrid = true;
            }
            if (containsAny(normalizedLine, ONSITE_ONLY_KEYWORDS)) {
                hasOnsite = true;
            }
        }

        if (hasNoRemote) {
            return "Onsite-only";
        }
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
        boolean hasNoRemote = containsAny(lowered, NO_REMOTE_KEYWORDS);
        boolean hasRemote = containsAny(lowered, REMOTE_KEYWORDS);
        boolean hasHybrid = containsAny(lowered, HYBRID_KEYWORDS);
        boolean hasOnsite = containsAny(lowered, ONSITE_ONLY_KEYWORDS)
                || "office".equals(lowered)
                || hasNoRemote;
        if (hasNoRemote) {
            return "Onsite-only";
        }
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

    private List<String> collectLeadingMeaningfulLines(List<String> lines) {
        List<String> candidates = new ArrayList<>();
        for (String line : lines) {
            String normalized = normalizeCandidateLine(line);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.contains(":") || normalized.contains("：")) {
                continue;
            }
            candidates.add(normalized);
            if (candidates.size() >= LEADING_CANDIDATE_LIMIT) {
                break;
            }
        }
        return candidates;
    }

    private String normalizeCandidateLine(String line) {
        String normalized = line == null ? "" : line.trim();
        normalized = normalized.replaceFirst("^#+\\s*", "");
        normalized = normalized.replaceFirst("^\\s*[•・*\\-]+\\s*", "");
        return normalized.trim();
    }

    private boolean looksLikeTitleCandidate(String line) {
        String normalized = normalizeCandidateLine(line);
        if (normalized.isBlank() || normalized.length() > 140) {
            return false;
        }
        if (containsAny(normalize(normalized), TITLE_NOISE_KEYWORDS)) {
            return false;
        }
        return containsAny(normalize(normalized), TITLE_CANDIDATE_KEYWORDS);
    }

    private boolean looksLikeCompanyCandidate(String line) {
        String normalized = normalizeCandidateLine(line);
        if (normalized.isBlank() || normalized.length() > 100) {
            return false;
        }
        if (looksLikeTitleCandidate(normalized)) {
            return false;
        }
        if (normalized.contains(":") || normalized.contains("：")) {
            return false;
        }
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
            return false;
        }
        if (normalized.contains("株式会社")) {
            return true;
        }
        return COMPANY_NAME_CANDIDATE_PATTERN.matcher(normalized).matches();
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String toDisplayCase(String value) {
        String[] parts = value.trim().split("\\s+");
        List<String> formatted = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String lowered = part.toLowerCase(Locale.ROOT);
            formatted.add(Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1));
        }
        return String.join(" ", formatted);
    }
}
