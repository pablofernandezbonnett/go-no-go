package com.pmfb.gonogo.engine.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class CareerPageFetchServiceTest {
    @Test
    void retriesAndEventuallyFetchesSuccessfully() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        CareerPageFetcher flakyFetcher = (url, timeout, userAgent) -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new IOException("temporary network issue");
            }
            return new CareerPageHttpFetcher.FetchResult(
                    200,
                    url,
                    """
                            <html><body><a href="/jobs/backend">Backend Engineer</a></body></html>
                            """
            );
        };

        Path tempDir = Files.createTempDirectory("gonogo-fetch-retry-test");
        CareerPageFetchService service = new CareerPageFetchService(flakyFetcher, new JobPostingExtractor());
        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                tempDir.resolve("raw"),
                List.of("moneyforward"),
                10,
                10,
                "test-agent",
                2,
                0,
                0,
                tempDir.resolve("cache"),
                720,
                true
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(company("moneyforward", "https://example.com/careers")),
                options
        );

        assertEquals(3, attempts.get());
        assertEquals(1, outcome.selectedCompanies());
        assertEquals(0, outcome.companiesFailed());
        assertEquals(1, outcome.rawFilesGenerated());
        assertTrue(Files.exists(tempDir.resolve("raw/moneyforward/01-backend-engineer.txt")));
    }

    @Test
    void usesFreshCacheWithoutCallingFetcher() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        CareerPageFetcher neverFetcher = (url, timeout, userAgent) -> {
            attempts.incrementAndGet();
            throw new IOException("should not fetch");
        };

        Path tempDir = Files.createTempDirectory("gonogo-fetch-cache-hit-test");
        Path cacheDir = tempDir.resolve("cache");
        new CareerPageResponseCache(cacheDir).write(
                "https://example.com/careers",
                new CareerPageHttpFetcher.FetchResult(
                        200,
                        "https://example.com/careers",
                        "<html><body><a href=\"/jobs/frontend\">Frontend Engineer</a></body></html>"
                )
        );

        CareerPageFetchService service = new CareerPageFetchService(neverFetcher, new JobPostingExtractor());
        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                tempDir.resolve("raw"),
                List.of("mercari"),
                10,
                10,
                "test-agent",
                2,
                0,
                0,
                cacheDir,
                720,
                true
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(company("mercari", "https://example.com/careers")),
                options
        );

        assertEquals(0, attempts.get());
        assertEquals(0, outcome.companiesFailed());
        assertEquals(1, outcome.rawFilesGenerated());
        assertTrue(outcome.informationalMessages().stream().anyMatch(item -> item.contains("Using fresh cache")));
    }

    @Test
    void fallsBackToStaleCacheAfterFetchFailures() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        CareerPageFetcher alwaysFailingFetcher = (url, timeout, userAgent) -> {
            attempts.incrementAndGet();
            throw new IOException("network down");
        };

        Path tempDir = Files.createTempDirectory("gonogo-fetch-stale-cache-test");
        Path cacheDir = tempDir.resolve("cache");
        CareerPageResponseCache cache = new CareerPageResponseCache(cacheDir);
        cache.write(
                "https://example.com/careers",
                new CareerPageHttpFetcher.FetchResult(
                        200,
                        "https://example.com/careers",
                        "<html><body><a href=\"/jobs/platform\">Platform Engineer</a></body></html>"
                )
        );

        Path cacheFile;
        try (Stream<Path> files = Files.list(cacheDir)) {
            cacheFile = files.findFirst().orElseThrow();
        }
        String oldContent = Files.readString(cacheFile, StandardCharsets.UTF_8);
        String staleContent = oldContent.replaceFirst(
                "fetched_at_epoch_ms:\\d+",
                "fetched_at_epoch_ms:" + (Instant.now().minusSeconds(48 * 3600).toEpochMilli())
        );
        Files.writeString(cacheFile, staleContent, StandardCharsets.UTF_8);

        CareerPageFetchService service = new CareerPageFetchService(alwaysFailingFetcher, new JobPostingExtractor());
        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                tempDir.resolve("raw"),
                List.of("freee"),
                10,
                10,
                "test-agent",
                1,
                0,
                0,
                cacheDir,
                1,
                true
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(company("freee", "https://example.com/careers")),
                options
        );

        assertEquals(2, attempts.get());
        assertEquals(0, outcome.companiesFailed());
        assertEquals(1, outcome.rawFilesGenerated());
        assertFalse(outcome.errorMessages().isEmpty());
        assertTrue(outcome.informationalMessages().stream().anyMatch(item -> item.contains("Using stale cache")));
    }

    private CompanyConfig company(String id, String careerUrl) {
        return new CompanyConfig(id, id, careerUrl, "product", "japan", "");
    }
}
