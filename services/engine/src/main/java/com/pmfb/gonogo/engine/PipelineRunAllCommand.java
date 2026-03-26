package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.ConfigSelections;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.EvaluationRuntimeConfig;
import com.pmfb.gonogo.engine.config.FetchWebRuntimeConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.RuntimeOptionResolver;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.report.TrendAlertSinkFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "run-all",
        aliases = {"all", "full"},
        mixinStandardHelpOptions = true,
        description = "Run the complete pipeline for all personas and all companies by default."
)
public final class PipelineRunAllCommand implements Callable<Integer> {
    private static final String WEEKLY_FILE_PREFIX = "weekly-";
    private static final String WEEKLY_FILE_EXTENSION = ".md";
    private static final String DEFAULT_ALERT_SINK = TrendAlertSinkFactory.SINK_NONE;
    private static final Path RESOLUTION_STATE_FILE = Path.of("output/fetch-web-resolution.yaml");
    private static final String LABEL_RUN_ALL_COMPLETED = "run-all completed.";
    private static final String FIELD_PERSONAS_TOTAL = "personas_total";
    private static final String FIELD_PERSONAS_SUCCEEDED = "personas_succeeded";
    private static final String FIELD_PERSONAS_FAILED = "personas_failed";
    private static final String FIELD_PERSONA_CONCURRENCY = "persona_concurrency";
    private static final String FIELD_RAW_INPUT_DIR = "raw_input_dir";
    private static final String FIELD_JOBS_OUTPUT_DIR = "jobs_output_dir";
    private static final String FIELD_BATCH_OUTPUT_DIR = "batch_output_dir";
    private static final String FIELD_WEEKLY_OUTPUT_DIR = "weekly_output_dir";
    private static final String FIELD_FETCH_MAX_JOBS_PER_COMPANY = "fetch_max_jobs_per_company";
    private static final String FIELD_PERSONA_EXIT = "persona_exit";
    private static final String FIELD_PERSONA_JOBS_OUTPUT_DIR = "persona_jobs_output_dir";

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Option(
            names = {"--persona-ids", "--personas"},
            split = ",",
            description = "Optional comma-separated persona ids. Defaults to all personas in config."
    )
    private List<String> personaIds;

    @Option(
            names = {"--candidate-profile"},
            description = "Optional candidate profile id from config/candidate-profiles (auto-selects when exactly one exists). Use 'none' to disable."
    )
    private String candidateProfileId;

    @Option(
            names = {"--company-ids"},
            split = ",",
            description = "Optional comma-separated company ids. Defaults to all companies in config."
    )
    private List<String> companyIds;

    @Option(
            names = {"--raw-input-dir"},
            description = "Directory with raw job text files.",
            defaultValue = "output/raw"
    )
    private Path rawInputDir;

    @Option(
            names = {"--raw-pattern"},
            description = "Glob pattern for raw job files (relative to input dir).",
            defaultValue = "*.txt"
    )
    private String rawPattern;

    @Option(
            names = {"--jobs-output-dir"},
            description = "Directory where normalized job YAML files are generated.",
            defaultValue = "output/jobs"
    )
    private Path jobsOutputDir;

    @Option(
            names = {"--batch-output-dir"},
            description = "Directory where batch JSON/Markdown reports are generated.",
            defaultValue = "output"
    )
    private Path batchOutputDir;

    @Option(
            names = {"--weekly-output-dir"},
            description = "Directory where per-persona weekly markdown reports are generated.",
            defaultValue = "output"
    )
    private Path weeklyOutputDir;

    @Option(
            names = {"--top-per-section"},
            description = "Max items to include in each digest verdict section.",
            defaultValue = "5"
    )
    private int topPerSection;

    @Option(
            names = {"--fetch-web-max-jobs-per-company"},
            description = "Maximum extracted job snippets per company in fetch stage.",
            defaultValue = "12"
    )
    private int fetchWebMaxJobsPerCompany;

    @Option(
            names = {"--fetch-web-timeout-seconds"},
            description = "HTTP timeout in seconds for each fetch-web request.",
            defaultValue = "20"
    )
    private int fetchWebTimeoutSeconds;

    @Option(
            names = {"--fetch-web-user-agent"},
            description = "User-Agent used for fetch-web requests.",
            defaultValue = "go-no-go-engine/0.1 (+https://local)"
    )
    private String fetchWebUserAgent;

