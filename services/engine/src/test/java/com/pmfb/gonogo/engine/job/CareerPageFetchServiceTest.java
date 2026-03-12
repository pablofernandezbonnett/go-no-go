package com.pmfb.gonogo.engine.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
                1,
                1,
                tempDir.resolve("context"),
                true,
                "off",
                tempDir.resolve("cache"),
                720,
                true
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(company("moneyforward", "https://example.com/careers")),
                options
        );

        assertEquals(26, attempts.get());
        assertEquals(1, outcome.selectedCompanies());
        assertEquals(0, outcome.companiesFailed());
        assertEquals(1, outcome.rawFilesGenerated());
        assertTrue(outcome.totalDurationMillis() >= 0);
        assertTrue(outcome.retryCount() >= 2);
        assertTrue(outcome.cacheMissCount() > 0);
        assertTrue(outcome.outgoingRequestCount() > 0);
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
                1,
                1,
                tempDir.resolve("context"),
                true,
                "off",
                cacheDir,
                720,
                true
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(company("mercari", "https://example.com/careers")),
                options
        );

        assertEquals(54, attempts.get());
        assertEquals(0, outcome.companiesFailed());
        assertEquals(1, outcome.rawFilesGenerated());
        assertTrue(outcome.freshCacheHitCount() > 0);
        assertTrue(outcome.cacheMissCount() > 0);
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
                1,
                1,
                tempDir.resolve("context"),
                true,
                "off",
                cacheDir,
                1,
                true
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(company("freee", "https://example.com/careers")),
                options
        );

        assertEquals(38, attempts.get());
        assertEquals(0, outcome.companiesFailed());
        assertEquals(1, outcome.rawFilesGenerated());
        assertTrue(outcome.cacheMissCount() > 0);
        assertTrue(outcome.staleCacheFallbackCount() > 0);
        assertTrue(outcome.retryCount() > 0);
        assertFalse(outcome.errorMessages().isEmpty());
        assertTrue(outcome.informationalMessages().stream().anyMatch(item -> item.contains("Using stale cache")));
    }

    @Test
    void boundsCompanyFetchConcurrencyWithVirtualThreadExecution() throws IOException {
        AtomicInteger activeFetches = new AtomicInteger();
        AtomicInteger maxActiveFetches = new AtomicInteger();
        CareerPageFetcher blockingFetcher = (url, timeout, userAgent) -> {
            int current = activeFetches.incrementAndGet();
            maxActiveFetches.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } finally {
                activeFetches.decrementAndGet();
            }
            return new CareerPageHttpFetcher.FetchResult(
                    200,
                    url,
                    """
                            <html><body><a href="/jobs/backend">Backend Engineer</a></body></html>
                            """
            );
        };

        Path tempDir = Files.createTempDirectory("gonogo-fetch-concurrency-test");
        CareerPageFetchService service = new CareerPageFetchService(blockingFetcher, new JobPostingExtractor());
        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                tempDir.resolve("raw"),
                List.of("first", "second", "third"),
                10,
                10,
                "test-agent",
                0,
                0,
                0,
                2,
                2,
                tempDir.resolve("context"),
                false,
                "off",
                tempDir.resolve("cache"),
                720,
                false
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(
                        company("first", "https://first.example.com/careers"),
                        company("second", "https://second.example.com/careers"),
                        company("third", "https://third.example.com/careers")
                ),
                options
        );

        assertEquals(0, outcome.companiesFailed());
        assertEquals(3, outcome.rawFilesGenerated());
        assertTrue(maxActiveFetches.get() >= 2, "expected company fetches to overlap");
        assertTrue(maxActiveFetches.get() <= 2, "expected max concurrency bound to be respected");
    }

    @Test
    void keepsCompanySummaryMessagesInInputOrderWhenFetchCompletesOutOfOrder() throws IOException {
        CareerPageFetcher staggeredFetcher = (url, timeout, userAgent) -> {
            if (url.contains("slow.example.com")) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            } else if (url.contains("fast.example.com")) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
            return new CareerPageHttpFetcher.FetchResult(
                    200,
                    url,
                    """
                            <html><body><a href="/jobs/backend">Backend Engineer</a></body></html>
                            """
            );
        };

        Path tempDir = Files.createTempDirectory("gonogo-fetch-order-test");
        CareerPageFetchService service = new CareerPageFetchService(staggeredFetcher, new JobPostingExtractor());
        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                tempDir.resolve("raw"),
                List.of("slow", "fast"),
                10,
                10,
                "test-agent",
                0,
                0,
                0,
                2,
                2,
                tempDir.resolve("context"),
                false,
                "off",
                tempDir.resolve("cache"),
                720,
                false
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(
                        company("slow", "https://slow.example.com/careers"),
                        company("fast", "https://fast.example.com/careers")
                ),
                options
        );

        List<String> fetchedMessages = outcome.informationalMessages().stream()
                .filter(message -> message.startsWith("Fetched "))
                .toList();

        assertEquals(
                List.of(
                        "Fetched slow: 1 raw job files generated.",
                        "Fetched fast: 1 raw job files generated."
                ),
                fetchedMessages
        );
    }

    @Test
    void respectsPerHostConcurrencyLimitWhileAllowingOtherHostsInParallel() throws IOException {
        AtomicInteger totalActiveFetches = new AtomicInteger();
        AtomicInteger maxTotalActiveFetches = new AtomicInteger();
        ConcurrentHashMap<String, AtomicInteger> activeByHost = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> maxByHost = new ConcurrentHashMap<>();

        CareerPageFetcher hostAwareFetcher = (url, timeout, userAgent) -> {
            String host = URI.create(url).getHost();
            AtomicInteger activeForHost = activeByHost.computeIfAbsent(host, ignored -> new AtomicInteger());
            AtomicInteger maxForHost = maxByHost.computeIfAbsent(host, ignored -> new AtomicInteger());

            int currentTotal = totalActiveFetches.incrementAndGet();
            int currentHost = activeForHost.incrementAndGet();
            maxTotalActiveFetches.accumulateAndGet(currentTotal, Math::max);
            maxForHost.accumulateAndGet(currentHost, Math::max);
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } finally {
                activeForHost.decrementAndGet();
                totalActiveFetches.decrementAndGet();
            }

            return new CareerPageHttpFetcher.FetchResult(
                    200,
                    url,
                    """
                            <html><body><a href="/jobs/backend">Backend Engineer</a></body></html>
                            """
            );
        };

        Path tempDir = Files.createTempDirectory("gonogo-fetch-per-host-test");
        CareerPageFetchService service = new CareerPageFetchService(hostAwareFetcher, new JobPostingExtractor());
        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                tempDir.resolve("raw"),
                List.of("shared_a", "shared_b", "other"),
                10,
                10,
                "test-agent",
                0,
                0,
                0,
                3,
                1,
                tempDir.resolve("context"),
                false,
                "off",
                tempDir.resolve("cache"),
                720,
                false
        );

        CareerPageFetchService.FetchOutcome outcome = service.fetchToRawFiles(
                List.of(
                        company("shared_a", "https://shared.example.com/a"),
                        company("shared_b", "https://shared.example.com/b"),
                        company("other", "https://other.example.com/careers")
                ),
                options
        );

        assertEquals(0, outcome.companiesFailed());
        assertEquals(3, outcome.rawFilesGenerated());
        assertTrue(maxTotalActiveFetches.get() >= 2, "expected other hosts to still overlap");
        assertEquals(1, maxByHost.get("shared.example.com").get());
    }

    private CompanyConfig company(String id, String careerUrl) {
        return new CompanyConfig(id, id, careerUrl, "product", "japan", "");
    }
}
