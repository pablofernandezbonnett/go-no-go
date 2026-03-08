package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.report.TrendAlertSinkFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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

        List<PersonaConfig> selectedPersonas = selectPersonas(config.personas(), personaIds);
        if (selectedPersonas.isEmpty()) {
            return 1;
        }

        int succeeded = 0;
        int failed = 0;
        Map<String, Integer> personaExitCodes = new LinkedHashMap<>();
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

        for (PersonaConfig persona : selectedPersonas) {
            List<String> runArgs = buildRunArgs(persona.id(), runFetchStage, effectiveFetchCompanyIds);
            int exitCode = new CommandLine(new PipelineRunCommand()).execute(runArgs.toArray(String[]::new));
            personaExitCodes.put(persona.id(), exitCode);
            if (exitCode == 0) {
                succeeded++;
            } else {
                failed++;
                System.err.println("run-all failure: persona '" + persona.id() + "' exited with code " + exitCode + ".");
                if (failFast) {
                    break;
                }
            }
            runFetchStage = false;
        }

        printSummary(selectedPersonas.size(), succeeded, failed, personaExitCodes);
        return failed == 0 ? 0 : 1;
    }

    private List<String> buildRunArgs(String personaId, boolean includeFetchWebStage, List<String> fetchCompanyIds) {
        List<String> args = new ArrayList<>();
        args.add("--persona=" + personaId);
        args.add("--config-dir=" + configDir);
        args.add("--raw-input-dir=" + rawInputDir);
        args.add("--raw-pattern=" + rawPattern);
        args.add("--recursive");
        args.add("--jobs-output-dir=" + jobsOutputDir);
        args.add("--batch-output-dir=" + batchOutputDir);
        args.add("--weekly-output-file=" + resolveWeeklyOutputFile(personaId));
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

        if (includeFetchWebStage) {
            args.add("--fetch-web-first");
            args.add("--fetch-web-max-jobs-per-company=" + fetchWebMaxJobsPerCompany);
            args.add("--fetch-web-timeout-seconds=" + fetchWebTimeoutSeconds);
            args.add("--fetch-web-user-agent=" + fetchWebUserAgent);
            args.add("--fetch-web-retries=" + fetchWebRetries);
            args.add("--fetch-web-backoff-millis=" + fetchWebBackoffMillis);
            args.add("--fetch-web-request-delay-millis=" + fetchWebRequestDelayMillis);
            args.add("--fetch-web-robots-mode=" + fetchWebRobotsMode);
            args.add("--fetch-web-cache-dir=" + fetchWebCacheDir);
            args.add("--fetch-web-cache-ttl-minutes=" + fetchWebCacheTtlMinutes);
            if (fetchWebDisableCache) {
                args.add("--fetch-web-disable-cache");
            }
            List<String> idsToFetch = hasValues(fetchCompanyIds) ? fetchCompanyIds : companyIds;
            if (hasValues(idsToFetch)) {
                args.add("--company-ids=" + joinNonBlank(idsToFetch));
            }
        }

        return args;
    }

    private String resolveWeeklyOutputFile(String personaId) {
        String sanitized = sanitizePersonaId(personaId);
        return weeklyOutputDir.resolve(WEEKLY_FILE_PREFIX + sanitized + WEEKLY_FILE_EXTENSION).toString();
    }

    private String sanitizePersonaId(String personaId) {
        return normalize(personaId).replaceAll("[^a-z0-9_-]", "_");
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
                    } catch (Exception ignored) {
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
            Map<String, Integer> personaExitCodes
    ) {
        System.out.println("run-all completed.");
        System.out.println("personas_total: " + totalPersonas);
        System.out.println("personas_succeeded: " + succeeded);
        System.out.println("personas_failed: " + failed);
        System.out.println("raw_input_dir: " + rawInputDir);
        System.out.println("jobs_output_dir: " + jobsOutputDir);
        System.out.println("batch_output_dir: " + batchOutputDir);
        System.out.println("weekly_output_dir: " + weeklyOutputDir);
        System.out.println("fetch_max_jobs_per_company: " + fetchWebMaxJobsPerCompany);
        for (Map.Entry<String, Integer> entry : personaExitCodes.entrySet()) {
            System.out.println("persona_exit: " + entry.getKey() + "=" + entry.getValue());
        }
    }
}