    @Option(
            names = {"--fetch-web-retries"},
            description = "Retry attempts after first fetch failure in fetch stage.",
            defaultValue = "2"
    )
    private int fetchWebRetries;

    @Option(
            names = {"--fetch-web-backoff-millis"},
            description = "Base backoff in milliseconds between fetch-web retries.",
            defaultValue = "300"
    )
    private long fetchWebBackoffMillis;

    @Option(
            names = {"--fetch-web-request-delay-millis"},
            description = "Minimum delay in milliseconds between fetch-web requests to the same host.",
            defaultValue = "1200"
    )
    private long fetchWebRequestDelayMillis;

    @Option(
            names = {"--fetch-web-max-concurrency"},
            description = "Maximum number of companies to fetch in parallel during fetch stage.",
            defaultValue = "4"
    )
    private int fetchWebMaxConcurrency;

    @Option(
            names = {"--fetch-web-max-concurrency-per-host"},
            description = "Maximum number of in-flight requests allowed per host during fetch stage.",
            defaultValue = "1"
    )
    private int fetchWebMaxConcurrencyPerHost;

    @Option(
            names = {"--evaluate-max-concurrency"},
            description = "Maximum number of jobs to evaluate in parallel inside each pipeline run.",
            defaultValue = "4"
    )
    private int evaluateMaxConcurrency;

    @Option(
            names = {"--fetch-web-robots-mode"},
            description = "Robots mode for fetch stage: strict, warn, off.",
            defaultValue = "strict"
    )
    private String fetchWebRobotsMode;

    @Option(
            names = {"--fetch-web-cache-dir"},
            description = "Directory for fetch-web response cache.",
            defaultValue = ".cache/fetch-web"
    )
    private Path fetchWebCacheDir;

    @Option(
            names = {"--fetch-web-cache-ttl-minutes"},
            description = "Fetch-web cache freshness TTL in minutes.",
            defaultValue = "720"
    )
    private long fetchWebCacheTtlMinutes;

    @Option(
            names = {"--fetch-web-disable-cache"},
            description = "Disable fetch-web response cache.",
            defaultValue = "false"
    )
    private boolean fetchWebDisableCache;

    @Option(
            names = {"--company-context-dir"},
            description = "Directory for company context files used in evaluation.",
            defaultValue = "output/company-context"
    )
    private Path companyContextDir;

    @Option(
            names = {"--disable-company-context"},
            description = "Disable company context enrichment in fetch/evaluation stages.",
            defaultValue = "false"
    )
    private boolean disableCompanyContext;

    @Option(
            names = {"--disable-change-detection"},
            description = "Disable job change detection and state persistence.",
            defaultValue = "false"
    )
    private boolean disableChangeDetection;

    @Option(
            names = {"--incremental-only"},
            description = "Evaluate only NEW and UPDATED jobs (requires change detection).",
            defaultValue = "false"
    )
    private boolean incrementalOnly;

    @Option(
            names = {"--disable-trend-history"},
            description = "Disable run-level trend comparison and history persistence.",
            defaultValue = "false"
    )
    private boolean disableTrendHistory;

    @Option(
            names = {"--disable-trend-alerts"},
            description = "Disable anomaly alerts derived from trend comparison.",
            defaultValue = "false"
    )
    private boolean disableTrendAlerts;

    @Option(
            names = {"--trend-history-max-runs"},
            description = "Maximum number of run snapshots to retain in trend history.",
            defaultValue = "26"
    )
    private int trendHistoryMaxRuns;

    @Option(
            names = {"--alert-sinks"},
            split = ",",
            description = "Comma-separated alert sinks: none, stdout, json-file.",
            defaultValue = DEFAULT_ALERT_SINK
    )
    private List<String> alertSinks;

    @Option(
            names = {"--fail-on-warnings"},
            description = "Return non-zero exit code when normalization warnings are present.",
            defaultValue = "false"
    )
    private boolean failOnWarnings;

    @Option(
            names = {"--skip-fetch-web"},
            description = "Skip fetch stage and evaluate from existing files in --raw-input-dir.",
            defaultValue = "false"
    )
    private boolean skipFetchWeb;

