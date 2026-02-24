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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

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
    private static final int COMPANY_CONTEXT_SCHEMA_VERSION = 1;
    private static final Set<String> CONTEXT_NAVIGATION_KEYWORDS = Set.of(
            "about us",
            "vision",
            "ceo message",
            "company overview",
            "our business",
            "history",
            "press releases",
            "photo library",
            "governance",
            "what's new",
            "links",
            "home"
    );

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
                    CompanyContextDocument contextDocument = buildCompanyContextDocument(
                            company,
                            response,
                            options,
                            cache,
                            lastRequestByHost,
                            robotsRulesByHost,
                            info,
                            errors
                    );
                    if (contextDocument.hasContent()) {
                        writeCompanyContextFile(options.contextOutputDir(), company, contextDocument);
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

    private CompanyContextDocument buildCompanyContextDocument(
            CompanyConfig company,
            CareerPageHttpFetcher.FetchResult careerResponse,
            FetchOptions options,
            CareerPageResponseCache cache,
            Map<String, Long> lastRequestByHost,
            Map<String, Optional<RobotsTxtRules>> robotsRulesByHost,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        ContextAccumulator context = new ContextAccumulator();
        LinkedHashSet<String> fetchedPageUrls = new LinkedHashSet<>();
        collectContextFromPage(careerResponse, context);
        fetchedPageUrls.add(canonicalizeUrl(careerResponse.finalUrl()));

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
            collectContextFromPage(corporateResponse, context);
            fetchedPageUrls.add(canonicalizeUrl(corporateResponse.finalUrl()));
            List<JobPostingExtractor.ContextLink> corporateLinks = extractor.discoverCompanyContextLinks(
                    corporateResponse.body(),
                    corporateResponse.finalUrl(),
                    CONTEXT_DISCOVERY_MAX_URLS
            );
            int linkedPages = 0;
            for (JobPostingExtractor.ContextLink link : corporateLinks) {
                context.addLink(link.title(), link.url());
                if (!isInternalCompanyUrl(company, link.url())) {
                    continue;
                }
                if (linkedPages >= CONTEXT_SUMMARY_MAX_PAGES) {
                    continue;
                }
                String canonicalLinkUrl = canonicalizeUrl(link.url());
                if (!fetchedPageUrls.add(canonicalLinkUrl)) {
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
                collectContextFromPage(linkedContextPage, context);
                linkedPages++;
            }
        }

        return context.toDocument(company);
    }

    private void collectContextFromPage(
            CareerPageHttpFetcher.FetchResult page,
            ContextAccumulator context
    ) {
        if (page == null || page.body() == null || page.body().isBlank()) {
            return;
        }

        JobPostingExtractor.ContextSummary summary = extractor.extractContextSummaryData(page.body(), page.finalUrl());
        context.addSource(page.finalUrl(), summary.pageTitle(), summary.pageDescription());
        for (String item : summary.contexts()) {
            context.addSummary(item);
        }

        List<JobPostingExtractor.ContextLink> links = extractor.discoverCompanyContextLinks(
                page.body(),
                page.finalUrl(),
                CONTEXT_DISCOVERY_MAX_URLS
        );
        for (JobPostingExtractor.ContextLink link : links) {
            context.addLink(link.title(), link.url());
        }
    }

    private boolean isLikelyBoilerplateContext(String text) {
        String normalized = normalizeForDedup(text);
        if (normalized.isBlank() || normalized.length() < 16) {
            return true;
        }
        int matches = 0;
        for (String keyword : CONTEXT_NAVIGATION_KEYWORDS) {
            if (normalized.contains(keyword)) {
                matches++;
            }
        }
        if (matches >= 4) {
            return true;
        }
        return normalized.startsWith("home >") || normalized.startsWith("home>");
    }

    private String normalizeForDedup(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String canonicalizeUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            StringBuilder sb = new StringBuilder();
            if (!scheme.isBlank()) {
                sb.append(scheme).append("://");
            }
            sb.append(host);
            if (port > 0) {
                sb.append(":").append(port);
            }
            sb.append(path);
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return normalized;
        }
    }

    private String dumpYaml(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        options.setWidth(120);
        return new Yaml(options).dump(root);
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

    private void writeCompanyContextFile(
            Path contextOutputDir,
            CompanyConfig company,
            CompanyContextDocument contextDocument
    ) throws IOException {
        if (contextOutputDir == null || contextDocument == null || !contextDocument.hasContent()) {
            return;
        }
        Files.createDirectories(contextOutputDir);
        String slug = sanitizeSlug(company.id());
        Path contextFile = contextOutputDir.resolve(slug + ".yaml");
        Files.writeString(contextFile, dumpYaml(contextDocument.toYamlMap()), StandardCharsets.UTF_8);
        Path legacyTextFile = contextOutputDir.resolve(slug + ".txt");
        if (Files.exists(legacyTextFile)) {
            Files.deleteIfExists(legacyTextFile);
        }
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

    private final class ContextAccumulator {
        private final LinkedHashMap<String, ContextSource> sourcesByUrl = new LinkedHashMap<>();
        private final LinkedHashMap<String, JobPostingExtractor.ContextLink> linksByUrl = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> summariesByKey = new LinkedHashMap<>();

        private void addSource(String url, String pageTitle, String pageDescription) {
            String canonicalUrl = canonicalizeUrl(url);
            if (canonicalUrl.isBlank()) {
                return;
            }
            String normalizedTitle = pageTitle == null ? "" : pageTitle.trim();
            String normalizedDescription = pageDescription == null ? "" : pageDescription.trim();
            if (normalizedTitle.isBlank() && normalizedDescription.isBlank()) {
                return;
            }
            sourcesByUrl.putIfAbsent(canonicalUrl, new ContextSource(
                    canonicalUrl,
                    normalizedTitle,
                    normalizedDescription
            ));
        }

        private void addLink(String title, String url) {
            String canonicalUrl = canonicalizeUrl(url);
            if (canonicalUrl.isBlank()) {
                return;
            }
            String normalizedTitle = title == null ? "" : title.trim();
            if (normalizedTitle.isBlank()) {
                return;
            }
            String dedupeKey = canonicalUrl.toLowerCase(Locale.ROOT);
            linksByUrl.putIfAbsent(dedupeKey, new JobPostingExtractor.ContextLink(normalizedTitle, canonicalUrl));
        }

        private void addSummary(String value) {
            String summary = value == null ? "" : value.trim();
            if (summary.isBlank() || isLikelyBoilerplateContext(summary)) {
                return;
            }
            String dedupeKey = normalizeForDedup(summary);
            if (dedupeKey.isBlank()) {
                return;
            }
            summariesByKey.putIfAbsent(dedupeKey, summary);
        }

        private CompanyContextDocument toDocument(CompanyConfig company) {
            return new CompanyContextDocument(
                    company.id(),
                    company.name(),
                    company.careerUrl(),
                    company.corporateUrl(),
                    List.copyOf(sourcesByUrl.values()),
                    List.copyOf(linksByUrl.values()),
                    List.copyOf(summariesByKey.values()),
                    Instant.now().toString(),
                    COMPANY_CONTEXT_SCHEMA_VERSION
            );
        }
    }

    private record ContextSource(
            String url,
            String pageTitle,
            String pageDescription
    ) {
        Map<String, Object> toYamlMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("url", url == null ? "" : url);
            if (pageTitle != null && !pageTitle.isBlank()) {
                out.put("page_title", pageTitle);
            }
            if (pageDescription != null && !pageDescription.isBlank()) {
                out.put("page_description", pageDescription);
            }
            return out;
        }
    }

    private record CompanyContextDocument(
            String companyId,
            String companyName,
            String careerUrl,
            String corporateUrl,
            List<ContextSource> sources,
            List<JobPostingExtractor.ContextLink> links,
            List<String> summaries,
            String fetchedAt,
            int schemaVersion
    ) {
        boolean hasContent() {
            return !(sources == null || sources.isEmpty())
                    || !(links == null || links.isEmpty())
                    || !(summaries == null || summaries.isEmpty());
        }

        Map<String, Object> toYamlMap() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("company_id", companyId == null ? "" : companyId);
            root.put("company_name", companyName == null ? "" : companyName);
            root.put("career_url", careerUrl == null ? "" : careerUrl);
            root.put("corporate_url", corporateUrl == null ? "" : corporateUrl);

            List<Map<String, Object>> sourceMaps = new ArrayList<>();
            if (sources != null) {
                for (ContextSource source : sources) {
                    sourceMaps.add(source.toYamlMap());
                }
            }
            root.put("sources", sourceMaps);

            List<Map<String, Object>> linkMaps = new ArrayList<>();
            if (links != null) {
                for (JobPostingExtractor.ContextLink link : links) {
                    Map<String, Object> linkMap = new LinkedHashMap<>();
                    linkMap.put("title", link.title() == null ? "" : link.title());
                    linkMap.put("url", link.url() == null ? "" : link.url());
                    linkMaps.add(linkMap);
                }
            }
            root.put("links", linkMaps);

            root.put("summaries", summaries == null ? List.of() : summaries);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("fetched_at", fetchedAt == null ? "" : fetchedAt);
            metadata.put("schema_version", schemaVersion);
            root.put("metadata", metadata);
            return root;
        }
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
