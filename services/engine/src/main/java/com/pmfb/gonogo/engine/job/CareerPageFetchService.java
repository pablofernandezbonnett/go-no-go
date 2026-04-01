package com.pmfb.gonogo.engine.job;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.FetchWebRuntimeConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public final class CareerPageFetchService {
    private static final int CONTEXT_DISCOVERY_MAX_URLS = 6;
    private static final int CONTEXT_SUMMARY_MAX_PAGES = 3;
    private static final Path DEFAULT_CONTEXT_OUTPUT_DIR = Path.of("output/company-context");
    private static final Path DEFAULT_CACHE_DIR = Path.of(".cache/fetch-web");
    private static final long MAX_BACKOFF_MILLIS = 10_000;
    private static final String ROBOTS_MODE_STRICT = "strict";
    private static final String ROBOTS_MODE_WARN = "warn";
    private static final String ROBOTS_MODE_OFF = "off";
    private static final int COMPANY_CONTEXT_SCHEMA_VERSION = 1;
    private static final int CAREER_URL_RESOLUTION_MAX_CANDIDATES = 24;
    private static final int CAREER_URL_RESOLUTION_MAX_DISCOVERED_URLS = 4;
    private static final Path DEFAULT_RESOLUTION_STATE_FILE = Path.of("output/fetch-web-resolution.yaml");
    private static final int RESOLUTION_STATE_SCHEMA_VERSION = 1;
    private static final List<String> CAREER_PATH_CANDIDATES = List.of(
            "/careers",
            "/careers/en",
            "/careers/jobs",
            "/careers/en/mid-career",
            "/careers/en/mid-career/dx",
            "/careers/en/job-description",
            "/jobs",
            "/jobs/openings",
            "/recruit",
            "/recruitment",
            "/employment",
            "/join-us",
            "/open-positions",
            "/positions"
    );
    private static final Set<String> ATS_PROVIDER_HINTS = Set.of(
            "ashbyhq.com",
            "greenhouse.io",
            "lever.co",
            "myworkdayjobs.com",
            "smartrecruiters.com",
            "workable.com",
            "bamboohr.com"
    );
    private static final Set<String> NON_JOB_PATH_HINTS = Set.of(
            "/about",
            "/company",
            "/mission",
            "/vision",
            "/values",
            "/sustainability",
            "/news",
            "/press",
            "/ir",
            "/investor",
            "/blog"
    );
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

    public CareerPageFetchService(CareerPageFetcher fetcher, JobPostingExtractor extractor) {
        this.fetcher = fetcher;
        this.extractor = extractor;
    }

    public FetchOutcome fetchToRawFiles(List<CompanyConfig> companies, FetchOptions options) {
        List<CompanyConfig> selectedCompanies = selectCompanies(companies, options.companyIds());
        if (selectedCompanies.isEmpty()) {
            return new FetchOutcome(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
        }

        long startedAtMillis = Instant.now().toEpochMilli();
        List<String> info = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CareerPageResponseCache cache = options.cacheEnabled()
                ? new CareerPageResponseCache(options.cacheDir())
                : null;
        CareerUrlResolutionStore resolutionStore = CareerUrlResolutionStore.load(DEFAULT_RESOLUTION_STATE_FILE, info);
        FetchRuntimeState runtimeState = new FetchRuntimeState();
        List<CompanyFetchWorkItem> workItems = new ArrayList<>();
        for (int i = 0; i < selectedCompanies.size(); i++) {
            CompanyConfig company = selectedCompanies.get(i);
            workItems.add(new CompanyFetchWorkItem(i, company, resolutionStore.find(company.id())));
        }

        List<CompanyFetchResult> companyResults;
        try {
            companyResults = fetchCompanies(workItems, options, cache, runtimeState);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Fetch interrupted before completion.");
            return new FetchOutcome(
                    selectedCompanies.size(),
                    selectedCompanies.size(),
                    0,
                    0,
                    Instant.now().toEpochMilli() - startedAtMillis,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.copyOf(info),
                    List.copyOf(errors)
            );
        }

        int totalFiles = 0;
        int contextFiles = 0;
        int companyFailures = 0;
        FetchStatsTracker stats = new FetchStatsTracker();

        for (CompanyFetchResult result : companyResults) {
            totalFiles += result.rawFilesGenerated();
            contextFiles += result.contextFilesGenerated();
            if (result.failed()) {
                companyFailures++;
            }
            stats.addAll(result.stats());
            info.addAll(result.informationalMessages());
            errors.addAll(result.errorMessages());
            result.resolutionEntry().ifPresent(resolutionStore::remember);
        }

        resolutionStore.save(DEFAULT_RESOLUTION_STATE_FILE, info);

        return new FetchOutcome(
                selectedCompanies.size(),
                companyFailures,
                totalFiles,
                contextFiles,
                Instant.now().toEpochMilli() - startedAtMillis,
                stats.freshCacheHits(),
                stats.cacheMisses(),
                stats.staleCacheFallbacks(),
                stats.retriesUsed(),
                stats.outgoingRequests(),
                List.copyOf(info),
                List.copyOf(errors)
        );
    }

    private String companyFetchTimingMessage(
            String companyId,
            long companyStartedAtMillis,
            int companyRawFilesGenerated,
            int companyContextFilesGenerated,
            String companyStatus
    ) {
        long durationMillis = Math.max(0, Instant.now().toEpochMilli() - companyStartedAtMillis);
        return "company_fetch_timing: "
                + companyId
                + " duration_ms="
                + durationMillis
                + " raw_files="
                + companyRawFilesGenerated
                + " context_files="
                + companyContextFilesGenerated
                + " status="
                + companyStatus;
    }

    private List<CompanyFetchResult> fetchCompanies(
            List<CompanyFetchWorkItem> workItems,
            FetchOptions options,
            CareerPageResponseCache cache,
            FetchRuntimeState runtimeState
    ) throws InterruptedException {
        if (workItems.isEmpty()) {
            return List.of();
        }

        int effectiveConcurrency = Math.min(workItems.size(), Math.max(1, options.maxConcurrency()));
        if (effectiveConcurrency <= 1) {
            List<CompanyFetchResult> results = new ArrayList<>();
            for (CompanyFetchWorkItem workItem : workItems) {
                results.add(fetchCompany(workItem, options, cache, runtimeState));
            }
            return List.copyOf(results);
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(
                effectiveConcurrency,
                Thread.ofVirtual().name("fetch-web-company-", 0).factory()
        )) {
            List<Future<CompanyFetchResult>> futures = new ArrayList<>();
            for (CompanyFetchWorkItem workItem : workItems) {
                futures.add(executor.submit(() -> fetchCompany(workItem, options, cache, runtimeState)));
            }

            List<CompanyFetchResult> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Future<CompanyFetchResult> future = futures.get(i);
                CompanyFetchWorkItem workItem = workItems.get(i);
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    results.add(unexpectedFetchFailure(workItem.company(), e.getCause()));
                }
            }
            return List.copyOf(results);
        }
    }

    private CompanyFetchResult fetchCompany(
            CompanyFetchWorkItem workItem,
            FetchOptions options,
            CareerPageResponseCache cache,
            FetchRuntimeState runtimeState
    ) {
        CompanyConfig company = workItem.company();
        long companyStartedAtMillis = Instant.now().toEpochMilli();
        int companyRawFilesGenerated = 0;
        int companyContextFilesGenerated = 0;
        String companyStatus = "ok";
        boolean failed = false;
        List<String> info = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        FetchStatsTracker stats = new FetchStatsTracker();
        Optional<CareerUrlResolutionEntry> resolutionEntry = Optional.empty();

        try {
            Optional<CareerPageResponseCache.CachedFetchResult> cachedResponse = readFromCache(cache, company.careerUrl());
            CareerPageHttpFetcher.FetchResult configuredResponse = chooseResponse(
                    company,
                    options,
                    cachedResponse,
                    cache,
                    runtimeState,
                    stats,
                    info,
                    errors
            );
            CareerResolutionResult resolution = resolveCareerPageForJobs(
                    company,
                    configuredResponse,
                    workItem.previousResolution(),
                    options,
                    cache,
                    runtimeState,
                    stats,
                    info,
                    errors
            );

            if (!resolution.reachable()) {
                failed = true;
                companyStatus = "failed";
                errors.add(
                        "Failed fetch for " + company.id()
                                + " (" + company.careerUrl()
                                + "): no reachable career page candidate."
                );
            } else {
                if (resolution.hasResolvedUrl() && !resolution.jobs().isEmpty()) {
                    resolutionEntry = Optional.of(new CareerUrlResolutionEntry(
                            company.id(),
                            company.careerUrl(),
                            resolution.resolvedCareerUrl(),
                            resolution.confidence(),
                            resolution.jobs().size(),
                            Instant.now().toString()
                    ));
                }

                if (options.contextEnabled() && resolution.contextResponse() != null) {
                    CompanyContextDocument contextDocument = buildCompanyContextDocument(
                            company,
                            resolution.contextResponse(),
                            options,
                            cache,
                            runtimeState,
                            stats,
                            info,
                            errors
                    );
                    if (contextDocument.hasContent()) {
                        writeCompanyContextFile(options.contextOutputDir(), company, contextDocument);
                        companyContextFilesGenerated++;
                    }
                }

                List<JobPostingCandidate> jobs = resolution.jobs();
                if (jobs.isEmpty()) {
                    companyStatus = "no_jobs";
                    info.add("No job candidates detected for " + company.id() + ".");
                } else {
                    int written = writeCompanyRawFiles(options.outputDir(), company, jobs);
                    companyRawFilesGenerated = written;
                    info.add("Fetched " + company.id() + ": " + written + " raw job files generated.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed = true;
            companyStatus = "failed";
            errors.add("Fetch interrupted for " + company.id() + ".");
        } catch (IOException e) {
            failed = true;
            companyStatus = "failed";
            errors.add("I/O failure for " + company.id() + ": " + e.getMessage());
        } finally {
            info.add(companyFetchTimingMessage(
                    company.id(),
                    companyStartedAtMillis,
                    companyRawFilesGenerated,
                    companyContextFilesGenerated,
                    companyStatus
            ));
        }

        return new CompanyFetchResult(
                workItem.index(),
                company.id(),
                failed,
                companyRawFilesGenerated,
                companyContextFilesGenerated,
                stats.snapshot(),
                List.copyOf(info),
                List.copyOf(errors),
                resolutionEntry
        );
    }

    private CompanyFetchResult unexpectedFetchFailure(CompanyConfig company, Throwable cause) {
        String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? "Unexpected fetch task failure."
                : cause.getMessage();
        return new CompanyFetchResult(
                -1,
                company.id(),
                true,
                0,
                0,
                new FetchStatsSnapshot(0, 0, 0, 0, 0),
                List.of(),
                List.of("Fetch failed for " + company.id() + ": " + message),
                Optional.empty()
        );
    }

    private CareerPageHttpFetcher.FetchResult chooseResponse(
            CompanyConfig company,
            FetchOptions options,
            Optional<CareerPageResponseCache.CachedFetchResult> cachedResponse,
            CareerPageResponseCache cache,
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        return chooseResponseForUrl(
                company.id(),
                company.careerUrl(),
                options,
                cachedResponse,
                cache,
                runtimeState,
                stats,
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
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        if (!isAllowedByRobots(url, options, runtimeState, stats, info, errors, companyId)) {
            return null;
        }
        if (cachedResponse.isPresent() && isFresh(cachedResponse.get(), options.cacheTtlMinutes())) {
            stats.recordFreshCacheHit();
            info.add("Using fresh cache for " + companyId + ".");
            return cachedResponse.get().response();
        }

        stats.recordCacheMiss();
        FetchAttemptOutcome attempt = fetchWithRetry(url, options, runtimeState, stats);
        stats.addRetries(attempt.retriesUsed());
        if (attempt.response() != null && attempt.response().statusCode() < 400) {
            if (attempt.retriesUsed() > 0) {
                info.add("Recovered " + companyId + " after " + attempt.retriesUsed() + " retry attempt(s).");
            }
            writeCacheSafely(cache, url, attempt.response(), info);
            return attempt.response();
        }

        if (cachedResponse.isPresent()) {
            stats.recordStaleCacheFallback();
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

    private CareerResolutionResult resolveCareerPageForJobs(
            CompanyConfig company,
            CareerPageHttpFetcher.FetchResult configuredResponse,
            Optional<CareerUrlResolutionEntry> previousResolution,
            FetchOptions options,
            CareerPageResponseCache cache,
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
            List<String> info,
            List<String> errors
    ) throws InterruptedException {
        int maxItems = Math.max(1, options.maxJobsPerCompany());
        List<String> queue = new ArrayList<>();
        Set<String> queuedKeys = new LinkedHashSet<>();
        Map<String, CareerPageHttpFetcher.FetchResult> responsesByKey = new HashMap<>();
        Set<String> expandedFinalUrls = new HashSet<>();

        previousResolution.ifPresent(entry -> {
            if (!entry.resolvedCareerUrl().isBlank() && entry.jobsFound() > 0) {
                enqueueCandidateUrl(queue, queuedKeys, entry.resolvedCareerUrl());
            }
        });
        enqueueCandidateUrl(queue, queuedKeys, company.careerUrl());
        addPathCandidatesFromUrl(queue, queuedKeys, company.careerUrl());
        addPathCandidatesFromUrl(queue, queuedKeys, company.corporateUrl());

        if (configuredResponse != null) {
            responsesByKey.put(urlKey(company.careerUrl()), configuredResponse);
            responsesByKey.put(urlKey(configuredResponse.finalUrl()), configuredResponse);
            if (configuredResponse.statusCode() < 400) {
                addDiscoveredCandidates(
                        queue,
                        queuedKeys,
                        extractor.discoverJobBoardUrls(
                                configuredResponse.body(),
                                configuredResponse.finalUrl(),
                                CAREER_URL_RESOLUTION_MAX_DISCOVERED_URLS
                        )
                );
            }
        }

        CandidateEvaluation best = null;
        CareerPageHttpFetcher.FetchResult contextResponse = null;
        boolean reachable = false;

        int index = 0;
        while (index < queue.size() && index < CAREER_URL_RESOLUTION_MAX_CANDIDATES) {
            String candidateUrl = queue.get(index++);
            if (candidateUrl == null || candidateUrl.isBlank()) {
                continue;
            }

            CareerPageHttpFetcher.FetchResult response = responsesByKey.get(urlKey(candidateUrl));
            if (response == null) {
                response = chooseResponseForUrl(
                        company.id(),
                        candidateUrl,
                        options,
                        readFromCache(cache, candidateUrl),
                        cache,
                        runtimeState,
                        stats,
                        info,
                        errors
                );
                if (response != null) {
                    responsesByKey.put(urlKey(candidateUrl), response);
                    responsesByKey.put(urlKey(response.finalUrl()), response);
                }
            }
            if (response == null || response.statusCode() >= 400) {
                continue;
            }

            reachable = true;
            if (contextResponse == null) {
                contextResponse = response;
            }

            String finalUrlKey = urlKey(response.finalUrl());
            if (expandedFinalUrls.add(finalUrlKey)) {
                addDiscoveredCandidates(
                        queue,
                        queuedKeys,
                        extractor.discoverJobBoardUrls(
                                response.body(),
                                response.finalUrl(),
                                CAREER_URL_RESOLUTION_MAX_DISCOVERED_URLS
                        )
                );
                addPathCandidatesFromUrl(queue, queuedKeys, response.finalUrl());
            }

            List<JobPostingCandidate> jobs = extractor.extract(response.body(), response.finalUrl(), maxItems);
            int score = scoreCareerPageCandidate(company, response.finalUrl(), jobs);
            CandidateEvaluation evaluation = new CandidateEvaluation(response, jobs, score);
            if (best == null || isBetterCandidate(evaluation, best)) {
                best = evaluation;
            }
        }

        if (best == null) {
            return new CareerResolutionResult(false, contextResponse, List.of(), "", 0);
        }

        int confidence = computeResolutionConfidence(best.score(), best.jobs().size());
        String configuredUrlKey = urlKey(company.careerUrl());
        String resolvedUrl = best.response().finalUrl();
        if (best.jobs().isEmpty()) {
            info.add(
                    "career_url_resolution_no_jobs: "
                            + company.id()
                            + " best_url="
                            + resolvedUrl
                            + " score="
                            + best.score()
                            + " candidates_checked="
                            + Math.min(index, CAREER_URL_RESOLUTION_MAX_CANDIDATES)
            );
        }
        if (!best.jobs().isEmpty() && !urlKey(resolvedUrl).equals(configuredUrlKey)) {
            info.add(
                    "career_url_resolved: "
                            + company.id()
                            + " -> "
                            + resolvedUrl
                            + " (confidence="
                            + confidence
                            + ", jobs="
                            + best.jobs().size()
                            + ")"
            );
        }
        return new CareerResolutionResult(
                reachable,
                contextResponse == null ? best.response() : contextResponse,
                best.jobs(),
                resolvedUrl,
                confidence
        );
    }

    private boolean isBetterCandidate(CandidateEvaluation left, CandidateEvaluation right) {
        if (left.score() != right.score()) {
            return left.score() > right.score();
        }
        if (left.jobs().size() != right.jobs().size()) {
            return left.jobs().size() > right.jobs().size();
        }
        return left.response().finalUrl().length() < right.response().finalUrl().length();
    }

    private int scoreCareerPageCandidate(
            CompanyConfig company,
            String url,
            List<JobPostingCandidate> jobs
    ) {
        int score = jobs.size() * 20;
        String normalizedUrl = normalize(url);
        String normalizedConfigured = normalize(company.careerUrl());
        if (normalizedUrl.equals(normalizedConfigured)) {
            score += 6;
        }
        for (String providerHint : ATS_PROVIDER_HINTS) {
            if (normalizedUrl.contains(providerHint)) {
                score += 18;
                break;
            }
        }
        if (normalizedUrl.contains("/job") || normalizedUrl.contains("/career") || normalizedUrl.contains("/recruit")) {
            score += 8;
        }
        for (String hint : NON_JOB_PATH_HINTS) {
            if (normalizedUrl.contains(hint)) {
                score -= 10;
            }
        }
        int jobDescriptionHits = 0;
        int eventLikeHits = 0;
        for (JobPostingCandidate job : jobs) {
            String candidateUrl = normalize(job.url());
            String title = normalize(job.title());
            if (candidateUrl.contains("job-description") || candidateUrl.contains("/jobs/")) {
                jobDescriptionHits++;
            }
            if (title.contains("event") || title.contains("session") || title.contains("registration")) {
                eventLikeHits++;
            }
        }
        score += Math.min(18, jobDescriptionHits * 6);
        score -= eventLikeHits * 8;
        if (!jobs.isEmpty()) {
            score += 12;
        }
        return score;
    }

    private int computeResolutionConfidence(int score, int jobCount) {
        if (jobCount < 1) {
            return 0;
        }
        int raw = 20 + score + (jobCount * 4);
        if (raw < 0) {
            return 0;
        }
        return Math.min(100, raw);
    }

    private void addDiscoveredCandidates(
            List<String> queue,
            Set<String> queuedKeys,
            List<String> discoveredUrls
    ) {
        for (String discoveredUrl : discoveredUrls) {
            if (queue.size() >= CAREER_URL_RESOLUTION_MAX_CANDIDATES) {
                return;
            }
            enqueueCandidateUrl(queue, queuedKeys, discoveredUrl);
        }
    }

    private void addPathCandidatesFromUrl(
            List<String> queue,
            Set<String> queuedKeys,
            String sourceUrl
    ) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException e) {
            return;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isBlank()) {
            return;
        }
        StringBuilder base = new StringBuilder();
        base.append(scheme).append("://").append(host);
        if (uri.getPort() > 0) {
            base.append(":").append(uri.getPort());
        }
        String baseUrl = base.toString();
        for (String path : CAREER_PATH_CANDIDATES) {
            if (queue.size() >= CAREER_URL_RESOLUTION_MAX_CANDIDATES) {
                return;
            }
            enqueueCandidateUrl(queue, queuedKeys, baseUrl + path);
        }

        String sourcePath = uri.getPath();
        if (sourcePath == null || sourcePath.isBlank() || "/".equals(sourcePath)) {
            return;
        }
        String normalizedPath = sourcePath.endsWith("/")
                ? sourcePath.substring(0, sourcePath.length() - 1)
                : sourcePath;
        if (normalizedPath.isBlank()) {
            return;
        }
        List<String> derivedPaths = List.of(
                normalizedPath + "/jobs",
                normalizedPath + "/positions",
                normalizedPath + "/open-positions",
                normalizedPath + "/mid-career",
                normalizedPath + "/mid-career/dx"
        );
        for (String path : derivedPaths) {
            if (queue.size() >= CAREER_URL_RESOLUTION_MAX_CANDIDATES) {
                return;
            }
            enqueueCandidateUrl(queue, queuedKeys, baseUrl + path);
        }
    }

    private void enqueueCandidateUrl(
            List<String> queue,
            Set<String> queuedKeys,
            String candidateUrl
    ) {
        if (candidateUrl == null || candidateUrl.isBlank()) {
            return;
        }
        String key = urlKey(candidateUrl);
        if (key.isBlank() || !queuedKeys.add(key)) {
            return;
        }
        queue.add(candidateUrl.trim());
    }

    private String urlKey(String value) {
        return normalize(canonicalizeUrl(value));
    }

    private CompanyContextDocument buildCompanyContextDocument(
            CompanyConfig company,
            CareerPageHttpFetcher.FetchResult careerResponse,
            FetchOptions options,
            CareerPageResponseCache cache,
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
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
                runtimeState,
                stats,
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
                        runtimeState,
                        stats,
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
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
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
                runtimeState,
                stats,
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
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
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
                runtimeState,
                stats,
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
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
            List<String> info
    ) throws InterruptedException {
        String host = hostKey(targetUrl);
        Optional<RobotsTxtRules> cached = runtimeState.robotsRulesByHost().get(host);
        if (cached != null) {
            return cached;
        }

        String robotsUrl = buildRobotsUrl(targetUrl);
        if (robotsUrl.isBlank()) {
            Optional<RobotsTxtRules> empty = Optional.empty();
            runtimeState.robotsRulesByHost().put(host, empty);
            return empty;
        }

        CompletableFuture<Optional<RobotsTxtRules>> created = new CompletableFuture<>();
        CompletableFuture<Optional<RobotsTxtRules>> existing =
                runtimeState.robotsLoadsInFlight().putIfAbsent(host, created);
        if (existing != null) {
            return waitForRobotsRules(host, existing, info);
        }

        try {
            Optional<RobotsTxtRules> loaded = fetchRobotsRulesFromNetwork(
                    host,
                    robotsUrl,
                    options,
                    runtimeState,
                    stats,
                    info
            );
            runtimeState.robotsRulesByHost().put(host, loaded);
            created.complete(loaded);
            return loaded;
        } catch (InterruptedException e) {
            created.completeExceptionally(e);
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            created.completeExceptionally(e);
            throw e;
        } finally {
            runtimeState.robotsLoadsInFlight().remove(host, created);
        }
    }

    private Optional<RobotsTxtRules> waitForRobotsRules(
            String host,
            CompletableFuture<Optional<RobotsTxtRules>> future,
            List<String> info
    ) throws InterruptedException {
        try {
            Optional<RobotsTxtRules> loaded = future.get();
            return loaded == null ? Optional.empty() : loaded;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                    ? "unknown failure"
                    : cause.getMessage();
            info.add("robots_warning: failed to reuse robots.txt result for " + host + ": " + message);
            return Optional.empty();
        }
    }

    private Optional<RobotsTxtRules> fetchRobotsRulesFromNetwork(
            String host,
            String robotsUrl,
            FetchOptions options,
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats,
            List<String> info
    ) throws InterruptedException {
        Semaphore semaphore = runtimeState.hostSemaphore(host, options.maxConcurrencyPerHost());
        semaphore.acquire();
        try {
            applyRequestDelay(robotsUrl, options.requestDelayMillis(), runtimeState);
            stats.recordOutgoingRequest();
            CareerPageHttpFetcher.FetchResult robotsResponse = fetcher.fetch(
                    robotsUrl,
                    Duration.ofSeconds(Math.max(5, options.timeoutSeconds())),
                    options.userAgent()
            );
            if (robotsResponse.statusCode() >= 400) {
                return Optional.empty();
            }
            return Optional.of(RobotsTxtRules.parse(robotsResponse.body()));
        } catch (IOException e) {
            info.add("robots_warning: failed to read robots.txt for " + host + ": " + e.getMessage());
            return Optional.empty();
        } finally {
            semaphore.release();
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
            FetchRuntimeState runtimeState,
            FetchStatsTracker stats
    ) throws InterruptedException {
        int maxAttempts = Math.max(1, options.retries() + 1);
        int retriesUsed = 0;
        String lastError = null;
        String host = hostKey(url);
        Semaphore semaphore = runtimeState.hostSemaphore(host, options.maxConcurrencyPerHost());

        for (int attemptIndex = 0; attemptIndex < maxAttempts; attemptIndex++) {
            try {
                semaphore.acquire();
                CareerPageHttpFetcher.FetchResult response;
                try {
                    applyRequestDelay(url, options.requestDelayMillis(), runtimeState);
                    stats.recordOutgoingRequest();
                    response = fetcher.fetch(
                            url,
                            Duration.ofSeconds(Math.max(5, options.timeoutSeconds())),
                            options.userAgent()
                    );
                } finally {
                    semaphore.release();
                }
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
            FetchRuntimeState runtimeState
    ) throws InterruptedException {
        long normalizedDelayMillis = Math.max(0, requestDelayMillis);
        String hostKey = hostKey(url);
        Object hostLock = runtimeState.hostLock(hostKey);
        synchronized (hostLock) {
            if (normalizedDelayMillis == 0) {
                return;
            }
            long now = Instant.now().toEpochMilli();
            Long lastRequestAt = runtimeState.lastRequestByHost().get(hostKey);
            if (lastRequestAt != null) {
                long elapsed = now - lastRequestAt;
                long waitMillis = normalizedDelayMillis - elapsed;
                if (waitMillis > 0) {
                    Thread.sleep(waitMillis);
                }
            }
            runtimeState.lastRequestByHost().put(hostKey, Instant.now().toEpochMilli());
        }
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
        try (var stream = Files.list(companyDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
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

    private record CandidateEvaluation(
            CareerPageHttpFetcher.FetchResult response,
            List<JobPostingCandidate> jobs,
            int score
    ) {
    }

    private record CareerResolutionResult(
            boolean reachable,
            CareerPageHttpFetcher.FetchResult contextResponse,
            List<JobPostingCandidate> jobs,
            String resolvedCareerUrl,
            int confidence
    ) {
        boolean hasResolvedUrl() {
            return resolvedCareerUrl != null && !resolvedCareerUrl.isBlank();
        }
    }

    private record CareerUrlResolutionEntry(
            String companyId,
            String configuredCareerUrl,
            String resolvedCareerUrl,
            int confidence,
            int jobsFound,
            String updatedAt
    ) {
    }

    private static final class CareerUrlResolutionStore {
        private final Map<String, CareerUrlResolutionEntry> entriesByCompanyId;

        private CareerUrlResolutionStore(Map<String, CareerUrlResolutionEntry> entriesByCompanyId) {
            this.entriesByCompanyId = entriesByCompanyId;
        }

        static CareerUrlResolutionStore load(Path stateFile, List<String> info) {
            Map<String, CareerUrlResolutionEntry> entries = new LinkedHashMap<>();
            if (stateFile == null || !Files.exists(stateFile)) {
                return new CareerUrlResolutionStore(entries);
            }

            try {
                String yamlText = Files.readString(stateFile, StandardCharsets.UTF_8);
                Object loaded = new Yaml().load(yamlText);
                if (!(loaded instanceof Map<?, ?> root)) {
                    return new CareerUrlResolutionStore(entries);
                }
                Object resolutionItems = root.get("resolutions");
                if (!(resolutionItems instanceof List<?> list)) {
                    return new CareerUrlResolutionStore(entries);
                }
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> mapItem)) {
                        continue;
                    }
                    String companyId = toSafeString(mapItem.get("company_id"));
                    String configuredCareerUrl = toSafeString(mapItem.get("configured_career_url"));
                    String resolvedCareerUrl = toSafeString(mapItem.get("resolved_career_url"));
                    int confidence = toSafeInt(mapItem.get("confidence"));
                    int jobsFound = toSafeInt(mapItem.get("jobs_found"));
                    String updatedAt = toSafeString(mapItem.get("updated_at"));
                    if (companyId.isBlank()) {
                        continue;
                    }
                    entries.put(normalizeCompanyId(companyId), new CareerUrlResolutionEntry(
                            companyId,
                            configuredCareerUrl,
                            resolvedCareerUrl,
                            confidence,
                            jobsFound,
                            updatedAt
                    ));
                }
            } catch (IOException | YAMLException | ClassCastException e) {
                info.add("career_url_resolution_warning: failed to load resolution state (" + e.getMessage() + ")");
            }
            return new CareerUrlResolutionStore(entries);
        }

        Optional<CareerUrlResolutionEntry> find(String companyId) {
            return Optional.ofNullable(entriesByCompanyId.get(normalizeCompanyId(companyId)));
        }

        void remember(CareerUrlResolutionEntry entry) {
            if (entry == null || entry.companyId() == null || entry.companyId().isBlank()) {
                return;
            }
            entriesByCompanyId.put(normalizeCompanyId(entry.companyId()), entry);
        }

        void save(Path stateFile, List<String> info) {
            if (stateFile == null) {
                return;
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schema_version", RESOLUTION_STATE_SCHEMA_VERSION);
            root.put("updated_at", Instant.now().toString());

            List<Map<String, Object>> items = new ArrayList<>();
            for (CareerUrlResolutionEntry entry : entriesByCompanyId.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("company_id", toSafeString(entry.companyId()));
                item.put("configured_career_url", toSafeString(entry.configuredCareerUrl()));
                item.put("resolved_career_url", toSafeString(entry.resolvedCareerUrl()));
                item.put("confidence", Math.max(0, Math.min(100, entry.confidence())));
                item.put("jobs_found", Math.max(0, entry.jobsFound()));
                item.put("updated_at", toSafeString(entry.updatedAt()));
                items.add(item);
            }
            root.put("resolutions", items);

            try {
                Path parent = stateFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(stateFile, dumpResolutionYaml(root), StandardCharsets.UTF_8);
            } catch (IOException e) {
                info.add("career_url_resolution_warning: failed to persist resolution state (" + e.getMessage() + ")");
            }
        }

        private static String normalizeCompanyId(String value) {
            if (value == null) {
                return "";
            }
            return value.trim().toLowerCase(Locale.ROOT);
        }

        private static String toSafeString(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof String text) {
                return text.trim();
            }
            return String.valueOf(value).trim();
        }

        private static int toSafeInt(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }

        private static String dumpResolutionYaml(Map<String, Object> root) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setIndicatorIndent(1);
            options.setWidth(120);
            return new Yaml(options).dump(root);
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
            int maxConcurrency,
            int maxConcurrencyPerHost,
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
                    FetchWebRuntimeConfig.defaults().retries(),
                    FetchWebRuntimeConfig.defaults().backoffMillis(),
                    FetchWebRuntimeConfig.defaults().requestDelayMillis(),
                    FetchWebRuntimeConfig.defaults().maxConcurrency(),
                    FetchWebRuntimeConfig.defaults().maxConcurrencyPerHost(),
                    DEFAULT_CONTEXT_OUTPUT_DIR,
                    true,
                    FetchWebRuntimeConfig.defaults().robotsMode(),
                    DEFAULT_CACHE_DIR,
                    FetchWebRuntimeConfig.defaults().cacheTtlMinutes(),
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
            maxConcurrency = Math.max(1, maxConcurrency);
            maxConcurrencyPerHost = Math.max(1, maxConcurrencyPerHost);
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
            long totalDurationMillis,
            int freshCacheHitCount,
            int cacheMissCount,
            int staleCacheFallbackCount,
            int retryCount,
            int outgoingRequestCount,
            List<String> informationalMessages,
            List<String> errorMessages
    ) {
        public boolean allSelectedCompaniesFailed() {
            return selectedCompanies > 0 && companiesFailed == selectedCompanies;
        }
    }

    private static final class FetchStatsTracker {
        private int freshCacheHits;
        private int cacheMisses;
        private int staleCacheFallbacks;
        private int retriesUsed;
        private int outgoingRequests;

        private void recordFreshCacheHit() {
            freshCacheHits++;
        }

        private void recordCacheMiss() {
            cacheMisses++;
        }

        private void recordStaleCacheFallback() {
            staleCacheFallbacks++;
        }

        private void addRetries(int count) {
            retriesUsed += Math.max(0, count);
        }

        private void recordOutgoingRequest() {
            outgoingRequests++;
        }

        private void addAll(FetchStatsSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            freshCacheHits += Math.max(0, snapshot.freshCacheHits());
            cacheMisses += Math.max(0, snapshot.cacheMisses());
            staleCacheFallbacks += Math.max(0, snapshot.staleCacheFallbacks());
            retriesUsed += Math.max(0, snapshot.retriesUsed());
            outgoingRequests += Math.max(0, snapshot.outgoingRequests());
        }

        private FetchStatsSnapshot snapshot() {
            return new FetchStatsSnapshot(
                    freshCacheHits,
                    cacheMisses,
                    staleCacheFallbacks,
                    retriesUsed,
                    outgoingRequests
            );
        }

        private int freshCacheHits() {
            return freshCacheHits;
        }

        private int cacheMisses() {
            return cacheMisses;
        }

        private int staleCacheFallbacks() {
            return staleCacheFallbacks;
        }

        private int retriesUsed() {
            return retriesUsed;
        }

        private int outgoingRequests() {
            return outgoingRequests;
        }
    }

    private record FetchStatsSnapshot(
            int freshCacheHits,
            int cacheMisses,
            int staleCacheFallbacks,
            int retriesUsed,
            int outgoingRequests
    ) {
    }

    private record CompanyFetchWorkItem(
            int index,
            CompanyConfig company,
            Optional<CareerUrlResolutionEntry> previousResolution
    ) {
    }

    private record CompanyFetchResult(
            int index,
            String companyId,
            boolean failed,
            int rawFilesGenerated,
            int contextFilesGenerated,
            FetchStatsSnapshot stats,
            List<String> informationalMessages,
            List<String> errorMessages,
            Optional<CareerUrlResolutionEntry> resolutionEntry
    ) {
    }

    private static final class FetchRuntimeState {
        private final ConcurrentHashMap<String, Long> lastRequestByHost = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Optional<RobotsTxtRules>> robotsRulesByHost = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CompletableFuture<Optional<RobotsTxtRules>>> robotsLoadsInFlight =
                new ConcurrentHashMap<>();

        private ConcurrentHashMap<String, Long> lastRequestByHost() {
            return lastRequestByHost;
        }

        private ConcurrentHashMap<String, Optional<RobotsTxtRules>> robotsRulesByHost() {
            return robotsRulesByHost;
        }

        private Object hostLock(String host) {
            String normalizedHost = host == null ? "" : host;
            return hostLocks.computeIfAbsent(normalizedHost, ignored -> new Object());
        }

        private Semaphore hostSemaphore(String host, int maxConcurrencyPerHost) {
            String normalizedHost = host == null ? "" : host;
            return hostSemaphores.computeIfAbsent(
                    normalizedHost,
                    ignored -> new Semaphore(Math.max(1, maxConcurrencyPerHost), true)
            );
        }

        private ConcurrentHashMap<String, CompletableFuture<Optional<RobotsTxtRules>>> robotsLoadsInFlight() {
            return robotsLoadsInFlight;
        }
    }

    private record FetchAttemptOutcome(
            CareerPageHttpFetcher.FetchResult response,
            String errorMessage,
            int retriesUsed
    ) {
    }
}
