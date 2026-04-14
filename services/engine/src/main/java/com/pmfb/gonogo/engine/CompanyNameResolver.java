package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class CompanyNameResolver {
    private static final String DEFAULT_UNKNOWN = "Unknown";
    private static final Pattern DOMAIN_LIKE_PATTERN = Pattern.compile("(?i)^[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}$");
    private static final Pattern SOURCE_URL_LINE_PATTERN = Pattern.compile("(?im)^Source URL:\\s*\\S+\\s*$");
    private static final Set<String> GENERIC_PLACEHOLDERS = Set.of(
            "unknown",
            "unknown company",
            "our client",
            "one of our clients",
            "our customer",
            "client",
            "confidential",
            "stealth",
            "hiring company",
            "the company"
    );
    private static final Set<String> SECOND_LEVEL_COUNTRY_CODE_SUFFIXES = Set.of(
            "ac",
            "co",
            "com",
            "edu",
            "go",
            "gov",
            "net",
            "or",
            "org"
    );
    private static final Map<String, String> HOST_ALIASES = Map.ofEntries(
            Map.entry("bizreach.jp", "BizReach"),
            Map.entry("doda.jp", "Doda"),
            Map.entry("enworld.com", "en world"),
            Map.entry("greenhouse.io", "Greenhouse"),
            Map.entry("hays.co.jp", "Hays"),
            Map.entry("hays.com", "Hays"),
            Map.entry("japan-dev.com", "Japan Dev"),
            Map.entry("lever.co", "Lever"),
            Map.entry("linkedin.com", "LinkedIn"),
            Map.entry("michaelpage.co.jp", "Michael Page"),
            Map.entry("michaelpage.com", "Michael Page"),
            Map.entry("randstad.co.jp", "Randstad"),
            Map.entry("robertwalters.co.jp", "Robert Walters"),
            Map.entry("talentio.com", "Talentio"),
            Map.entry("tokyodev.com", "TokyoDev"),
            Map.entry("wantedly.com", "Wantedly"),
            Map.entry("workable.com", "Workable")
    );

    String resolveConfiguredCompanyName(String url, List<CompanyConfig> companies) {
        String host = hostOf(url);
        for (CompanyConfig company : companies) {
            if (sameOrSubdomain(host, hostOf(company.careerUrl()))
                    || sameOrSubdomain(host, hostOf(company.corporateUrl()))) {
                return normalizeCandidate(company.name());
            }
        }
        return host.isBlank() ? DEFAULT_UNKNOWN : host;
    }

    String resolve(
            String currentCompanyName,
            String title,
            String description,
            String aboutHeading,
            String sourceUrl
    ) {
        String normalizedCurrent = normalizeCandidate(currentCompanyName);
        if (!looksLikeGenericCompanyPlaceholder(normalizedCurrent)) {
            return normalizedCurrent;
        }

        String fromTitle = inferCompanyNameFromTitle(title);
        if (!fromTitle.isBlank()) {
            return fromTitle;
        }

        String fromDescription = inferCompanyNameFromDescription(description);
        if (!fromDescription.isBlank()) {
            return fromDescription;
        }

        String normalizedAboutHeading = normalizeCandidate(aboutHeading);
        if (!normalizedAboutHeading.isBlank() && !looksLikeGenericCompanyPlaceholder(normalizedAboutHeading)) {
            return normalizedAboutHeading;
        }

        String fromUrl = inferCompanyNameFromUrl(sourceUrl);
        if (!fromUrl.isBlank()) {
            return fromUrl;
        }

        return normalizedCurrent;
    }

    boolean looksLikeGenericCompanyPlaceholder(String companyName) {
        String normalized = normalizeCandidate(companyName);
        if (normalized.isBlank()) {
            return true;
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (GENERIC_PLACEHOLDERS.contains(lowered)) {
            return true;
        }
        if (DEFAULT_UNKNOWN.equalsIgnoreCase(normalized)) {
            return true;
        }
        if (DOMAIN_LIKE_PATTERN.matcher(lowered).matches()) {
            return true;
        }
        if (lowered.contains("our client")) {
            return true;
        }
        if (lowered.startsWith("based in ")) {
            return true;
        }
        return lowered.contains("confidential") || lowered.contains("stealth");
    }

    String inferCompanyNameFromTitle(String title) {
        String normalized = normalizeCandidate(title);
        if (normalized.isBlank()) {
            return "";
        }

        String[] pipeSegments = normalized.split("\\s+\\|\\s+");
        for (String segment : pipeSegments) {
            String fromDash = inferCompanyNameFromDashSeparatedSegment(segment);
            if (!fromDash.isBlank()) {
                return fromDash;
            }
        }

        if (pipeSegments.length > 1) {
            String candidate = sanitizeCandidate(pipeSegments[pipeSegments.length - 1]);
            if (!looksLikeGenericCompanyPlaceholder(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    String inferCompanyNameFromDescription(String description) {
        String normalized = normalizeDescription(description);
        if (normalized.isBlank()) {
            return "";
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("about ") || normalized.length() <= 6) {
            return "";
        }

        int separatorIndex = findDescriptionSeparatorIndex(normalized);
        if (separatorIndex <= 6) {
            return "";
        }

        String candidate = sanitizeCandidate(normalized.substring(6, separatorIndex));
        if (looksLikeGenericCompanyPlaceholder(candidate)) {
            return "";
        }
        return candidate;
    }

    String inferCompanyNameFromUrl(String sourceUrl) {
        String host = hostOf(sourceUrl);
        if (host.isBlank()) {
            return "";
        }

        for (Map.Entry<String, String> entry : HOST_ALIASES.entrySet()) {
            if (sameOrSubdomain(host, entry.getKey())) {
                return entry.getValue();
            }
        }

        String brandToken = registrableBrandToken(host);
        if (brandToken.isBlank()) {
            return "";
        }
        return humanizeBrandToken(brandToken);
    }

    private String inferCompanyNameFromDashSeparatedSegment(String value) {
        int separatorIndex = value.lastIndexOf(" - ");
        if (separatorIndex < 0 || separatorIndex + 3 >= value.length()) {
            return "";
        }

        String candidate = sanitizeCandidate(value.substring(separatorIndex + 3));
        if (looksLikeGenericCompanyPlaceholder(candidate)) {
            return "";
        }
        return candidate;
    }

    private int findDescriptionSeparatorIndex(String description) {
        int separatorIndex = description.indexOf('.');
        if (separatorIndex < 0) {
            separatorIndex = description.indexOf(':');
        }
        if (separatorIndex < 0) {
            separatorIndex = description.indexOf(' ');
        }
        return separatorIndex;
    }

    private String normalizeDescription(String description) {
        String withoutSourceUrl = SOURCE_URL_LINE_PATTERN.matcher(description == null ? "" : description)
                .replaceAll("")
                .trim();
        return normalizeCandidate(withoutSourceUrl);
    }

    private String sanitizeCandidate(String value) {
        String normalized = normalizeCandidate(value);
        if (normalized.isBlank()) {
            return "";
        }

        normalized = normalized.replaceAll("^[\"'`\\s]+|[\"'`\\s]+$", "");
        normalized = normalized.replaceAll(
                "(?i)\\b(group recruiting information|recruiting information|careers?|jobs?|job board|hiring)\\b$",
                ""
        ).trim();
        normalized = normalized.replaceAll("[\\s,;:|\\-]+$", "").trim();
        if (normalized.length() > 80) {
            return "";
        }
        return normalized;
    }

    private String registrableBrandToken(String host) {
        String canonicalHost = host.startsWith("www.") ? host.substring(4) : host;
        if (canonicalHost.isBlank()) {
            return "";
        }

        String[] labels = canonicalHost.split("\\.");
        if (labels.length == 0) {
            return "";
        }

        int brandIndex = labels.length - 2;
        if (labels.length >= 3
                && labels[labels.length - 1].length() == 2
                && SECOND_LEVEL_COUNTRY_CODE_SUFFIXES.contains(labels[labels.length - 2])) {
            brandIndex = labels.length - 3;
        }
        if (brandIndex < 0 || brandIndex >= labels.length) {
            return "";
        }
        return labels[brandIndex];
    }

    private String humanizeBrandToken(String token) {
        String normalized = token.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return "";
        }

        String[] words = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private String normalizeCandidate(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String hostOf(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            return host.trim().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private boolean sameOrSubdomain(String candidateHost, String baseHost) {
        if (candidateHost == null || candidateHost.isBlank() || baseHost == null || baseHost.isBlank()) {
            return false;
        }
        return candidateHost.equals(baseHost) || candidateHost.endsWith("." + baseHost);
    }
}