    @Option(
            names = {"--fetch-web-auto-skip-minutes"},
            description = "Skip fetch for companies with data younger than this threshold (minutes). "
                    + "0 disables auto-skip. Ignored when --skip-fetch-web or --company-ids is set. "
                    + "Default matches the response cache TTL.",
            defaultValue = "720"
    )
    private long fetchWebAutoSkipMinutes;

    @Option(
            names = {"--fail-fast"},
            description = "Stop at first persona failure.",
            defaultValue = "false"
    )
    private boolean failFast;

    @Option(
            names = {"--persona-concurrency"},
            description = "Maximum number of persona runs to execute in parallel after shared fetch is complete.",
            defaultValue = "1"
    )
    private int personaConcurrency;

    private int effectiveFetchWebTimeoutSeconds;
    private String effectiveFetchWebUserAgent;
    private int effectiveFetchWebRetries;
    private long effectiveFetchWebBackoffMillis;
    private long effectiveFetchWebRequestDelayMillis;
    private int effectiveFetchWebMaxConcurrency;
    private int effectiveFetchWebMaxConcurrencyPerHost;
    private String effectiveFetchWebRobotsMode;
    private long effectiveFetchWebCacheTtlMinutes;
    private int effectiveEvaluateMaxConcurrency;

    @Override
    public Integer call() {
        if (fetchWebMaxJobsPerCompany < 1) {
            System.err.println("--fetch-web-max-jobs-per-company must be at least 1.");
            return 1;
        }
        if (trendHistoryMaxRuns < 1) {
            System.err.println("--trend-history-max-runs must be at least 1.");
            return 1;
        }
        if (incrementalOnly && disableChangeDetection) {
            System.err.println("--incremental-only requires change detection.");
            return 1;
        }
        if (personaConcurrency < 1) {
            System.err.println("--persona-concurrency must be at least 1.");
            return 1;
        }
        if (evaluateMaxConcurrency < 1) {
            System.err.println("--evaluate-max-concurrency must be at least 1.");
            return 1;
        }
        if (failFast && personaConcurrency > 1) {
            System.err.println("--fail-fast is only supported with --persona-concurrency=1.");
            return 1;
        }

        EngineConfig config;
        try {
            config = new YamlConfigLoader(configDir).load();
        } catch (ConfigLoadException e) {
            printErrors("Configuration loading failed", e.errors());
            return 1;
        }

        List<String> configValidationErrors = new ConfigValidator().validate(config);
        if (!configValidationErrors.isEmpty()) {
            printErrors("Configuration validation failed", configValidationErrors);
            return 1;
        }

        applyRuntimeDefaults(
                config.runtimeSettings().fetchWeb(),
                config.runtimeSettings().evaluation()
        );

        List<PersonaConfig> selectedPersonas = selectPersonas(config.personas(), personaIds);
        if (selectedPersonas.isEmpty()) {
            return 1;
        }

        ConfigSelections.CandidateProfileResolution candidateProfileResolution =
                ConfigSelections.resolveCandidateProfile(config.candidateProfiles(), candidateProfileId);
        if (!candidateProfileResolution.errorMessage().isBlank()) {
            System.err.println(candidateProfileResolution.errorMessage());
            if (!config.candidateProfiles().isEmpty()) {
                System.err.println("Available candidate profiles:");
                for (CandidateProfileConfig item : config.candidateProfiles()) {
                    System.err.println(" - " + item.id());
                }
            }
            return 1;
        }
        String forwardedCandidateProfileArg = resolveForwardedCandidateProfileArg(
                candidateProfileId,
                candidateProfileResolution
        );

        boolean runFetchStage = !skipFetchWeb;
        List<String> effectiveFetchCompanyIds = null; // null = all configured companies

        if (runFetchStage && fetchWebAutoSkipMinutes > 0 && !hasValues(companyIds)) {
            List<String> staleIds = computeStaleCompanyIds(config.companies());
            if (staleIds.isEmpty()) {
                System.out.println("[run-all] All " + config.companies().size()
                        + " companies have fresh data (< " + fetchWebAutoSkipMinutes
                        + "min). Skipping fetch stage.");
                runFetchStage = false;
            } else if (staleIds.size() < config.companies().size()) {
                System.out.println("[run-all] Fetching " + staleIds.size() + " of "
                        + config.companies().size() + " companies with stale or missing data: "
                        + String.join(", ", staleIds));
                effectiveFetchCompanyIds = staleIds;
            } else {
                System.out.println("[run-all] All " + config.companies().size()
                        + " companies need fetch.");
            }
        }

        if (runFetchStage) {
            int fetchExitCode = runSharedFetchStage(effectiveFetchCompanyIds);
            if (fetchExitCode != 0) {
                return fetchExitCode;
            }
        }

        List<PersonaRunRequest> requests = new ArrayList<>(selectedPersonas.size());
        for (PersonaConfig persona : selectedPersonas) {
            requests.add(new PersonaRunRequest(
                    persona.id(),
                    resolvePersonaJobsOutputDir(persona.id(), forwardedCandidateProfileArg, personaConcurrency),
                    buildRunArgs(persona.id(), forwardedCandidateProfileArg, personaConcurrency > 1)
            ));
        }

        List<PersonaRunResult> results = personaConcurrency == 1
                ? runPersonaRequestsSequentially(requests)
                : runPersonaRequestsConcurrently(requests);
        Map<String, Integer> personaExitCodes = new LinkedHashMap<>();
        int succeeded = 0;
        int failed = 0;
        for (PersonaRunResult result : results) {
            personaExitCodes.put(result.personaId(), result.exitCode());
            if (result.exitCode() == 0) {
                succeeded++;
            } else {
                failed++;
                System.err.println("run-all failure: persona '" + result.personaId()
                        + "' exited with code " + result.exitCode() + ".");
            }
            if (!result.jobsOutputDir().equals(jobsOutputDir)) {
                System.out.println(FIELD_PERSONA_JOBS_OUTPUT_DIR + ": "
                        + result.personaId() + "=" + result.jobsOutputDir());
            }
        }

        printSummary(
                selectedPersonas.size(),
                succeeded,
                failed,
                forwardedCandidateProfileArg.isBlank()
                        ? ConfigSelections.CANDIDATE_PROFILE_NONE
                        : forwardedCandidateProfileArg,
                personaExitCodes,
                personaConcurrency
        );
        return failed == 0 ? 0 : 1;
    }

