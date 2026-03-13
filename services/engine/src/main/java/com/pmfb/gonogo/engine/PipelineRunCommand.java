package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.ConfigSelections;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.FetchWebRuntimeConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.RuntimeOptionResolver;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.decision.DecisionEngineV1;
import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.decision.Verdict;
import com.pmfb.gonogo.engine.job.CareerPageFetchService;
import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.job.JobInputYamlWriter;
import com.pmfb.gonogo.engine.job.RawJobExtractionResult;
import com.pmfb.gonogo.engine.job.RawJobParser;
import com.pmfb.gonogo.engine.report.BatchEvaluationError;
import com.pmfb.gonogo.engine.report.BatchEvaluationItem;
import com.pmfb.gonogo.engine.report.BatchEvaluationReport;
import com.pmfb.gonogo.engine.report.BatchReportWriter;
import com.pmfb.gonogo.engine.report.ChangeStatuses;
import com.pmfb.gonogo.engine.report.JobChangeDetector;
import com.pmfb.gonogo.engine.report.RemovedJobItem;
import com.pmfb.gonogo.engine.report.RunTrendAlertEngine;
import com.pmfb.gonogo.engine.report.RunTrendAnalyzer;
import com.pmfb.gonogo.engine.report.RunTrendSnapshot;
import com.pmfb.gonogo.engine.report.RunTrendStore;
import com.pmfb.gonogo.engine.report.TrendAlertSink;
import com.pmfb.gonogo.engine.report.TrendAlertSinkFactory;
import com.pmfb.gonogo.engine.report.WeeklyDigestData;
import com.pmfb.gonogo.engine.report.WeeklyDigestGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "run",
        description = "Run full pipeline: raw text normalization, batch evaluation, and weekly digest."
)
public final class PipelineRunCommand implements Callable<Integer> {
    private static final String LABEL_FETCH_WEB_STAGE_SUMMARY = "fetch-web stage summary:";
    private static final String LABEL_PIPELINE_COMPLETED = "Pipeline completed.";
    private static final String FIELD_COMPANIES_PROCESSED = "companies_processed";
    private static final String FIELD_COMPANIES_FAILED = "companies_failed";
    private static final String FIELD_RAW_FILES_GENERATED = "raw_files_generated";
    private static final String FIELD_CONTEXT_FILES_GENERATED = "context_files_generated";
    private static final String FIELD_DURATION_MS = "duration_ms";
    private static final String FIELD_CACHE_FRESH_HITS = "cache_fresh_hits";
    private static final String FIELD_CACHE_MISSES = "cache_misses";
    private static final String FIELD_CACHE_STALE_FALLBACKS = "cache_stale_fallbacks";
    private static final String FIELD_RETRIES_USED = "retries_used";
    private static final String FIELD_OUTGOING_REQUESTS = "outgoing_requests";
    private static final String FIELD_MAX_CONCURRENCY = "max_concurrency";
    private static final String FIELD_MAX_CONCURRENCY_PER_HOST = "max_concurrency_per_host";
    private static final String FIELD_RAW_INPUT_DIR = "raw_input_dir";
    private static final String FIELD_COMPANY_CONTEXT_DIR = "company_context_dir";
    private static final String FIELD_FETCH_DURATION_MS = "fetch_duration_ms";
    private static final String FIELD_NORMALIZE_DURATION_MS = "normalize_duration_ms";
    private static final String FIELD_EVALUATE_DURATION_MS = "evaluate_duration_ms";
    private static final String FIELD_EVALUATE_MAX_CONCURRENCY = "evaluate_max_concurrency";
    private static final String FIELD_REPORT_DURATION_MS = "report_duration_ms";
    private static final String FIELD_TOTAL_RAW_FILES = "total_raw_files";
    private static final String FIELD_WARNINGS = "warnings";
    private static final String FIELD_EVALUATED = "evaluated";
    private static final String FIELD_FAILED = "failed";
    private static final String FIELD_GO = "go";
    private static final String FIELD_GO_WITH_CAUTION = "go_with_caution";
    private static final String FIELD_NO_GO = "no_go";
    private static final String FIELD_NEW = "new";
    private static final String FIELD_UPDATED = "updated";
    private static final String FIELD_UNCHANGED = "unchanged";
    private static final String FIELD_REMOVED = "removed";
    private static final String FIELD_BATCH_JSON = "batch_json";
    private static final String FIELD_BATCH_MARKDOWN = "batch_markdown";
    private static final String FIELD_WEEKLY_DIGEST = "weekly_digest";
    private static final String FIELD_TREND_HISTORY = "trend_history";
    private static final String FIELD_TREND_ALERTS = "trend_alerts";
    private static final String FIELD_TREND_ALERT_DISPATCH = "trend_alert_dispatch";

    private final CareerPageFetchService fetchService;
    private final JobEvaluationService jobEvaluationService;

    public PipelineRunCommand() {
        this(new DecisionEngineV1(), new CareerPageFetchService());
    }

    public PipelineRunCommand(DecisionEngineV1 engine, CareerPageFetchService fetchService) {
        this(
                engine,
                fetchService,
                (job, persona, candidateProfile, config, externalContext) -> engine.evaluate(
                        job,
                        persona,
                        candidateProfile,
                        config,
                        externalContext
                )
        );
    }

