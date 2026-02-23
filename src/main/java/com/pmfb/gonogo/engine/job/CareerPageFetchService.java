package com.pmfb.gonogo.engine.job;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CareerPageFetchService {
    private static final int DEFAULT_RETRIES = 2;
    private static final long DEFAULT_BACKOFF_MILLIS = 300;
    private static final long DEFAULT_CACHE_TTL_MINUTES = 720;
    private static final long DEFAULT_REQUEST_DELAY_MILLIS = 1200;
    private static final Path DEFAULT_CACHE_DIR = Path.of(".cache/fetch-web");
    private static final long MAX_BACKOFF_MILLIS = 10_000;

    private final CareerPageFetcher fetcher;
    private final JobPostingExtractor extractor;

    public CareerPageFetchService() {
        this(new CareerPageHttpFetcher(), new JobPostingExtractor());
    }

    CareerPageFetchService(CareerPageFetcher fetcher, JobPostingExtractor extractor) {
        this.fetcher = fetcher;
        this.extractor = extractor;
    }

    public FetchOutcome fetchToRawFiles(List<CompanyConfig> companies, FetchOptions options) {
        List<CompanyConfig> selectedCompanies = selectCompanies(companies, options.companyIds());
        if (selectedCompanies.isEmpty()) {
            return new FetchOutcome(0, 0, 0, List.of(), List.of());
        }

        int totalFiles = 0;
        int companyFailures = 0;
        List<String> info = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Long> lastRequestByHost = new HashMap<>();
        CareerPageResponseCache cache = options.cacheEnabled()
                ? new CareerPageResponseCache(options.cacheDir())
                : null;

        for (CompanyConfig company : selectedCompanies) {
            try {
                Optional<CareerPageResponseCache.CachedFetchResult> cachedResponse = readFromCache(cache, company.careerUrl());
                CareerPageHttpFetcher.FetchResult response = chooseResponse(
                        company,
                        options,
                        cachedResponse,
                        cache,
                        lastRequestByHost,
                        info,
                        errors
                );
                if (response == null) {
                    companyFailures++;
                    continue;
                }

                if (response.statusCode() >= 400) {
                    companyFailures++;
                    errors.add(
                            "Failed fetch for " + company.id() + " (" + company.careerUrl() + "): HTTP " + response.statusCode()
                    );
                    continue;
                }

                List<JobPostingCandidate> jobs = extractor.extract(
                        response.body(),
                        response.finalUrl(),
                        Math.max(1, options.maxJobsPerCompany())
                );
                if (jobs.isEmpty()) {
                    info.add("No job candidates detected for " + company.id() + ".");
                    continue;
                }

                int written = writeCompanyRawFiles(options.outputDir(), company, jobs);
                totalFiles += written;
                info.add("Fetched " + company.id() + ": " + written + " raw job files generated.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                companyFailures++;
                errors.add("Fetch interrupted for " + company.id() + ".");
                break;
            } catch (IOException e) {
                companyFailures++;
                errors.add("I/O failure for " + company.id() + ": " + e.getMessage());
            } catch (RuntimeException e) {
                companyFailures++;
                errors.add("Unexpected failure for " + company.id() + ": " + e.getMessage());
            }
        }

        return new FetchOutcome(
                selectedCompanies.size(),
                companyFailures,
                totalFiles,
                List.copyOf(info),
                List.copyOf(errors)
        );
    }

    private CareerPageHttpFetcher.FetchResult chooseResponse(
            CompanyConfig company,
            FetchOptions options,
            Optional<CareerPageResponseCache.CachedFetchResult> cachedResponse,
            CareerPageResponseCache cache,
            Map<String, Long> lastRequestByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        if (cachedResponse.isPresent() && isFresh(cachedResponse.get(), options.cacheTtlMinutes())) {
            info.add("Using fresh cache for " + company.id() + ".");
            return cachedResponse.get().response();
        }

        FetchAttemptOutcome attempt = fetchWithRetry(company.careerUrl(), options, lastRequestByHost);
        if (attempt.response() != null && attempt.response().statusCode() < 400) {
            if (attempt.retriesUsed() > 0) {
                info.add("Recovered " + company.id() + " after " + attempt.retriesUsed() + " retry attempt(s).");
            }
            writeCacheSafely(cache, company.careerUrl(), attempt.response(), info);
            return attempt.response();
        }

        if (cachedResponse.isPresent()) {
            info.add("Using stale cache for " + company.id() + " after fetch failure.");
            if (attempt.errorMessage() != null && !attempt.errorMessage().isBlank()) {
                errors.add("Fetch warning for " + company.id() + ": " + attempt.errorMessage());
            }
            return cachedResponse.get().response();
        }

        if (attempt.response() != null) {
            return attempt.response();
        }

        String failureMessage = attempt.errorMessage() == null
                ? "Unknown fetch failure."
                : attempt.errorMessage();
        errors.add("I/O failure for " + company.id() + ": " + failureMessage);
        return null;
    }

    private FetchAttemptOutcome fetchWithRetry(
            String url,
            FetchOptions options,
            Map<String, Long> lastRequestByHost
    ) throws InterruptedException {
        int maxAttempts = Math.max(1, options.retries() + 1);
        int retriesUsed = 0;
        String lastError = null;

        for (int attemptIndex = 0; attemptIndex < maxAttempts; attemptIndex++) {
            try {
                applyRequestDelay(url, options.requestDelayMillis(), lastRequestByHost);
                CareerPageHttpFetcher.FetchResult response = fetcher.fetch(
                        url,
                        Duration.ofSeconds(Math.max(5, options.timeoutSeconds())),
                        options.userAgent()
                );
                if (isRetryableStatus(response.statusCode()) && attemptIndex < maxAttempts - 1) {
                    retriesUsed++;
                    sleepWithBackoff(attemptIndex, options.backoffMillis());
                    continue;
                }
                return new FetchAttemptOutcome(response, lastError, retriesUsed);
            } catch (IOException e) {
                lastError = e.getMessage();
                if (attemptIndex >= maxAttempts - 1) {
                    break;
                }
                retriesUsed++;
                sleepWithBackoff(attemptIndex, options.backoffMillis());
            }
        }
        return new FetchAttemptOutcome(null, lastError, retriesUsed);
    }

    private void applyRequestDelay(
            String url,
            long requestDelayMillis,
            Map<String, Long> lastRequestByHost
    ) throws InterruptedException {
        long normalizedDelayMillis = Math.max(0, requestDelayMillis);
        if (normalizedDelayMillis == 0) {
            return;
        }

        String hostKey = hostKey(url);
        long now = Instant.now().toEpochMilli();
        Long lastRequestAt = lastRequestByHost.get(hostKey);
        if (lastRequestAt != null) {
            long elapsed = now - lastRequestAt;
            long waitMillis = normalizedDelayMillis - elapsed;
            if (waitMillis > 0) {
                Thread.sleep(waitMillis);
            }
        }
        lastRequestByHost.put(hostKey, Instant.now().toEpochMilli());
    }

    private String hostKey(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.toLowerCase(Locale.ROOT);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return url.toLowerCase(Locale.ROOT);
    }

    private Optional<CareerPageResponseCache.CachedFetchResult> readFromCache(CareerPageResponseCache cache, String url) {
        if (cache == null) {
            return Optional.empty();
        }
        return cache.read(url);
    }

    private void writeCacheSafely(
            CareerPageResponseCache cache,
            String url,
            CareerPageHttpFetcher.FetchResult response,
            List<String> info
    ) {
        if (cache == null) {
            return;
        }
        try {
            cache.write(url, response);
        } catch (IOException e) {
            info.add("Cache write skipped (" + e.getMessage() + ").");
        }
    }

    private boolean isFresh(CareerPageResponseCache.CachedFetchResult cached, long ttlMinutes) {
        long ageMillis = Instant.now().toEpochMilli() - cached.fetchedAtEpochMillis();
        long ttlMillis = Math.max(1, ttlMinutes) * 60_000L;
        return ageMillis <= ttlMillis;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void sleepWithBackoff(int attemptIndex, long baseBackoffMillis) throws InterruptedException {
        long normalizedBase = Math.max(0, baseBackoffMillis);
        if (normalizedBase == 0) {
            return;
        }
        long multiplier = 1L << Math.min(attemptIndex, 10);
        long waitMillis = Math.min(MAX_BACKOFF_MILLIS, normalizedBase * multiplier);
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    private List<CompanyConfig> selectCompanies(List<CompanyConfig> companies, List<String> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return companies;
        }

        Set<String> requested = new LinkedHashSet<>();
        for (String id : companyIds) {
            if (id != null && !id.isBlank()) {
                requested.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<CompanyConfig> selected = new ArrayList<>();
        for (CompanyConfig company : companies) {
            if (requested.contains(company.id().toLowerCase(Locale.ROOT))) {
                selected.add(company);
            }
        }
        return selected;
    }

    private int writeCompanyRawFiles(Path outputDir, CompanyConfig company, List<JobPostingCandidate> jobs) throws IOException {
        Path companyDir = outputDir.resolve(sanitizeSlug(company.id()));
        Files.createDirectories(companyDir);
        int count = 0;
        for (int i = 0; i < jobs.size(); i++) {
            JobPostingCandidate job = jobs.get(i);
            String fileName = String.format("%02d-%s.txt", i + 1, sanitizeSlug(job.title()));
            Path outputFile = companyDir.resolve(fileName);
            Files.writeString(outputFile, renderRawJobText(company, job), StandardCharsets.UTF_8);
            count++;
        }
        return count;
    }

    private String renderRawJobText(CompanyConfig company, JobPostingCandidate job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Company: ").append(company.name()).append("\n");
        sb.append("Company ID: ").append(company.id()).append("\n");
        sb.append("Title: ").append(job.title()).append("\n");
        if (!job.url().isBlank()) {
            sb.append("Source URL: ").append(job.url()).append("\n");
        } else {
            sb.append("Source URL: ").append(company.careerUrl()).append("\n");
        }
        sb.append("Fetched At: ").append(Instant.now()).append("\n");
        sb.append("Location: Unspecified\n");
        sb.append("Salary: TBD\n");
        sb.append("Description:\n");
        sb.append(job.snippet()).append("\n");
        return sb.toString();
    }

    private String sanitizeSlug(String input) {
        String normalized = input == null ? "unknown" : input.trim().toLowerCase(Locale.ROOT);
        String slug = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "unknown" : slug;
    }

    public record FetchOptions(
            Path outputDir,
            List<String> companyIds,
            int maxJobsPerCompany,
            int timeoutSeconds,
            String userAgent,
            int retries,
            long backoffMillis,
            long requestDelayMillis,
            Path cacheDir,
            long cacheTtlMinutes,
            boolean cacheEnabled
    ) {
        public FetchOptions(Path outputDir, List<String> companyIds, int maxJobsPerCompany, int timeoutSeconds, String userAgent) {
            this(
                    outputDir,
                    companyIds,
                    maxJobsPerCompany,
                    timeoutSeconds,
                    userAgent,
                    DEFAULT_RETRIES,
                    DEFAULT_BACKOFF_MILLIS,
                    DEFAULT_REQUEST_DELAY_MILLIS,
                    DEFAULT_CACHE_DIR,
                    DEFAULT_CACHE_TTL_MINUTES,
                    true
            );
        }

        public FetchOptions {
            outputDir = outputDir == null ? Path.of("output/raw") : outputDir;
            companyIds = companyIds == null ? List.of() : List.copyOf(companyIds);
            maxJobsPerCompany = Math.max(1, maxJobsPerCompany);
            timeoutSeconds = Math.max(5, timeoutSeconds);
            userAgent = (userAgent == null || userAgent.isBlank())
                    ? "go-no-go-engine/0.1 (+https://local)"
                    : userAgent;
            retries = Math.max(0, retries);
            backoffMillis = Math.max(0, backoffMillis);
            requestDelayMillis = Math.max(0, requestDelayMillis);
            cacheDir = cacheDir == null ? DEFAULT_CACHE_DIR : cacheDir;
            cacheTtlMinutes = Math.max(1, cacheTtlMinutes);
        }
    }

    public record FetchOutcome(
            int selectedCompanies,
            int companiesFailed,
            int rawFilesGenerated,
            List<String> informationalMessages,
            List<String> errorMessages
    ) {
        public boolean allSelectedCompaniesFailed() {
            return selectedCompanies > 0 && companiesFailed == selectedCompanies;
        }
    }

    private record FetchAttemptOutcome(
            CareerPageHttpFetcher.FetchResult response,
            String errorMessage,
            int retriesUsed
    ) {
    }
}