    private List<String> buildRunArgs(
            String personaId,
            String forwardedCandidateProfileArg,
            boolean suppressSummaryOutput
    ) {
        List<String> args = new ArrayList<>();
        args.add("--persona=" + personaId);
        if (!forwardedCandidateProfileArg.isBlank()) {
            args.add("--candidate-profile=" + forwardedCandidateProfileArg);
        }
        args.add("--config-dir=" + configDir);
        args.add("--raw-input-dir=" + rawInputDir);
        args.add("--raw-pattern=" + rawPattern);
        args.add("--recursive");
        args.add("--batch-output-dir=" + batchOutputDir);
        args.add("--weekly-output-file=" + resolveWeeklyOutputFile(personaId, forwardedCandidateProfileArg));
        args.add("--top-per-section=" + topPerSection);
        args.add("--trend-history-max-runs=" + trendHistoryMaxRuns);
        args.add("--company-context-dir=" + companyContextDir);
        if (hasValues(alertSinks)) {
            args.add("--alert-sinks=" + joinNonBlank(alertSinks));
        }

        if (disableCompanyContext) {
            args.add("--disable-company-context");
        }
        if (disableChangeDetection) {
            args.add("--disable-change-detection");
        }
        if (incrementalOnly) {
            args.add("--incremental-only");
        }
        if (disableTrendHistory) {
            args.add("--disable-trend-history");
        }
        if (disableTrendAlerts) {
            args.add("--disable-trend-alerts");
        }
        if (failOnWarnings) {
            args.add("--fail-on-warnings");
        }
        args.add("--evaluate-max-concurrency=" + effectiveEvaluateMaxConcurrency);
        if (suppressSummaryOutput) {
            args.add("--suppress-summary-output");
        }

        return args;
    }

