package com.pmfb.gonogo.engine;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AdHocArtifactFileNameResolver {
    private static final String URL_FALLBACK_SEGMENT = "url";
    private static final String TEXT_FALLBACK_SEGMENT = "text";
    private static final String FILE_EXTENSION = ".yaml";
    private static final int STABLE_NAME_MAX_LENGTH = 72;
    private static final Pattern TEXT_ARTIFACT_PATTERN =
            Pattern.compile("^(adhoc-[^-]+)-(.+)-(text\\.yaml)$");

    Path buildUrlArtifactPath(
            Path inputDir,
            String personaId,
            String candidateProfileId,
            String url
    ) {
        URI sourceUri = URI.create(url);
        String hostSegment = sanitizeFileSegment(
                sourceUri.getHost() == null || sourceUri.getHost().isBlank()
                        ? URL_FALLBACK_SEGMENT
                        : sourceUri.getHost()
        );
        String pathSegment = sanitizeFileSegment(resolveLastPathSegment(sourceUri));
        String stableStem = truncateFileSegment(hostSegment + "-" + pathSegment, STABLE_NAME_MAX_LENGTH);
        return inputDir.resolve(
                "adhoc-url-"
                        + scopeSegment(personaId, candidateProfileId)
                        + "-"
                        + stableStem
                        + "-"
                        + stableHash(url)
                        + FILE_EXTENSION
        );
    }

    Path buildTextArtifactPath(
            Path inputDir,
            Path templateArtifactFile,
            String personaId,
            String candidateProfileId,
            String rawText
    ) {
        String scopeSegment = scopeSegment(personaId, candidateProfileId);
        String fileName = templateArtifactFile.getFileName().toString();
        Matcher matcher = TEXT_ARTIFACT_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            return inputDir.resolve(matcher.group(1) + "-" + scopeSegment + "-" + matcher.group(3));
        }

        return inputDir.resolve(
                "adhoc-text-"
                        + scopeSegment
                        + "-"
                        + stableHash(rawText)
                        + FILE_EXTENSION
        );
    }

    private String resolveLastPathSegment(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return URL_FALLBACK_SEGMENT;
        }

        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isBlank()) {
                return segments[i];
            }
        }
        return URL_FALLBACK_SEGMENT;
    }

    private String scopeSegment(String personaId, String candidateProfileId) {
        return sanitizeFileSegment(personaId + "--" + candidateProfileId);
    }

    private String sanitizeFileSegment(String input) {
        String normalized = input == null
                ? ""
                : input.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("-{2,}", "-")
                        .replaceAll("^-|-$", "");
        return normalized.isBlank() ? TEXT_FALLBACK_SEGMENT : normalized;
    }

    private String truncateFileSegment(String input, int maxLength) {
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }

    private String stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        String hex = Long.toUnsignedString(hash, 16);
        return "0".repeat(Math.max(0, 16 - hex.length())) + hex;
    }
}