    PipelineRunCommand(
            DecisionEngineV1 engine,
            CareerPageFetchService fetchService,
            JobEvaluationService jobEvaluationService
    ) {
        this.fetchService = fetchService;
        this.jobEvaluationService = jobEvaluationService;
    }

    private static final String DEFAULT_ALERT_SINKS = TrendAlertSinkFactory.SINK_NONE;

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"--persona"},
            description = "Persona id from config/personas.yaml",
            defaultValue = "product_expat_engineer"
    )
    private String personaId;

    @Option(
            names = {"--candidate-profile"},
            description = "Optional candidate profile id from config/candidate-profiles (auto-selects when exactly one exists)."
    )
    private String candidateProfileId;

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
            names = {"--recursive"},
            description = "Walk raw input directory recursively.",
            defaultValue = "false"
    )
    private boolean recursive;

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

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
            names = {"--batch-output-json"},
            description = "Optional explicit JSON path for batch report."
    )
    private Path batchOutputJson;

    @Option(
            names = {"--batch-output-markdown"},
            description = "Optional explicit Markdown path for batch report."
    )
    private Path batchOutputMarkdown;

    @Option(
            names = {"--weekly-output-file"},
            description = "Output markdown file for weekly digest.",
            defaultValue = "output/weekly.md"
    )
    private Path weeklyOutputFile;

    @Option(
            names = {"--top-per-section"},
            description = "Max items to include in each digest verdict section.",
            defaultValue = "5"
    )
    private int topPerSection;

    @Option(
            names = {"--trend-history-file"},
            description = "State file used to persist run-level trend snapshots."
    )
    private Path trendHistoryFile;

    @Option(
            names = {"--trend-history-max-runs"},
            description = "Maximum number of run snapshots to retain in trend history.",
            defaultValue = "26"
    )
    private int trendHistoryMaxRuns;

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
            names = {"--alert-sinks"},
            split = ",",
            description = "Comma-separated alert sinks: none, stdout, json-file.",
            defaultValue = DEFAULT_ALERT_SINKS
    )
    private List<String> alertSinks;

    @Option(
            names = {"--alert-json-file"},
            description = "Output JSON file used by the json-file alert sink."
    )
    private Path alertJsonFile;

    @Option(
            names = {"--fail-on-warnings"},
            description = "Return non-zero exit code when normalization warnings are present.",
            defaultValue = "false"
    )
    private boolean failOnWarnings;

    @Option(
            names = {"--change-state-file"},
            description = "State file used to track job changes between pipeline runs."
    )
    private Path changeStateFile;

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
            names = {"--fetch-web-first"},
            description = "Fetch selected career pages first and write raw text files into --raw-input-dir.",
            defaultValue = "false"
    )
    private boolean fetchWebFirst;

    @Option(
            names = {"--fetch-web-company-ids", "--company-ids"},
            split = ",",
            description = "Optional comma-separated company ids to fetch. Defaults to all companies when fetch-web runs. If provided, fetch-web stage is enabled automatically."
    )
    private List<String> fetchWebCompanyIds;

    @Option(
            names = {"--fetch-web-max-jobs-per-company"},
            description = "Maximum extracted job snippets per company for the fetch stage.",
            defaultValue = "5"
    )
    private int fetchWebMaxJobsPerCompany;

    @Option(
            names = {"--fetch-web-timeout-seconds"},
            description = "HTTP timeout in seconds for each fetch-web request in pipeline.",
            defaultValue = "20"
    )
    private int fetchWebTimeoutSeconds;

    @Option(
            names = {"--fetch-web-user-agent"},
            description = "User-Agent used for fetch-web requests in pipeline.",
            defaultValue = "go-no-go-engine/0.1 (+https://local)"
    )
    private String fetchWebUserAgent;

    @Option(
            names = {"--fetch-web-retries"},
            description = "Retry attempts after first fetch failure in fetch-web stage.",
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
            description = "Maximum number of companies to fetch in parallel during fetch-web stage.",
            defaultValue = "4"
    )
    private int fetchWebMaxConcurrency;

    @Option(
            names = {"--fetch-web-max-concurrency-per-host"},
            description = "Maximum number of in-flight requests allowed per host during fetch-web stage.",
            defaultValue = "1"
    )
    private int fetchWebMaxConcurrencyPerHost;

    @Option(
            names = {"--evaluate-max-concurrency"},
            description = "Maximum number of jobs to evaluate in parallel during the pipeline evaluation stage.",
            defaultValue = "4"
    )
    private int evaluateMaxConcurrency;

    @Option(
            names = {"--company-context-dir"},
            description = "Directory for company context files used in evaluation.",
            defaultValue = "output/company-context"
    )
    private Path companyContextDir;

    @Option(
            names = {"--disable-company-context"},
            description = "Disable company context enrichment from company-context files.",
            defaultValue = "false"
    )
    private boolean disableCompanyContext;

    @Option(
            names = {"--fetch-web-robots-mode"},
            description = "Robots mode for fetch stage: strict, warn, off.",
            defaultValue = "strict"
    )
    private String fetchWebRobotsMode;

    @Option(
            names = {"--fetch-web-cache-dir"},
            description = "Directory for fetch-web response cache in pipeline.",
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
            description = "Disable fetch-web response cache in pipeline.",
            defaultValue = "false"
    )
    private boolean fetchWebDisableCache;

    @Option(
            names = {"--suppress-summary-output"},
            description = "Internal: suppress pipeline summary output.",
            hidden = true,
            defaultValue = "false"
    )
    private boolean suppressSummaryOutput;

    @Override
    public Integer call() {
        long pipelineStartedAtMillis = Instant.now().toEpochMilli();
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

        Optional<PersonaConfig> persona = ConfigSelections.findPersona(config.personas(), personaId);
        if (persona.isEmpty()) {
            System.err.println("Unknown persona id '" + personaId + "'.");
            System.err.println("Available personas:");
            for (PersonaConfig item : config.personas()) {
                System.err.println(" - " + item.id());
            }
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
        CandidateProfileConfig candidateProfile = candidateProfileResolution.profile().orElse(null);
        String effectiveCandidateProfileId = effectiveCandidateProfileId(candidateProfile);
        if (incrementalOnly && disableChangeDetection) {
            System.err.println("--incremental-only requires change detection.");
            System.err.println("Remove --disable-change-detection or disable incremental mode.");
            return 1;
        }
        if (trendHistoryMaxRuns < 1) {
            System.err.println("--trend-history-max-runs must be at least 1.");
            return 1;
        }

        boolean effectiveFetchWebFirst = fetchWebFirst || hasRequestedCompanyIds();
        long fetchDurationMillis = 0;
        if (effectiveFetchWebFirst) {
            long fetchStartedAtMillis = Instant.now().toEpochMilli();
            int fetchExit = runFetchWebStage(config);
            fetchDurationMillis = Instant.now().toEpochMilli() - fetchStartedAtMillis;
            if (fetchExit != 0) {
                return fetchExit;
            }
        }

        long normalizeStartedAtMillis = Instant.now().toEpochMilli();
        List<Path> rawFiles = collectRawInputFiles(recursive || effectiveFetchWebFirst);
        if (rawFiles.isEmpty()) {
            System.err.println(
                    "No raw files found in '" + rawInputDir + "' with pattern '" + rawPattern + "'."
            );
            System.err.println("Hint: provide --company-ids to run fetch + evaluate in one command.");
            return 1;
        }

        RawJobParser parser = new RawJobParser();
        JobInputYamlWriter yamlWriter = new JobInputYamlWriter();
        List<PreparedJob> preparedJobs = new ArrayList<>();
        List<BatchEvaluationItem> items = new ArrayList<>();
        List<BatchEvaluationError> errors = new ArrayList<>();
        List<RemovedJobItem> removedItems = List.of();
        List<String> warningMessages = new ArrayList<>();
        List<String> changeWarnings = new ArrayList<>();
        int warningCount = 0;
        int newCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;
        int removedCount = 0;
        int trendAlertCount = 0;
        List<TrendAlertSink.DispatchResult> trendDispatchResults = List.of();

        for (Path rawFile : rawFiles) {
            String rawText;
            try {
                rawText = Files.readString(rawFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                errors.add(new BatchEvaluationError(
                        relativize(rawInputDir, rawFile),
                        List.of("Failed to read raw input file: " + e.getMessage())
                ));
                continue;
            }

            RawJobExtractionResult extraction = parser.parse(rawText, null, null);
            warningCount += extraction.warnings().size();
            if (!extraction.warnings().isEmpty()) {
                String source = relativize(rawInputDir, rawFile);
                for (String warning : extraction.warnings()) {
                    warningMessages.add(source + ": " + warning);
                }
            }

            Path yamlOutputFile = buildYamlOutputPath(rawFile);
            try {
                yamlWriter.write(yamlOutputFile, extraction.jobInput());
            } catch (IOException e) {
                errors.add(new BatchEvaluationError(
                        relativize(rawInputDir, rawFile),
                        List.of("Failed to write generated job YAML: " + e.getMessage())
                ));
                continue;
            }

            preparedJobs.add(new PreparedJob(
                    relativize(jobsOutputDir, yamlOutputFile),
                    extraction.jobInput()
            ));
        }

        List<EvaluableJob> jobsToEvaluate;
        if (!disableChangeDetection) {
            Path stateFile = resolveChangeStateFile(persona.get().id(), effectiveCandidateProfileId);
            try {
                JobChangeDetector detector = new JobChangeDetector();
                List<JobChangeDetector.CandidateJob> candidates = preparedJobs.stream()
                        .map(job -> new JobChangeDetector.CandidateJob(job.sourceFile(), job.jobInput()))
                        .toList();
                JobChangeDetector.ClassificationResult classification = detector.classifyAndPersist(candidates, stateFile);
                removedItems = classification.removedItems();
                newCount = classification.newCount();
                updatedCount = classification.updatedCount();
                unchangedCount = classification.unchangedCount();
                removedCount = classification.removedCount();
                changeWarnings = classification.warnings();
                jobsToEvaluate = classification.jobs().stream()
                        .map(job -> new EvaluableJob(
                                job.sourceFile(),
                                job.job(),
                                job.jobKey(),
                                job.fingerprint(),
                                job.changeStatus()
                        ))
                        .filter(job -> !incrementalOnly || isChangedStatus(job.changeStatus()))
                        .toList();
            } catch (IOException e) {
                System.err.println("Failed to persist change state file: " + e.getMessage());
                return 1;
            }
        } else {
            unchangedCount = preparedJobs.size();
            jobsToEvaluate = preparedJobs.stream()
                    .map(job -> new EvaluableJob(
                            job.sourceFile(),
                            job.jobInput(),
                            "",
                            "",
                            ChangeStatuses.UNCHANGED
                    ))
                    .toList();
        }

        Map<String, String> companyContextById = loadCompanyContextById(config.companies());
        long normalizeDurationMillis = Instant.now().toEpochMilli() - normalizeStartedAtMillis;
        int effectiveEvaluateMaxConcurrency = RuntimeOptionResolver.resolveInt(
                spec,
                "--evaluate-max-concurrency",
                evaluateMaxConcurrency,
                config.runtimeSettings().evaluation().maxConcurrency()
        );
        if (effectiveEvaluateMaxConcurrency < 1) {
            System.err.println("--evaluate-max-concurrency must be at least 1.");
            return 1;
        }
        long evaluateStartedAtMillis = Instant.now().toEpochMilli();
        try {
            items.addAll(evaluateJobs(
                    jobsToEvaluate,
                    persona.get(),
                    candidateProfile,
                    config,
                    companyContextById,
                    effectiveEvaluateMaxConcurrency
            ));
        } catch (JobEvaluationFailedException e) {
            System.err.println("Failed to evaluate job '" + e.sourceFile() + "': " + e.getCause().getMessage());
            return 1;
        }
        long evaluateDurationMillis = Instant.now().toEpochMilli() - evaluateStartedAtMillis;

        long reportStartedAtMillis = Instant.now().toEpochMilli();
        BatchEvaluationReport batchReport = buildBatchReport(
                persona.get().id(),
                effectiveCandidateProfileId,
                rawFiles.size(),
                items,
                removedItems,
                errors,
                newCount,
                updatedCount,
                unchangedCount,
                removedCount
        );
        BatchReportWriter batchWriter = new BatchReportWriter();

        Path batchJsonPath = batchOutputJson != null
                ? batchOutputJson
                : batchOutputDir.resolve(batchWriter.defaultJsonFileName(
                        persona.get().id(),
                        effectiveCandidateProfileId
                ));
        Path batchMarkdownPath = batchOutputMarkdown != null
                ? batchOutputMarkdown
                : batchOutputDir.resolve(batchWriter.defaultMarkdownFileName(
                        persona.get().id(),
                        effectiveCandidateProfileId
                ));

        try {
            batchWriter.writeJson(batchJsonPath, batchReport);
            batchWriter.writeMarkdown(batchMarkdownPath, batchReport);
        } catch (IOException e) {
            System.err.println("Failed to write batch reports: " + e.getMessage());
            return 1;
        }

        WeeklyDigestGenerator digestGenerator = new WeeklyDigestGenerator();
        WeeklyDigestData digestData = digestGenerator.fromBatchReport(batchJsonPath.toString(), batchReport);
        String digestMarkdown = digestGenerator.toMarkdown(digestData, topPerSection, persona.get().rankingStrategy());
        Path resolvedTrendHistoryFile = null;
        if (!disableTrendHistory) {
            resolvedTrendHistoryFile = resolveTrendHistoryFile(persona.get().id(), effectiveCandidateProfileId);
            RunTrendAnalyzer trendAnalyzer = new RunTrendAnalyzer();
            RunTrendAlertEngine trendAlertEngine = new RunTrendAlertEngine();
            RunTrendStore trendStore = new RunTrendStore();
            RunTrendSnapshot previous = trendStore.loadLatest(resolvedTrendHistoryFile);
            RunTrendSnapshot current = trendAnalyzer.fromBatchReport(batchReport);
            String trendMarkdown = trendAnalyzer.toMarkdown(previous, current);
            if (!trendMarkdown.isBlank()) {
                digestMarkdown = digestMarkdown + "\n\n" + trendMarkdown;
            }
            if (!disableTrendAlerts) {
                var alerts = trendAlertEngine.evaluate(previous, current);
                trendAlertCount = alerts.size();
                String alertsMarkdown = trendAlertEngine.toMarkdown(alerts);
                if (!alertsMarkdown.isBlank()) {
                    digestMarkdown = digestMarkdown + "\n\n" + alertsMarkdown;
                }

                try {
                    trendDispatchResults = dispatchTrendAlerts(
                            alerts,
                            new com.pmfb.gonogo.engine.report.TrendAlertDispatchContext(
                                    persona.get().id(),
                                    effectiveCandidateProfileId,
                                    batchReport.generatedAt(),
                                    batchJsonPath,
                                    weeklyOutputFile
                            )
                    );
                } catch (IOException | IllegalArgumentException e) {
                    System.err.println("Failed to dispatch trend alerts: " + e.getMessage());
                    return 1;
                }
            }
            try {
                trendStore.append(resolvedTrendHistoryFile, current, trendHistoryMaxRuns);
            } catch (IOException e) {
                System.err.println("Failed to persist trend history: " + e.getMessage());
                return 1;
            }
        }

        try {
            Path parent = weeklyOutputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(weeklyOutputFile, digestMarkdown, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write weekly digest: " + e.getMessage());
            return 1;
        }
        long reportDurationMillis = Instant.now().toEpochMilli() - reportStartedAtMillis;

        if (!suppressSummaryOutput) {
            printSummary(
                    batchReport,
                    warningCount,
                    batchJsonPath,
                    batchMarkdownPath,
                    weeklyOutputFile,
                    resolvedTrendHistoryFile,
                    effectiveCandidateProfileId,
                    trendAlertCount,
                    trendDispatchResults,
                    Instant.now().toEpochMilli() - pipelineStartedAtMillis,
                    fetchDurationMillis,
                    normalizeDurationMillis,
                    evaluateDurationMillis,
                    reportDurationMillis,
                    effectiveEvaluateMaxConcurrency
            );
            if (!changeWarnings.isEmpty()) {
                for (String warning : changeWarnings) {
                    System.err.println("change_detection_warning: " + warning);
                }
            }
        }
        if (failOnWarnings && warningCount > 0) {
            printWarnings(warningMessages);
            System.err.println(
                    "Pipeline finished with warnings and --fail-on-warnings is enabled."
            );
            return 2;
        }
        return 0;
    }

    private List<Path> collectRawInputFiles(boolean recursiveSearch) {
        if (!Files.exists(rawInputDir) || !Files.isDirectory(rawInputDir)) {
            return List.of();
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + rawPattern);
        int maxDepth = recursiveSearch ? Integer.MAX_VALUE : 1;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(rawInputDir, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> matchesPattern(path, matcher))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(files::add);
        } catch (IOException e) {
            return List.of();
        }
        return files;
    }

    private int runFetchWebStage(EngineConfig config) {
        FetchWebRuntimeConfig runtimeDefaults = config.runtimeSettings().fetchWeb();
        int effectiveTimeoutSeconds = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-timeout-seconds",
                fetchWebTimeoutSeconds,
                runtimeDefaults.timeoutSeconds()
        );
        String effectiveUserAgent = RuntimeOptionResolver.resolveString(
                spec,
                "--fetch-web-user-agent",
                fetchWebUserAgent,
                runtimeDefaults.userAgent()
        );
        int effectiveRetries = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-retries",
                fetchWebRetries,
                runtimeDefaults.retries()
        );
        long effectiveBackoffMillis = RuntimeOptionResolver.resolveLong(
                spec,
                "--fetch-web-backoff-millis",
                fetchWebBackoffMillis,
                runtimeDefaults.backoffMillis()
        );
        long effectiveRequestDelayMillis = RuntimeOptionResolver.resolveLong(
                spec,
                "--fetch-web-request-delay-millis",
                fetchWebRequestDelayMillis,
                runtimeDefaults.requestDelayMillis()
        );
        int effectiveMaxConcurrency = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-max-concurrency",
                fetchWebMaxConcurrency,
                runtimeDefaults.maxConcurrency()
        );
        int effectiveMaxConcurrencyPerHost = RuntimeOptionResolver.resolveInt(
                spec,
                "--fetch-web-max-concurrency-per-host",
                fetchWebMaxConcurrencyPerHost,
                runtimeDefaults.maxConcurrencyPerHost()
        );
        String effectiveRobotsMode = RuntimeOptionResolver.resolveString(
                spec,
                "--fetch-web-robots-mode",
                fetchWebRobotsMode,
                runtimeDefaults.robotsMode()
        );
        long effectiveCacheTtlMinutes = RuntimeOptionResolver.resolveLong(
                spec,
                "--fetch-web-cache-ttl-minutes",
                fetchWebCacheTtlMinutes,
                runtimeDefaults.cacheTtlMinutes()
        );

        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                rawInputDir,
                fetchWebCompanyIds,
                fetchWebMaxJobsPerCompany,
                effectiveTimeoutSeconds,
                effectiveUserAgent,
                effectiveRetries,
                effectiveBackoffMillis,
                effectiveRequestDelayMillis,
                effectiveMaxConcurrency,
                effectiveMaxConcurrencyPerHost,
                companyContextDir,
                !disableCompanyContext,
                effectiveRobotsMode,
                fetchWebCacheDir,
                effectiveCacheTtlMinutes,
                !fetchWebDisableCache
        );
        CareerPageFetchService.FetchOutcome outcome = fetchService.fetchToRawFiles(
                config.companies(),
                options
        );

        if (outcome.selectedCompanies() == 0) {
            System.err.println("No companies selected for fetch stage.");
            return 1;
        }

        for (String message : outcome.informationalMessages()) {
            System.out.println(message);
        }
        for (String message : outcome.errorMessages()) {
            System.err.println(message);
        }

        System.out.println(LABEL_FETCH_WEB_STAGE_SUMMARY);
        System.out.println(FIELD_COMPANIES_PROCESSED + ": " + outcome.selectedCompanies());
        System.out.println(FIELD_COMPANIES_FAILED + ": " + outcome.companiesFailed());
        System.out.println(FIELD_RAW_FILES_GENERATED + ": " + outcome.rawFilesGenerated());
        System.out.println(FIELD_CONTEXT_FILES_GENERATED + ": " + outcome.contextFilesGenerated());
        System.out.println(FIELD_DURATION_MS + ": " + outcome.totalDurationMillis());
        System.out.println(FIELD_CACHE_FRESH_HITS + ": " + outcome.freshCacheHitCount());
        System.out.println(FIELD_CACHE_MISSES + ": " + outcome.cacheMissCount());
        System.out.println(FIELD_CACHE_STALE_FALLBACKS + ": " + outcome.staleCacheFallbackCount());
        System.out.println(FIELD_RETRIES_USED + ": " + outcome.retryCount());
        System.out.println(FIELD_OUTGOING_REQUESTS + ": " + outcome.outgoingRequestCount());
        System.out.println(FIELD_MAX_CONCURRENCY + ": " + effectiveMaxConcurrency);
        System.out.println(FIELD_MAX_CONCURRENCY_PER_HOST + ": " + effectiveMaxConcurrencyPerHost);
        System.out.println(FIELD_RAW_INPUT_DIR + ": " + rawInputDir);
        if (!disableCompanyContext) {
            System.out.println(FIELD_COMPANY_CONTEXT_DIR + ": " + companyContextDir);
        }

        return outcome.allSelectedCompaniesFailed() ? 1 : 0;
    }

    private boolean hasRequestedCompanyIds() {
        if (fetchWebCompanyIds == null || fetchWebCompanyIds.isEmpty()) {
            return false;
        }
        for (String companyId : fetchWebCompanyIds) {
            if (companyId != null && !companyId.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> loadCompanyContextById(List<CompanyConfig> companies) {
        Map<String, String> contextById = new LinkedHashMap<>();
        for (CompanyConfig company : companies) {
            String contextText = "";
            if (!disableCompanyContext) {
                Path yamlFile = companyContextDir.resolve(sanitizeCompanyId(company.id()) + ".yaml");
                Path ymlFile = companyContextDir.resolve(sanitizeCompanyId(company.id()) + ".yml");
                Path textFile = companyContextDir.resolve(sanitizeCompanyId(company.id()) + ".txt");
                Path contextFile = null;
                if (Files.exists(yamlFile) && Files.isRegularFile(yamlFile)) {
                    contextFile = yamlFile;
                } else if (Files.exists(ymlFile) && Files.isRegularFile(ymlFile)) {
                    contextFile = ymlFile;
                } else if (Files.exists(textFile) && Files.isRegularFile(textFile)) {
                    contextFile = textFile;
                }
                if (contextFile != null) {
                    try {
                        String fileContent = Files.readString(contextFile, StandardCharsets.UTF_8);
                        contextText = isYamlFile(contextFile)
                                ? flattenCompanyContextYaml(fileContent)
                                : fileContent;
                    } catch (IOException | RuntimeException e) {
                        System.err.println("company_context_warning: failed to read " + contextFile + " (" + e.getMessage() + ")");
                    }
                }
            }
            if (contextText == null) {
                contextText = "";
            }
            contextById.put(sanitizeCompanyId(company.id()), contextText);
        }
        return Map.copyOf(contextById);
    }

    private boolean isYamlFile(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    private String flattenCompanyContextYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return "";
        }
        Object loaded = new Yaml().load(yamlContent);
        if (!(loaded instanceof Map<?, ?> root)) {
            return yamlContent;
        }

        LinkedHashMap<String, String> dedup = new LinkedHashMap<>();
        putDedupText(dedup, root.get("company_name"));

        Object summariesObj = root.get("summaries");
        if (summariesObj instanceof List<?> summaries) {
            for (Object item : summaries) {
                putDedupText(dedup, item);
            }
        }

        Object sourcesObj = root.get("sources");
        if (sourcesObj instanceof List<?> sources) {
            for (Object sourceObj : sources) {
                if (!(sourceObj instanceof Map<?, ?> source)) {
                    continue;
                }
                putDedupText(dedup, source.get("page_title"));
                putDedupText(dedup, source.get("page_description"));
            }
        }

        Object linksObj = root.get("links");
        if (linksObj instanceof List<?> links) {
            for (Object linkObj : links) {
                if (!(linkObj instanceof Map<?, ?> link)) {
                    continue;
                }
                putDedupText(dedup, link.get("title"));
            }
        }

        return String.join(" ", dedup.values());
    }

    private void putDedupText(Map<String, String> dedup, Object value) {
        if (!(value instanceof String text)) {
            return;
        }
        String normalized = text.trim();
        if (normalized.isBlank()) {
            return;
        }
        dedup.putIfAbsent(normalize(normalized), normalized);
    }

    private String resolveCompanyContext(
            String companyName,
            List<CompanyConfig> companies,
            Map<String, String> contextById
    ) {
        String normalizedCompanyName = normalize(companyName);
        if (normalizedCompanyName.isBlank()) {
            return "";
        }
        for (CompanyConfig company : companies) {
            String normalizedConfigName = normalize(company.name());
            if (normalizedConfigName.isBlank()) {
                continue;
            }
            if (normalizedConfigName.equals(normalizedCompanyName)
                    || normalizedConfigName.contains(normalizedCompanyName)
                    || normalizedCompanyName.contains(normalizedConfigName)) {
                return contextById.getOrDefault(sanitizeCompanyId(company.id()), "");
            }
        }
        return "";
    }

    private String sanitizeCompanyId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private boolean isChangedStatus(String status) {
        return ChangeStatuses.NEW.equals(status) || ChangeStatuses.UPDATED.equals(status);
    }

    private boolean matchesPattern(Path file, PathMatcher matcher) {
        Path relative = rawInputDir.relativize(file);
        return matcher.matches(relative) || matcher.matches(file.getFileName());
    }

    private Path buildYamlOutputPath(Path rawFile) {
        Path relative = rawInputDir.relativize(rawFile);
        Path parent = relative.getParent();
        String fileName = relative.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String generated = baseName + ".generated.yaml";
        return parent == null ? jobsOutputDir.resolve(generated) : jobsOutputDir.resolve(parent).resolve(generated);
    }

    private BatchEvaluationReport buildBatchReport(
            String personaIdValue,
            String candidateProfileIdValue,
            int totalFiles,
            List<BatchEvaluationItem> items,
            List<RemovedJobItem> removedItems,
            List<BatchEvaluationError> errors,
            int newCount,
            int updatedCount,
            int unchangedCount,
            int removedCount
    ) {
        int go = 0;
        int caution = 0;
        int noGo = 0;
        for (BatchEvaluationItem item : items) {
            Verdict verdict = item.evaluation().verdict();
            if (verdict == Verdict.GO) {
                go++;
            } else if (verdict == Verdict.GO_WITH_CAUTION) {
                caution++;
            } else {
                noGo++;
            }
        }
        return new BatchEvaluationReport(
                Instant.now().toString(),
                personaIdValue,
                candidateProfileIdValue,
                totalFiles,
                items.size(),
                errors.size(),
                go,
                caution,
                noGo,
                newCount,
                updatedCount,
                unchangedCount,
                removedCount,
                items,
                removedItems,
                errors
        );
    }

    private List<BatchEvaluationItem> evaluateJobs(
            List<EvaluableJob> jobsToEvaluate,
            PersonaConfig persona,
            CandidateProfileConfig candidateProfile,
            EngineConfig config,
            Map<String, String> companyContextById,
            int maxConcurrency
    ) {
        if (jobsToEvaluate.isEmpty()) {
            return List.of();
        }
        if (maxConcurrency == 1 || jobsToEvaluate.size() == 1) {
            List<BatchEvaluationItem> items = new ArrayList<>(jobsToEvaluate.size());
            for (EvaluableJob job : jobsToEvaluate) {
                items.add(evaluateJob(job, persona, candidateProfile, config, companyContextById));
            }
            return List.copyOf(items);
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency)) {
            List<Future<BatchEvaluationItem>> futures = new ArrayList<>(jobsToEvaluate.size());
            for (EvaluableJob job : jobsToEvaluate) {
                futures.add(executor.submit(() -> evaluateJob(
                        job,
                        persona,
                        candidateProfile,
                        config,
                        companyContextById
                )));
            }

            List<BatchEvaluationItem> items = new ArrayList<>(jobsToEvaluate.size());
            for (Future<BatchEvaluationItem> future : futures) {
                items.add(future.get());
            }
            return List.copyOf(items);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while evaluating jobs.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JobEvaluationFailedException failedException) {
                throw failedException;
            }
            throw new IllegalStateException("Failed to evaluate jobs.", cause);
        }
    }

    private BatchEvaluationItem evaluateJob(
            EvaluableJob job,
            PersonaConfig persona,
            CandidateProfileConfig candidateProfile,
            EngineConfig config,
            Map<String, String> companyContextById
    ) {
        try {
            String externalContext = resolveCompanyContext(
                    job.jobInput().companyName(),
                    config.companies(),
                    companyContextById
            );
            EvaluationResult evaluation = jobEvaluationService.evaluate(
                    job.jobInput(),
                    persona,
                    candidateProfile,
                    config,
                    externalContext
            );
            return new BatchEvaluationItem(
                    job.sourceFile(),
                    job.jobInput(),
                    evaluation,
                    job.jobKey(),
                    job.fingerprint(),
                    job.changeStatus()
            );
        } catch (RuntimeException e) {
            throw new JobEvaluationFailedException(job.sourceFile(), e);
        }
    }

    private Path resolveChangeStateFile(String personaIdValue, String candidateProfileIdValue) {
        if (changeStateFile != null) {
            return changeStateFile;
        }
        String sanitizedPersona = sanitizeScope(personaIdValue, candidateProfileIdValue);
        return batchOutputDir.resolve("job-change-state-" + sanitizedPersona + ".yaml");
    }

    private Path resolveTrendHistoryFile(String personaIdValue, String candidateProfileIdValue) {
        if (trendHistoryFile != null) {
            return trendHistoryFile;
        }
        String sanitizedPersona = sanitizeScope(personaIdValue, candidateProfileIdValue);
        return batchOutputDir.resolve("trend-history-" + sanitizedPersona + ".yaml");
    }

    private Path resolveAlertJsonFile(String personaIdValue, String candidateProfileIdValue) {
        if (alertJsonFile != null) {
            return alertJsonFile;
        }
        String sanitizedPersona = sanitizeScope(personaIdValue, candidateProfileIdValue);
        return batchOutputDir.resolve("trend-alerts-" + sanitizedPersona + ".json");
    }

    private List<TrendAlertSink.DispatchResult> dispatchTrendAlerts(
            List<com.pmfb.gonogo.engine.report.TrendAlert> alerts,
            com.pmfb.gonogo.engine.report.TrendAlertDispatchContext context
    ) throws IOException {
        List<TrendAlertSink> sinks = TrendAlertSinkFactory.create(
                alertSinks,
                resolveAlertJsonFile(context.personaId(), context.candidateProfileId())
        );
        List<TrendAlertSink.DispatchResult> results = new ArrayList<>();
        for (TrendAlertSink sink : sinks) {
            results.add(sink.dispatch(alerts, context));
        }
        return List.copyOf(results);
    }

    private String effectiveCandidateProfileId(CandidateProfileConfig candidateProfile) {
        return ConfigSelections.candidateProfileIdOrNone(candidateProfile);
    }

    private String sanitizeScope(String personaIdValue, String candidateProfileIdValue) {
        String sanitizedPersona = personaIdValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (candidateProfileIdValue == null
                || candidateProfileIdValue.isBlank()
                || ConfigSelections.isCandidateProfileNone(candidateProfileIdValue)) {
            return sanitizedPersona;
        }
        String sanitizedCandidate =
                candidateProfileIdValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return sanitizedPersona + "--" + sanitizedCandidate;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String relativize(Path base, Path child) {
        return base.relativize(child).toString();
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    private void printSummary(
            BatchEvaluationReport report,
            int warningCount,
            Path batchJsonPath,
            Path batchMarkdownPath,
            Path weeklyPath,
            Path trendHistoryPath,
            String candidateProfileIdValue,
            int trendAlertCount,
            List<TrendAlertSink.DispatchResult> trendDispatchResults,
            long totalDurationMillis,
            long fetchDurationMillis,
            long normalizeDurationMillis,
            long evaluateDurationMillis,
            long reportDurationMillis,
            int evaluateMaxConcurrencyValue
    ) {
        System.out.println(LABEL_PIPELINE_COMPLETED);
        System.out.println(EvaluateInputFieldKeys.PERSONA + ": " + report.personaId());
        System.out.println(EvaluateInputFieldKeys.CANDIDATE_PROFILE + ": " + candidateProfileIdValue);
        System.out.println(FIELD_DURATION_MS + ": " + totalDurationMillis);
        System.out.println(FIELD_FETCH_DURATION_MS + ": " + fetchDurationMillis);
        System.out.println(FIELD_NORMALIZE_DURATION_MS + ": " + normalizeDurationMillis);
        System.out.println(FIELD_EVALUATE_DURATION_MS + ": " + evaluateDurationMillis);
        System.out.println(FIELD_EVALUATE_MAX_CONCURRENCY + ": " + evaluateMaxConcurrencyValue);
        System.out.println(FIELD_REPORT_DURATION_MS + ": " + reportDurationMillis);
        System.out.println(FIELD_TOTAL_RAW_FILES + ": " + report.totalFiles());
        System.out.println(FIELD_WARNINGS + ": " + warningCount);
        System.out.println(FIELD_EVALUATED + ": " + report.evaluatedCount());
        System.out.println(FIELD_FAILED + ": " + report.failedCount());
        System.out.println(FIELD_GO + ": " + report.goCount());
        System.out.println(FIELD_GO_WITH_CAUTION + ": " + report.goWithCautionCount());
        System.out.println(FIELD_NO_GO + ": " + report.noGoCount());
        System.out.println(FIELD_NEW + ": " + report.newCount());
        System.out.println(FIELD_UPDATED + ": " + report.updatedCount());
        System.out.println(FIELD_UNCHANGED + ": " + report.unchangedCount());
        System.out.println(FIELD_REMOVED + ": " + report.removedCount());
        System.out.println(FIELD_BATCH_JSON + ": " + batchJsonPath);
        System.out.println(FIELD_BATCH_MARKDOWN + ": " + batchMarkdownPath);
        System.out.println(FIELD_WEEKLY_DIGEST + ": " + weeklyPath);
        if (trendHistoryPath != null) {
            System.out.println(FIELD_TREND_HISTORY + ": " + trendHistoryPath);
            System.out.println(FIELD_TREND_ALERTS + ": " + trendAlertCount);
            for (TrendAlertSink.DispatchResult result : trendDispatchResults) {
                System.out.println(
                        FIELD_TREND_ALERT_DISPATCH + ": sink="
                                + result.sinkId()
                                + ", delivered="
                                + result.delivered()
                                + ", message="
                                + result.message()
                );
            }
        }
    }

    private void printWarnings(List<String> warnings) {
        System.err.println("Normalization warnings (" + warnings.size() + "):");
        for (String warning : warnings) {
            System.err.println(" - " + warning);
        }
    }

    private record PreparedJob(
            String sourceFile,
            JobInput jobInput
    ) {
    }

    private record EvaluableJob(
            String sourceFile,
            JobInput jobInput,
            String jobKey,
            String fingerprint,
            String changeStatus
    ) {
    }

    @FunctionalInterface
    interface JobEvaluationService {
        EvaluationResult evaluate(
                JobInput jobInput,
                PersonaConfig persona,
                CandidateProfileConfig candidateProfile,
                EngineConfig config,
                String externalContext
        );
    }

    private static final class JobEvaluationFailedException extends RuntimeException {
        private final String sourceFile;

        private JobEvaluationFailedException(String sourceFile, Throwable cause) {
            super(cause);
            this.sourceFile = sourceFile;
        }

        private String sourceFile() {
            return sourceFile;
        }
    }
}
