package com.pmfb.gonogo.engine.config;

import java.util.Locale;

public record FetchWebRuntimeConfig(
        int timeoutSeconds,
        String userAgent,
        int retries,
        long backoffMillis,
        long requestDelayMillis,
        int maxConcurrency,
        int maxConcurrencyPerHost,
        String robotsMode,
        long cacheTtlMinutes
) {
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final String DEFAULT_USER_AGENT = "go-no-go-engine/0.1 (+https://local)";
    private static final int DEFAULT_RETRIES = 2;
    private static final long DEFAULT_BACKOFF_MILLIS = 300;
    private static final long DEFAULT_REQUEST_DELAY_MILLIS = 1200;
    private static final int DEFAULT_MAX_CONCURRENCY = 4;
    private static final int DEFAULT_MAX_CONCURRENCY_PER_HOST = 1;
    private static final String DEFAULT_ROBOTS_MODE = "strict";
    private static final long DEFAULT_CACHE_TTL_MINUTES = 720;

    public static FetchWebRuntimeConfig defaults() {
        return new FetchWebRuntimeConfig(
                DEFAULT_TIMEOUT_SECONDS,
                DEFAULT_USER_AGENT,
                DEFAULT_RETRIES,
                DEFAULT_BACKOFF_MILLIS,
                DEFAULT_REQUEST_DELAY_MILLIS,
                DEFAULT_MAX_CONCURRENCY,
                DEFAULT_MAX_CONCURRENCY_PER_HOST,
                DEFAULT_ROBOTS_MODE,
                DEFAULT_CACHE_TTL_MINUTES
        );
    }

    public FetchWebRuntimeConfig {
        userAgent = userAgent == null ? DEFAULT_USER_AGENT : userAgent.trim();
        robotsMode = normalizeRobotsMode(robotsMode);
    }

    private static String normalizeRobotsMode(String raw) {
        return raw == null ? DEFAULT_ROBOTS_MODE : raw.trim().toLowerCase(Locale.ROOT);
    }
}