    private int runSharedFetchStage(List<String> fetchCompanyIds) {
        List<String> args = new ArrayList<>();
        args.add("--config-dir=" + configDir);
        args.add("--output-dir=" + rawInputDir);
        args.add("--max-jobs-per-company=" + fetchWebMaxJobsPerCompany);
        args.add("--timeout-seconds=" + effectiveFetchWebTimeoutSeconds);
        args.add("--user-agent=" + effectiveFetchWebUserAgent);
        args.add("--retries=" + effectiveFetchWebRetries);
        args.add("--backoff-millis=" + effectiveFetchWebBackoffMillis);
        args.add("--request-delay-millis=" + effectiveFetchWebRequestDelayMillis);
        args.add("--max-concurrency=" + effectiveFetchWebMaxConcurrency);
        args.add("--max-concurrency-per-host=" + effectiveFetchWebMaxConcurrencyPerHost);
        args.add("--context-output-dir=" + companyContextDir);
        args.add("--robots-mode=" + effectiveFetchWebRobotsMode);
        args.add("--cache-dir=" + fetchWebCacheDir);
        args.add("--cache-ttl-minutes=" + effectiveFetchWebCacheTtlMinutes);
        if (disableCompanyContext) {
            args.add("--disable-company-context");
        }
        if (fetchWebDisableCache) {
            args.add("--disable-cache");
        }
        List<String> idsToFetch = hasValues(fetchCompanyIds) ? fetchCompanyIds : companyIds;
        if (hasValues(idsToFetch)) {
            args.add("--company-ids=" + joinNonBlank(idsToFetch));
        }
        return new CommandLine(new FetchWebCommand()).execute(args.toArray(String[]::new));
    }

