package com.pmfb.gonogo.engine.job;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

final class CareerPageResponseCache {
    private static final String BODY_MARKER = "---BODY---";
    private final Path cacheDir;

    CareerPageResponseCache(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    Optional<CachedFetchResult> read(String url) {
        Path file = cacheFile(url);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int markerIndex = content.indexOf(BODY_MARKER + "\n");
            if (markerIndex < 0) {
                return Optional.empty();
            }
            String header = content.substring(0, markerIndex);
            String body = content.substring(markerIndex + BODY_MARKER.length() + 1);

            int status = parseIntValue(header, "status");
            long fetchedAt = parseLongValue(header, "fetched_at_epoch_ms");
            String finalUrl = parseStringValue(header, "final_url");

            CareerPageHttpFetcher.FetchResult response = new CareerPageHttpFetcher.FetchResult(status, finalUrl, body);
            return Optional.of(new CachedFetchResult(response, fetchedAt));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    void write(String url, CareerPageHttpFetcher.FetchResult response) throws IOException {
        Files.createDirectories(cacheDir);
        StringBuilder sb = new StringBuilder();
        sb.append("status:").append(response.statusCode()).append("\n");
        sb.append("fetched_at_epoch_ms:").append(Instant.now().toEpochMilli()).append("\n");
        sb.append("final_url:").append(escape(response.finalUrl())).append("\n");
        sb.append(BODY_MARKER).append("\n");
        sb.append(response.body() == null ? "" : response.body());
        Files.writeString(cacheFile(url), sb.toString(), StandardCharsets.UTF_8);
    }

    private int parseIntValue(String header, String key) {
        String value = parseStringValue(header, key);
        return Integer.parseInt(value);
    }

    private long parseLongValue(String header, String key) {
        String value = parseStringValue(header, key);
        return Long.parseLong(value);
    }

    private String parseStringValue(String header, String key) {
        String prefix = key + ":";
        String[] lines = header.split("\\R");
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return unescape(line.substring(prefix.length()));
            }
        }
        throw new IllegalArgumentException("Missing cache key: " + key);
    }

    private Path cacheFile(String url) {
        return cacheDir.resolve(sha256(url) + ".cache");
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String unescape(String value) {
        return value.replace("\\r", "\r").replace("\\n", "\n").replace("\\\\", "\\");
    }

    record CachedFetchResult(
            CareerPageHttpFetcher.FetchResult response,
            long fetchedAtEpochMillis
    ) {
    }
}
