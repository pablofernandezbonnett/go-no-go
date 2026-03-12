package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.FetchWebRuntimeConfig;
import com.pmfb.gonogo.engine.config.RuntimeOptionResolver;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.job.CareerPageFetchService;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "fetch-web",
        description = "Fetch selected company career pages and generate normalized raw job text files."
)
public final class FetchWebCommand implements Callable<Integer> {
    private final CareerPageFetchService fetchService;

    @Spec
    private CommandSpec spec;

    public FetchWebCommand() {
        this(new CareerPageFetchService());
    }

    public FetchWebCommand(CareerPageFetchService fetchService) {
        this.fetchService = fetchService;
    }

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Option(
            names = {"--output-dir"},
            description = "Directory for generated raw text files.",
            defaultValue = "output/raw"
    )
    private Path outputDir;

    @Option(
            names = {"--company-ids"},
            split = ",",
            description = "Optional comma-separated company ids from config/companies.yaml. Defaults to all companies."
    )
    private List<String> companyIds;

    @Option(
            names = {"--max-jobs-per-company"},
            description = "Maximum extracted job snippets per company.",
            defaultValue = "5"
    )
    private int maxJobsPerCompany;

    @Option(
            names = {"--timeout-seconds"},
            description = "HTTP timeout for each career page request.",
            defaultValue = "20"
    )
    private int timeoutSeconds;

    @Option(
            names = {"--user-agent"},
            description = "User-Agent used for HTTP requests.",
            defaultValue = "go-no-go-engine/0.1 (+https://local)"
    )
    private String userAgent;

    @Option(
            names = {"--retries"},
            description = "Retry attempts after first fetch failure.",
            defaultValue = "2"
    )
    private int retries;

    @Option(
            names = {"--backoff-millis"},
            description = "Base backoff in milliseconds between retries.",
            defaultValue = "300"
    )
    private long backoffMillis;

    @Option(
            names = {"--request-delay-millis"},
            description = "Minimum delay in milliseconds between requests to the same host.",
            defaultValue = "1200"
    )
    private long requestDelayMillis;

    @Option(
            names = {"--max-concurrency"},
            description = "Maximum number of companies to fetch in parallel.",
            defaultValue = "4"
    )
    private int maxConcurrency;

    @Option(
            names = {"--max-concurrency-per-host"},
            description = "Maximum number of in-flight requests allowed per host.",
            defaultValue = "1"
    )
    private int maxConcurrencyPerHost;

    @Option(
            names = {"--context-output-dir"},
            description = "Directory for extracted company context files (culture/reputation pages).",
            defaultValue = "output/company-context"
    )
    private Path contextOutputDir;

    @Option(
            names = {"--disable-company-context"},
            description = "Disable company context extraction from corporate/workplace pages.",
            defaultValue = "false"
    )
    private boolean disableCompanyContext;

    @Option(
            names = {"--robots-mode"},
            description = "Robots mode: strict, warn, off.",
            defaultValue = "strict"
    )
    private String robotsMode;

    @Option(
            names = {"--cache-dir"},
            description = "Directory to store cached career page responses.",
            defaultValue = ".cache/fetch-web"
    )
    private Path cacheDir;

    @Option(
            names = {"--cache-ttl-minutes"},
            description = "Cache freshness TTL in minutes.",
            defaultValue = "720"
    )
    private long cacheTtlMinutes;

    @Option(
            names = {"--disable-cache"},
            description = "Disable local fetch response cache.",
            defaultValue = "false"
    )
    private boolean disableCache;