    private void applyRuntimeDefaults(
            FetchWebRuntimeConfig fetchWebRuntimeDefaults,
            EvaluationRuntimeConfig evaluationRuntimeDefaults
    ) {
        effectiveFetchWebTimeoutSeconds = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-timeout-seconds",
                fetchWebTimeoutSeconds,
                fetchWebRuntimeDefaults.timeoutSeconds()
        );
        effectiveFetchWebUserAgent = RuntimeOptionResolver.resolveString(
                spec,
                "--fetch-web-user-agent",
                fetchWebUserAgent,
                fetchWebRuntimeDefaults.userAgent()
        );
        effectiveFetchWebRetries = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-retries",
                fetchWebRetries,
                fetchWebRuntimeDefaults.retries()
        );
        effectiveFetchWebBackoffMillis = RuntimeOptionResolver.resolveLong(
                spec,
                "--fetch-web-backoff-millis",
                fetchWebBackoffMillis,
                fetchWebRuntimeDefaults.backoffMillis()
        );
        effectiveFetchWebRequestDelayMillis = RuntimeOptionResolver.resolveLong(
                spec,
                "--fetch-web-request-delay-millis",
                fetchWebRequestDelayMillis,
                fetchWebRuntimeDefaults.requestDelayMillis()
        );
        effectiveFetchWebMaxConcurrency = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-max-concurrency",
                fetchWebMaxConcurrency,
                fetchWebRuntimeDefaults.maxConcurrency()
        );
        effectiveFetchWebMaxConcurrencyPerHost = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-max-concurrency-per-host",
                fetchWebMaxConcurrencyPerHost,
                fetchWebRuntimeDefaults.maxConcurrencyPerHost()
        );
        effectiveFetchWebRobotsMode = RuntimeOptionResolver.resolveString(
                spec,
                "--fetch-web-robots-mode",
                fetchWebRobotsMode,
                fetchWebRuntimeDefaults.robotsMode()
        );
        effectiveFetchWebCacheTtlMinutes = RuntimeOptionResolver.resolveLong(
                spec,
                "--fetch-web-cache-ttl-minutes",
                fetchWebCacheTtlMinutes,
                fetchWebRuntimeDefaults.cacheTtlMinutes()
        );
        effectiveEvaluateMaxConcurrency = RuntimeOptionResolver.resolveInt(
                spec,
                "--evaluate-max-concurrency",
                evaluateMaxConcurrency,
                evaluationRuntimeDefaults.maxConcurrency()
        );
    }

    private String resolveWeeklyOutputFile(String personaId, String candidateProfileIdValue) {
        String sanitized = sanitizeScope(personaId, candidateProfileIdValue);
        return weeklyOutputDir.resolve(WEEKLY_FILE_PREFIX + sanitized + WEEKLY_FILE_EXTENSION).toString();
    }

    private Path resolvePersonaJobsOutputDir(
            String personaId,
            String candidateProfileIdValue,
            int personaConcurrencyValue
    ) {
        if (personaConcurrencyValue <= 1) {
            return jobsOutputDir;
        }
        return jobsOutputDir.resolve(sanitizeScope(personaId, candidateProfileIdValue));
    }

    private String sanitizePersonaId(String personaId) {
        return normalize(personaId).replaceAll("[^a-z0-9_-]", "_");
    }

    private String sanitizeScope(String personaId, String candidateProfileIdValue) {
        String sanitizedPersona = sanitizePersonaId(personaId);
        String normalizedCandidate = normalize(candidateProfileIdValue);
        if (normalizedCandidate.isBlank() || ConfigSelections.isCandidateProfileNone(normalizedCandidate)) {
            return sanitizedPersona;
        }
        return sanitizedPersona + "--" + normalizedCandidate.replaceAll("[^a-z0-9_-]", "_");
    }

    private String resolveForwardedCandidateProfileArg(
            String requestedCandidateProfileId,
            ConfigSelections.CandidateProfileResolution resolution
    ) {
        String normalizedRequested = normalize(requestedCandidateProfileId);
        if (ConfigSelections.isCandidateProfileNone(normalizedRequested)) {
            return ConfigSelections.CANDIDATE_PROFILE_NONE;
        }
        return resolution.profile().map(CandidateProfileConfig::id).orElse("");
    }

    private List<PersonaConfig> selectPersonas(List<PersonaConfig> personas, List<String> requestedIds) {
        if (!hasValues(requestedIds)) {
            return personas;
        }

        Map<String, PersonaConfig> byId = new LinkedHashMap<>();
        for (PersonaConfig persona : personas) {
            byId.put(normalize(persona.id()), persona);
        }

        List<PersonaConfig> selected = new ArrayList<>();
        List<String> unknownIds = new ArrayList<>();
        for (String requestedId : requestedIds) {
            String normalized = normalize(requestedId);
            if (normalized.isBlank()) {
                continue;
            }
            PersonaConfig persona = byId.get(normalized);
            if (persona == null) {
                unknownIds.add(requestedId);
                continue;
            }
            if (!selected.contains(persona)) {
                selected.add(persona);
            }
        }

        if (!unknownIds.isEmpty()) {
            System.err.println("Unknown persona id(s): " + String.join(", ", unknownIds));
            System.err.println("Available personas:");
            for (PersonaConfig persona : personas) {
                System.err.println(" - " + persona.id());
            }
            return List.of();
        }

        if (selected.isEmpty()) {
            System.err.println("No valid persona ids were provided.");
            return List.of();
        }
        return List.copyOf(selected);
    }

    private boolean hasValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String joinNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> nonBlankValues = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                nonBlankValues.add(value.trim());
            }
        }
        return String.join(",", nonBlankValues);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    private List<String> computeStaleCompanyIds(List<CompanyConfig> companies) {
        Map<String, Instant> resolutionAges = loadResolutionUpdatedAt();
        Instant cutoff = Instant.now().minus(fetchWebAutoSkipMinutes, ChronoUnit.MINUTES);
        List<String> stale = new ArrayList<>();
        for (CompanyConfig company : companies) {
            Instant lastResolved = resolutionAges.getOrDefault(company.id(), Instant.EPOCH);
            boolean resolutionStale = lastResolved.isBefore(cutoff);
            boolean missingRawFiles = !hasRawFiles(rawInputDir, sanitizeCompanySlug(company.id()));
            if (resolutionStale || missingRawFiles) {
                stale.add(company.id());
            }
        }
        return stale;
    }

    private Map<String, Instant> loadResolutionUpdatedAt() {
        Map<String, Instant> result = new HashMap<>();
        if (!Files.exists(RESOLUTION_STATE_FILE)) {
            return result;
        }
        try {
            String content = Files.readString(RESOLUTION_STATE_FILE, StandardCharsets.UTF_8);
            Object parsed = new Yaml().load(content);
            if (!(parsed instanceof Map<?, ?> root)) {
                return result;
            }
            Object resolutions = root.get("resolutions");
            if (!(resolutions instanceof List<?> list)) {
                return result;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object id = entry.get("company_id");
                Object updatedAt = entry.get("updated_at");
                if (id instanceof String companyId && updatedAt instanceof String ts && !ts.isBlank()) {
                    try {
                        result.put(companyId, Instant.parse(ts));
                    } catch (DateTimeParseException ignored) {
                        // malformed timestamp: treat as stale
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[run-all] Warning: could not read resolution state ("
                    + e.getMessage() + "); fetching all companies.");
        }
        return result;
    }

    private boolean hasRawFiles(Path rawDir, String companySlug) {
        Path companyDir = rawDir.resolve(companySlug);
        if (!Files.isDirectory(companyDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(companyDir)) {
            return stream.anyMatch(
                    p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"));
        } catch (IOException e) {
            return false;
        }
    }

    private String sanitizeCompanySlug(String id) {
        String normalized = id == null ? "unknown" : id.trim().toLowerCase(Locale.ROOT);
        String slug = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "unknown" : slug;
    }

    private void printSummary(
            int totalPersonas,
            int succeeded,
            int failed,
            String candidateProfile,
            Map<String, Integer> personaExitCodes,
            int personaConcurrencyValue
    ) {
        System.out.println(LABEL_RUN_ALL_COMPLETED);
        System.out.println(FIELD_PERSONAS_TOTAL + ": " + totalPersonas);
        System.out.println(FIELD_PERSONAS_SUCCEEDED + ": " + succeeded);
        System.out.println(FIELD_PERSONAS_FAILED + ": " + failed);
        System.out.println(FIELD_PERSONA_CONCURRENCY + ": " + personaConcurrencyValue);
        System.out.println(EvaluateInputFieldKeys.CANDIDATE_PROFILE + ": " + candidateProfile);
        System.out.println(FIELD_RAW_INPUT_DIR + ": " + rawInputDir);
        System.out.println(FIELD_JOBS_OUTPUT_DIR + ": " + jobsOutputDir);
        System.out.println(FIELD_BATCH_OUTPUT_DIR + ": " + batchOutputDir);
        System.out.println(FIELD_WEEKLY_OUTPUT_DIR + ": " + weeklyOutputDir);
        System.out.println(FIELD_FETCH_MAX_JOBS_PER_COMPANY + ": " + fetchWebMaxJobsPerCompany);
        for (Map.Entry<String, Integer> entry : personaExitCodes.entrySet()) {
            System.out.println(FIELD_PERSONA_EXIT + ": " + entry.getKey() + "=" + entry.getValue());
        }
    }

    private List<PersonaRunResult> runPersonaRequestsSequentially(List<PersonaRunRequest> requests) {
        List<PersonaRunResult> results = new ArrayList<>(requests.size());
        for (PersonaRunRequest request : requests) {
            List<String> args = new ArrayList<>(request.args());
            args.add("--jobs-output-dir=" + request.jobsOutputDir());
            int exitCode = new CommandLine(new PipelineRunCommand()).execute(args.toArray(String[]::new));
            results.add(new PersonaRunResult(request.personaId(), request.jobsOutputDir(), exitCode));
            if (exitCode != 0 && failFast) {
                break;
            }
        }
        return List.copyOf(results);
    }

    private List<PersonaRunResult> runPersonaRequestsConcurrently(List<PersonaRunRequest> requests) {
        int poolSize = Math.min(personaConcurrency, requests.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            List<Future<PersonaRunResult>> futures = new ArrayList<>(requests.size());
            for (PersonaRunRequest request : requests) {
                futures.add(executor.submit(() -> {
                    List<String> args = new ArrayList<>(request.args());
                    args.add("--jobs-output-dir=" + request.jobsOutputDir());
                    int exitCode = new CommandLine(new PipelineRunCommand()).execute(args.toArray(String[]::new));
                    return new PersonaRunResult(request.personaId(), request.jobsOutputDir(), exitCode);
                }));
            }

            List<PersonaRunResult> results = new ArrayList<>(requests.size());
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for persona runs.", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException(
                            "Failed to execute persona run '" + requests.get(i).personaId() + "'.",
                            e.getCause()
                    );
                }
            }
            return List.copyOf(results);
        }
    }

    private record PersonaRunRequest(
            String personaId,
            Path jobsOutputDir,
            List<String> args
    ) {
    }

    private record PersonaRunResult(
            String personaId,
            Path jobsOutputDir,
            int exitCode
    ) {
    }
}
