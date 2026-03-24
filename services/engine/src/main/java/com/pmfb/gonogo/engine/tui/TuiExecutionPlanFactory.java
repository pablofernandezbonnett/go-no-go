package com.pmfb.gonogo.engine.tui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TuiExecutionPlanFactory {
    static final String DEFAULT_CONFIG_DIR = "config";
    static final String DEFAULT_PERSONA_ID = "product_expat_engineer";
    static final int DEFAULT_CHECK_TIMEOUT_SECONDS = 20;
    static final int DEFAULT_PIPELINE_FETCH_MAX_JOBS = 5;
    static final int DEFAULT_RUN_ALL_FETCH_MAX_JOBS = 12;
    static final int DEFAULT_FETCH_WEB_MAX_JOBS = 5;
    static final int DEFAULT_PERSONA_CONCURRENCY = 1;
    static final int DEFAULT_FETCH_WEB_MAX_CONCURRENCY_PER_HOST = 1;
    private static final String RAW_TEXT_PLACEHOLDER = "<multiline raw text>";
    private static final String MODE_URL = "url";
    private static final String MODE_RAW_TEXT = "raw-text";
    private static final String MODE_RAW_FILE = "raw-file";
    private static final String MODE_JOB_YAML = "job-yaml";

    public TuiExecutionPlan buildRunAll(TuiConfigContext context, RunAllRequest request) {
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add("pipeline");
        commandArgs.add("run-all");
        addConfigDir(commandArgs, context.configDir());
        addJoinedIds(commandArgs, "--persona-ids", request.personaIds(), context.personaIds());
        addCandidateProfile(commandArgs, request.candidateProfileId());
        addJoinedIds(commandArgs, "--company-ids", request.companyIds(), context.companyIds());
        addBooleanFlag(commandArgs, "--skip-fetch-web", request.skipFetchWeb());
        addBooleanFlag(commandArgs, "--incremental-only", request.incrementalOnly());
        addIntOption(
                commandArgs,
                "--fetch-web-max-jobs-per-company",
                request.fetchWebMaxJobsPerCompany(),
                DEFAULT_RUN_ALL_FETCH_MAX_JOBS
        );
        addLongOption(
                commandArgs,
                "--fetch-web-request-delay-millis",
                request.fetchWebRequestDelayMillis(),
                context.fetchWebDefaults().requestDelayMillis()
        );
        addIntOption(
                commandArgs,
                "--fetch-web-max-concurrency",
                request.fetchWebMaxConcurrency(),
                context.fetchWebDefaults().maxConcurrency()
        );
        addIntOption(
                commandArgs,
                "--evaluate-max-concurrency",
                request.evaluateMaxConcurrency(),
                context.evaluationDefaults().maxConcurrency()
        );
        addIntOption(
                commandArgs,
                "--persona-concurrency",
                request.personaConcurrency(),
                DEFAULT_PERSONA_CONCURRENCY
        );
        return new TuiExecutionPlan("Run all", commandArgs, commandArgs);
    }

    public TuiExecutionPlan buildPipelineRun(TuiConfigContext context, PipelineRunRequest request) {
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add("pipeline");
        commandArgs.add("run");
        addConfigDir(commandArgs, context.configDir());
        addStringOption(commandArgs, "--persona", request.personaId(), DEFAULT_PERSONA_ID);
        addCandidateProfile(commandArgs, request.candidateProfileId());
        addBooleanFlag(commandArgs, "--fetch-web-first", request.fetchWebFirst());
        if (request.fetchWebFirst()) {
            addJoinedIds(commandArgs, "--company-ids", request.fetchWebCompanyIds(), context.companyIds());
        }
        addBooleanFlag(commandArgs, "--incremental-only", request.incrementalOnly());
        addIntOption(
                commandArgs,
                "--fetch-web-max-jobs-per-company",
                request.fetchWebMaxJobsPerCompany(),
                DEFAULT_PIPELINE_FETCH_MAX_JOBS
        );
        addLongOption(
                commandArgs,
                "--fetch-web-request-delay-millis",
                request.fetchWebRequestDelayMillis(),
                context.fetchWebDefaults().requestDelayMillis()
        );
        addIntOption(
                commandArgs,
                "--fetch-web-max-concurrency",
                request.fetchWebMaxConcurrency(),
                context.fetchWebDefaults().maxConcurrency()
        );
        addIntOption(
                commandArgs,
                "--evaluate-max-concurrency",
                request.evaluateMaxConcurrency(),
                context.evaluationDefaults().maxConcurrency()
        );
        return new TuiExecutionPlan("Pipeline run", commandArgs, commandArgs);
    }

    public TuiExecutionPlan buildQuickCheck(TuiConfigContext context, QuickCheckRequest request) {
        List<String> commandArgs = new ArrayList<>();
        List<String> displayArgs = new ArrayList<>();
        commandArgs.add("check");
        displayArgs.add("check");
        addConfigDir(commandArgs, displayArgs, context.configDir());
        addStringOption(commandArgs, displayArgs, "--persona", request.personaId(), DEFAULT_PERSONA_ID);
        addCandidateProfile(commandArgs, displayArgs, request.candidateProfileId());
        addStringOption(commandArgs, displayArgs, "--mode", request.mode().cliValue(), "");
        if (request.mode() == QuickCheckMode.URL) {
            addIntOption(commandArgs, displayArgs, "--timeout-seconds", request.timeoutSeconds(), DEFAULT_CHECK_TIMEOUT_SECONDS);
            addOptionalString(commandArgs, displayArgs, "--user-agent", request.userAgent());
        }
        commandArgs.add(request.inputValue());
        displayArgs.add(request.mode() == QuickCheckMode.RAW_TEXT ? RAW_TEXT_PLACEHOLDER : request.inputValue());
        return new TuiExecutionPlan("Quick check", commandArgs, displayArgs);
    }

    public TuiExecutionPlan buildFetchWeb(TuiConfigContext context, FetchWebRequest request) {
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add("fetch-web");
        addConfigDir(commandArgs, context.configDir());
        addJoinedIds(commandArgs, "--company-ids", request.companyIds(), context.companyIds());
        addIntOption(commandArgs, "--max-jobs-per-company", request.maxJobsPerCompany(), DEFAULT_FETCH_WEB_MAX_JOBS);
        addLongOption(
                commandArgs,
                "--request-delay-millis",
                request.requestDelayMillis(),
                context.fetchWebDefaults().requestDelayMillis()
        );
        addIntOption(
                commandArgs,
                "--max-concurrency",
                request.maxConcurrency(),
                context.fetchWebDefaults().maxConcurrency()
        );
        addIntOption(
                commandArgs,
                "--max-concurrency-per-host",
                request.maxConcurrencyPerHost(),
                DEFAULT_FETCH_WEB_MAX_CONCURRENCY_PER_HOST
        );
        addBooleanFlag(commandArgs, "--disable-cache", request.disableCache());
        addBooleanFlag(commandArgs, "--disable-company-context", request.disableCompanyContext());
        return new TuiExecutionPlan("Fetch web", commandArgs, commandArgs);
    }

    public TuiExecutionPlan buildConfigValidate(TuiConfigContext context) {
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add("config");
        commandArgs.add("validate");
        addConfigDir(commandArgs, context.configDir());
        return new TuiExecutionPlan("Config validate", commandArgs, commandArgs);
    }

    private void addConfigDir(List<String> commandArgs, Path configDir) {
        addConfigDir(commandArgs, commandArgs, configDir);
    }

    private void addConfigDir(List<String> commandArgs, List<String> displayArgs, Path configDir) {
        if (!DEFAULT_CONFIG_DIR.equals(configDir.toString())) {
            addOptionalString(commandArgs, displayArgs, "--config-dir", configDir.toString());
        }
    }

    private void addCandidateProfile(List<String> commandArgs, String value) {
        addCandidateProfile(commandArgs, commandArgs, value);
    }

    private void addCandidateProfile(List<String> commandArgs, List<String> displayArgs, String value) {
        addOptionalString(commandArgs, displayArgs, "--candidate-profile", value);
    }

    private void addJoinedIds(List<String> commandArgs, String optionName, List<String> selectedIds, List<String> allIds) {
        if (selectedIds.isEmpty() || selectedIds.equals(allIds)) {
            return;
        }
        commandArgs.add(optionName + "=" + String.join(",", selectedIds));
    }

    private void addBooleanFlag(List<String> commandArgs, String optionName, boolean value) {
        if (value) {
            commandArgs.add(optionName);
        }
    }

    private void addIntOption(List<String> commandArgs, String optionName, int value, int defaultValue) {
        addIntOption(commandArgs, commandArgs, optionName, value, defaultValue);
    }

    private void addIntOption(
            List<String> commandArgs,
            List<String> displayArgs,
            String optionName,
            int value,
            int defaultValue
    ) {
        if (value != defaultValue) {
            String rendered = optionName + "=" + value;
            addToken(commandArgs, displayArgs, rendered);
        }
    }

    private void addLongOption(List<String> commandArgs, String optionName, long value, long defaultValue) {
        if (value != defaultValue) {
            commandArgs.add(optionName + "=" + value);
        }
    }

    private void addStringOption(List<String> commandArgs, String optionName, String value, String defaultValue) {
        addStringOption(commandArgs, commandArgs, optionName, value, defaultValue);
    }

    private void addStringOption(
            List<String> commandArgs,
            List<String> displayArgs,
            String optionName,
            String value,
            String defaultValue
    ) {
        if (value != null && !value.isBlank() && !value.equals(defaultValue)) {
            String rendered = optionName + "=" + value;
            addToken(commandArgs, displayArgs, rendered);
        }
    }

    private void addOptionalString(List<String> commandArgs, List<String> displayArgs, String optionName, String value) {
        if (value != null && !value.isBlank()) {
            String rendered = optionName + "=" + value;
            addToken(commandArgs, displayArgs, rendered);
        }
    }

    private void addToken(List<String> commandArgs, List<String> displayArgs, String value) {
        commandArgs.add(value);
        if (commandArgs != displayArgs) {
            displayArgs.add(value);
        }
    }

    public record RunAllRequest(
            List<String> personaIds,
            String candidateProfileId,
            List<String> companyIds,
            boolean skipFetchWeb,
            boolean incrementalOnly,
            int fetchWebMaxJobsPerCompany,
            long fetchWebRequestDelayMillis,
            int fetchWebMaxConcurrency,
            int evaluateMaxConcurrency,
            int personaConcurrency
    ) {
    }

    public record PipelineRunRequest(
            String personaId,
            String candidateProfileId,
            boolean fetchWebFirst,
            List<String> fetchWebCompanyIds,
            boolean incrementalOnly,
            int fetchWebMaxJobsPerCompany,
            long fetchWebRequestDelayMillis,
            int fetchWebMaxConcurrency,
            int evaluateMaxConcurrency
    ) {
    }

    public record QuickCheckRequest(
            QuickCheckMode mode,
            String personaId,
            String candidateProfileId,
            String inputValue,
            int timeoutSeconds,
            String userAgent
    ) {
    }

    public record FetchWebRequest(
            List<String> companyIds,
            int maxJobsPerCompany,
            long requestDelayMillis,
            int maxConcurrency,
            int maxConcurrencyPerHost,
            boolean disableCache,
            boolean disableCompanyContext
    ) {
    }

    public enum QuickCheckMode {
        URL(MODE_URL, "URL"),
        RAW_TEXT(MODE_RAW_TEXT, "Raw text"),
        RAW_FILE(MODE_RAW_FILE, "Text file"),
        JOB_YAML(MODE_JOB_YAML, "Job YAML");

        private final String cliValue;
        private final String label;

        QuickCheckMode(String cliValue, String label) {
            this.cliValue = cliValue;
            this.label = label;
        }

        public String cliValue() {
            return cliValue;
        }

        public String label() {
            return label;
        }
    }
}
