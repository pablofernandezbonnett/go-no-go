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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CareerPageFetchService {
    private static final int JOB_BOARD_DISCOVERY_MAX_URLS = 3;
    private static final int CONTEXT_DISCOVERY_MAX_URLS = 6;
    private static final int CONTEXT_SUMMARY_MAX_PAGES = 3;
    private static final Path DEFAULT_CONTEXT_OUTPUT_DIR = Path.of("output/company-context");
    private static final int DEFAULT_RETRIES = 2;
    private static final long DEFAULT_BACKOFF_MILLIS = 300;
    private static final long DEFAULT_CACHE_TTL_MINUTES = 720;
    private static final long DEFAULT_REQUEST_DELAY_MILLIS = 1200;
    private static final Path DEFAULT_CACHE_DIR = Path.of(".cache/fetch-web");
    private static final long MAX_BACKOFF_MILLIS = 10_000;
    private static final String ROBOTS_MODE_STRICT = "strict";
    private static final String ROBOTS_MODE_WARN = "warn";
    private static final String ROBOTS_MODE_OFF = "off";

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
            return new FetchOutcome(0, 0, 0, 0, List.of(), List.of());
        }

        int totalFiles = 0;
        int contextFiles = 0;
        int companyFailures = 0;
        List<String> info = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Long> lastRequestByHost = new HashMap<>();
        Map<String, Optional<RobotsTxtRules>> robotsRulesByHost = new HashMap<>();
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
                        robotsRulesByHost,
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

                if (options.contextEnabled()) {
                    String contextText = buildCompanyContextText(
                            company,
                            response,
                            options,
                            cache,
                            lastRequestByHost,
                            robotsRulesByHost,
                            info,
                            errors
                    );
                    if (!contextText.isBlank()) {
                        writeCompanyContextFile(options.contextOutputDir(), company, contextText);
                        contextFiles++;
                    }
                }

                List<JobPostingCandidate> jobs = extractor.extract(
                        response.body(),
                        response.finalUrl(),
                        Math.max(1, options.maxJobsPerCompany())
                );
                if (jobs.isEmpty()) {
                    jobs = extractFromDiscoveredJobBoardLinks(
                            company,
                            response,
                            options,
                            cache,
                            lastRequestByHost,
                            robotsRulesByHost,
                            info,
                            errors
                    );
                    if (jobs.isEmpty()) {
                        info.add("No job candidates detected for " + company.id() + ".");
                        continue;
                    }
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
                contextFiles,
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
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        return chooseResponseForUrl(
                company.id(),
                company.careerUrl(),
                options,
                cachedResponse,
                cache,
                lastRequestByHost,
                robotsRulesByHost,
                info,
                errors
        );
    }

    private CareerPageHttpFetcher.FetchResult chooseResponseForUrl(
            String companyId,
            String url,
            FetchOptions options,
            Optional<CareerPageResponseCache.CachedFetchResult> cachedResponse,
            CareerPageResponseCache cache,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        if (!isAllowedByRobots(url, options, lastRequestByHost, robotsRulesByHost, info, errors, companyId)) {
            return null;
        }
        if (cachedResponse.isPresent() && isFresh(cachedResponse.get(), options.cacheTtlMinutes())) {
            info.add("Using fresh cache for " + companyId + ".");
            return cachedResponse.get().response();
        }

        FetchAttemptOutcome attempt = fetchWithRetry(url, options, lastRequestByHost);
        if (attempt.response() != null && attempt.response().statusCode() < 400) {
            if (attempt.retriesUsed() > 0) {
                info.add("Recovered " + companyId + " after " + attempt.retriesUsed() + " retry attempt(s).");
            }
            writeCacheSafely(cache, url, attempt.response(), info);
            return attempt.response();
        }

        if (cachedResponse.isPresent()) {
            info.add("Using stale cache for " + companyId + " after fetch failure.");
            if (attempt.errorMessage() != null && !attempt.errorMessage().isBlank()) {
                errors.add("Fetch warning for " + companyId + ": " + attempt.errorMessage());
            }
            return cachedResponse.get().response();
        }

        if (attempt.response() != null) {
            return attempt.response();
        }

        String failureMessage = attempt.errorMessage() == null
                ? "Unknown fetch failure."
                : attempt.errorMessage();
        errors.add("I/O failure for " + companyId + ": " + failureMessage);
        return null;
    }

    private List<JobPostingCandidate> extractFromDiscoveredJobBoardLinks(
            CompanyConfig company,
            CareerPageHttpFetcher.FetchResult careerPageResponse,
            FetchOptions options,
            CareerPageResponseCache cache,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        int maxItems = Math.max(1, options.maxJobsPerCompany());
        List<String> discoveredUrls = extractor.discoverJobBoardUrls(
                careerPageResponse.body(),
                careerPageResponse.finalUrl(),
                JOB_BOARD_DISCOVERY_MAX_URLS
        );
        if (discoveredUrls.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, JobPostingCandidate> collected = new LinkedHashMap<>();
        for (String discoveredUrl : discoveredUrls) {
            if (collected.size() >= maxItems) {
                break;
            }
            CareerPageHttpFetcher.FetchResult page = chooseResponseForUrl(
                    company.id(),
                    discoveredUrl,
                    options,
                    readFromCache(cache, discoveredUrl),
                    cache,
                    lastRequestByHost,
                    robotsRulesByHost,
                    info,
                    errors
            );
            if (page == null || page.statusCode() >= 400) {
                continue;
            }

            List<JobPostingCandidate> extracted = extractor.extract(
                    page.body(),
                    page.finalUrl(),
                    maxItems - collected.size()
            );
            for (JobPostingCandidate candidate : extracted) {
                if (collected.size() >= maxItems) {
                    break;
                }
                String key = normalizeKey(candidate);
                collected.putIfAbsent(key, candidate);
            }
        }

        if (!collected.isEmpty()) {
            info.add(
                    "Discovered additional career links for "
                            + company.id()
                            + " and extracted "
                            + collected.size()
                            + " job candidate(s)."
            );
        }
        return List.copyOf(collected.values());
    }

    private String normalizeKey(JobPostingCandidate candidate) {
        String title = candidate.title() == null ? "" : candidate.title().trim().toLowerCase(Locale.ROOT);
        String url = candidate.url() == null ? "" : candidate.url().trim().toLowerCase(Locale.ROOT);
        return title + "||" + url;
    }

    private String buildCompanyContextText(
            CompanyConfig company,
            CareerPageHttpFetcher.FetchResult careerResponse,
            FetchOptions options,
            CareerPageResponseCache cache,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        lines.add("company_id: " + company.id());
        lines.add("company_name: " + company.name());
        lines.add("career_url: " + company.careerUrl());
        lines.add("corporate_url: " + company.corporateUrl());

        String careerSummary = extractor.extractContextSummary(careerResponse.body(), careerResponse.finalUrl());
        if (!careerSummary.isBlank()) {
            lines.add(careerSummary);
        }
        List<JobPostingExtractor.ContextLink> contextLinks = extractor.discoverCompanyContextLinks(
                careerResponse.body(),
                careerResponse.finalUrl(),
                CONTEXT_DISCOVERY_MAX_URLS
        );
        for (JobPostingExtractor.ContextLink link : contextLinks) {
            lines.add("context_link: " + link.title() + " -> " + link.url());
        }

        CareerPageHttpFetcher.FetchResult corporateResponse = fetchCorporateContextPage(
                company,
                options,
                cache,
                lastRequestByHost,
                robotsRulesByHost,
                info,
                errors
        );
        if (corporateResponse != null) {
            String corporateSummary = extractor.extractContextSummary(corporateResponse.body(), corporateResponse.finalUrl());
            if (!corporateSummary.isBlank()) {
                lines.add(corporateSummary);
            }
            List<JobPostingExtractor.ContextLink> corporateLinks = extractor.discoverCompanyContextLinks(
                    corporateResponse.body(),
                    corporateResponse.finalUrl(),
                    CONTEXT_DISCOVERY_MAX_URLS
            );
            int linkedPages = 0;
            for (JobPostingExtractor.ContextLink link : corporateLinks) {
                lines.add("context_link: " + link.title() + " -> " + link.url());
                if (!isInternalCompanyUrl(company, link.url())) {
                    continue;
                }
                if (linkedPages >= CONTEXT_SUMMARY_MAX_PAGES) {
                    continue;
                }
                CareerPageHttpFetcher.FetchResult linkedContextPage = chooseResponseForUrl(
                        company.id(),
                        link.url(),
                        options,
                        readFromCache(cache, link.url()),
                        cache,
                        lastRequestByHost,
                        robotsRulesByHost,
                        info,
                        errors
                );
                if (linkedContextPage == null || linkedContextPage.statusCode() >= 400) {
                    continue;
                }
                String linkedSummary = extractor.extractContextSummary(
                        linkedContextPage.body(),
                        linkedContextPage.finalUrl()
                );
                if (!linkedSummary.isBlank()) {
                    lines.add(linkedSummary);
                    linkedPages++;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean isInternalCompanyUrl(CompanyConfig company, String targetUrl) {
        String targetHost = hostKey(targetUrl);
        String careerHost = hostKey(company.careerUrl());
        String corporateHost = hostKey(company.corporateUrl());
        return isSameDomainOrSubdomain(targetHost, careerHost)
                || isSameDomainOrSubdomain(targetHost, corporateHost);
    }

    private boolean isSameDomainOrSubdomain(String candidateHost, String baseHost) {
        if (candidateHost == null || candidateHost.isBlank() || baseHost == null || baseHost.isBlank()) {
            return false;
        }
        String normalizedCandidate = candidateHost.toLowerCase(Locale.ROOT);
        String normalizedBase = baseHost.toLowerCase(Locale.ROOT);
        return normalizedCandidate.equals(normalizedBase)
                || normalizedCandidate.endsWith("." + normalizedBase);
    }

    private CareerPageHttpFetcher.FetchResult fetchCorporateContextPage(
            CompanyConfig company,
            FetchOptions options,
            CareerPageResponseCache cache,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        if (company.corporateUrl() == null || company.corporateUrl().isBlank()) {
            return null;
        }
        if (normalize(company.careerUrl()).equals(normalize(company.corporateUrl()))) {
            return null;
        }
        CareerPageHttpFetcher.FetchResult corporateResponse = chooseResponseForUrl(
                company.id(),
                company.corporateUrl(),
                options,
                readFromCache(cache, company.corporateUrl()),
                cache,
                lastRequestByHost,
                robotsRulesByHost,
                info,
                errors
        );
        if (corporateResponse != null && corporateResponse.statusCode() < 400) {
            return corporateResponse;
        }
        return null;
    }

    private void writeCompanyContextFile(Path contextOutputDir, CompanyConfig company, String contextText) throws IOException {
        if (contextOutputDir == null || contextText == null || contextText.isBlank()) {
            return;
        }
        Files.createDirectories(contextOutputDir);
        Path contextFile = contextOutputDir.resolve(sanitizeSlug(company.id()) + ".txt");
        Files.writeString(contextFile, contextText + "\n", StandardCharsets.UTF_8);
    }

    private boolean isAllowedByRobots(
            String url,
            FetchOptions options,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors,
            String companyId
    ) throws InterruptedException {
        String mode = normalizeRobotsMode(options.robotsMode());
        if (ROBOTS_MODE_OFF.equals(mode)) {
            return true;
        }

        Optional<RobotsTxtRules> rulesOptional = loadRobotsRules(
                url,
                options,
                lastRequestByHost,
                robotsRulesByHost,
                info
        );
        if (rulesOptional.isEmpty()) {
            return true;
        }
        boolean allowed = rulesOptional.get().isAllowed(url, options.userAgent());
        if (allowed) {
            return true;
        }
        if (ROBOTS_MODE_WARN.equals(mode)) {
            info.add("robots_warning: " + companyId + " URL may be disallowed by robots.txt, continuing due to warn mode: " + url);
            return true;
        }

        errors.add("Blocked by robots.txt for " + companyId + ": " + url);
        return false;
    }

    private Optional<RobotsTxtRules> loadRobotsRules(
            String targetUrl,
            FetchOptions options,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info
    ) throws InterruptedException {
        String host = hostKey(targetUrl);
        Optional<RobotsTxtRules> cached = robotsRulesByHost.get(host);
        if (cached != null) {
            return cached;
        }

        String robotsUrl = buildRobotsUrl(targetUrl);
        if (robotsUrl.isBlank()) {
            Optional<RobotsTxtRules> empty = Optional.empty();
            robotsRulesByHost.put(host, empty);
            return empty;
        }

        try {
            applyRequestDelay(robotsUrl, options.requestDelayMillis(), lastRequestByHost);
            CareerPageHttpFetcher.FetchResult robotsResponse = fetcher.fetch(
                    robotsUrl,
                    Duration.ofSeconds(Math.max(5, options.timeoutSeconds())),
                    options.userAgent()
            );
            if (robotsResponse.statusCode() >= 400) {
                Optional<RobotsTxtRules> empty = Optional.empty();
                robotsRulesByHost.put(host, empty);
                return empty;
            }
            Optional<RobotsTxtRules> parsed = Optional.of(RobotsTxtRules.parse(robotsResponse.body()));
            robotsRulesByHost.put(host, parsed);
            return parsed;
        } catch (IOException e) {
            info.add("robots_warning: failed to read robots.txt for " + host + ": " + e.getMessage());
            Optional<RobotsTxtRules> empty = Optional.empty();
            robotsRulesByHost.put(host, empty);
            return empty;
        }
    }

    private String buildRobotsUrl(String targetUrl) {
        try {
            URI uri = URI.create(targetUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isBlank()) {
                return "";
            }
            int port = uri.getPort();
            if (port > 0) {
                return scheme + "://" + host + ":" + port + "/robots.txt";
            }
            return scheme + "://" + host + "/robots.txt";
        } catch (IllegalArgumentException e) {
            return "";
        }
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

    private static String normalizeRobotsMode(String robotsMode) {
        String normalized = robotsMode == null ? ROBOTS_MODE_STRICT : robotsMode.trim().toLowerCase(Locale.ROOT);
        if (ROBOTS_MODE_WARN.equals(normalized) || ROBOTS_MODE_OFF.equals(normalized)) {
            return normalized;
        }
        return ROBOTS_MODE_STRICT;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
            Path contextOutputDir,
            boolean contextEnabled,
            String robotsMode,
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
                    DEFAULT_CONTEXT_OUTPUT_DIR,
                    true,
                    ROBOTS_MODE_STRICT,
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
            contextOutputDir = contextOutputDir == null ? DEFAULT_CONTEXT_OUTPUT_DIR : contextOutputDir;
            robotsMode = normalizeRobotsMode(robotsMode);
            cacheDir = cacheDir == null ? DEFAULT_CACHE_DIR : cacheDir;
            cacheTtlMinutes = Math.max(1, cacheTtlMinutes);
        }
    }

    public record FetchOutcome(
            int selectedCompanies,
            int companiesFailed,
            int rawFilesGenerated,
            int contextFilesGenerated,
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