    @Override
    public Integer call() {
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

        FetchWebRuntimeConfig runtimeDefaults = config.runtimeSettings().fetchWeb();
        int effectiveTimeoutSeconds = RuntimeOptionResolver.resolveInt(
                spec,
                "--timeout-seconds",
                timeoutSeconds,
                runtimeDefaults.timeoutSeconds()
        );
        String effectiveUserAgent = RuntimeOptionResolver.resolveString(
                spec,
                "--user-agent",
                userAgent,
                runtimeDefaults.userAgent()
        );
        int effectiveRetries = RuntimeOptionResolver.resolveInt(
                spec,
                "--retries",
                retries,
                runtimeDefaults.retries()
        );
        long effectiveBackoffMillis = RuntimeOptionResolver.resolveLong(
                spec,
                "--backoff-millis",
                backoffMillis,
                runtimeDefaults.backoffMillis()
        );
        long effectiveRequestDelayMillis = RuntimeOptionResolver.resolveLong(
                spec,
                "--request-delay-millis",
                requestDelayMillis,
                runtimeDefaults.requestDelayMillis()
        );
        int effectiveMaxConcurrency = RuntimeOptionResolver.resolveInt(
                spec,
                "--max-concurrency",
                maxConcurrency,
                runtimeDefaults.maxConcurrency()
        );
        int effectiveMaxConcurrencyPerHost = RuntimeOptionResolver.resolveInt(
                spec,
                "--max-concurrency-per-host",
                maxConcurrencyPerHost,
                runtimeDefaults.maxConcurrencyPerHost()
        );
        String effectiveRobotsMode = RuntimeOptionResolver.resolveString(
                spec,
                "--robots-mode",
                robotsMode,
                runtimeDefaults.robotsMode()
        );
        long effectiveCacheTtlMinutes = RuntimeOptionResolver.resolveLong(
                spec,
                "--cache-ttl-minutes",
                cacheTtlMinutes,
                runtimeDefaults.cacheTtlMinutes()
        );

        CareerPageFetchService.FetchOptions options = new CareerPageFetchService.FetchOptions(
                outputDir,
                companyIds,
                maxJobsPerCompany,
                effectiveTimeoutSeconds,
                effectiveUserAgent,
                effectiveRetries,
                effectiveBackoffMillis,
                effectiveRequestDelayMillis,
                effectiveMaxConcurrency,
                effectiveMaxConcurrencyPerHost,
                contextOutputDir,
                !disableCompanyContext,
                effectiveRobotsMode,
                cacheDir,
                effectiveCacheTtlMinutes,
                !disableCache
        );
        CareerPageFetchService.FetchOutcome outcome = fetchService.fetchToRawFiles(
                config.companies(),
                options
        );

        if (outcome.selectedCompanies() == 0) {
            System.err.println("No companies selected to fetch.");
            return 1;
        }

        for (String message : outcome.informationalMessages()) {
            System.out.println(message);
        }
        for (String message : outcome.errorMessages()) {
            System.err.println(message);
        }

        System.out.println("fetch-web summary:");
        System.out.println("companies_processed: " + outcome.selectedCompanies());
        System.out.println("companies_failed: " + outcome.companiesFailed());
        System.out.println("raw_files_generated: " + outcome.rawFilesGenerated());
        System.out.println("context_files_generated: " + outcome.contextFilesGenerated());
        System.out.println("duration_ms: " + outcome.totalDurationMillis());
        System.out.println("cache_fresh_hits: " + outcome.freshCacheHitCount());
        System.out.println("cache_misses: " + outcome.cacheMissCount());
        System.out.println("cache_stale_fallbacks: " + outcome.staleCacheFallbackCount());
        System.out.println("retries_used: " + outcome.retryCount());
        System.out.println("outgoing_requests: " + outcome.outgoingRequestCount());
        System.out.println("max_concurrency: " + effectiveMaxConcurrency);
        System.out.println("max_concurrency_per_host: " + effectiveMaxConcurrencyPerHost);
        System.out.println("output_dir: " + outputDir);
        if (!disableCompanyContext) {
            System.out.println("context_output_dir: " + contextOutputDir);
        }

        return outcome.allSelectedCompaniesFailed() ? 1 : 0;
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }
}
