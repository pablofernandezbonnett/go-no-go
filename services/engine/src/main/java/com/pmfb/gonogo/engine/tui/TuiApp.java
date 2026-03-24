package com.pmfb.gonogo.engine.tui;

import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.FetchWebRequest;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.PipelineRunRequest;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.QuickCheckMode;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.QuickCheckRequest;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.RunAllRequest;
import com.pmfb.gonogo.engine.tui.TuiPrompts.EndOfInputException;
import com.pmfb.gonogo.engine.tui.TuiPrompts.OptionItem;
import java.util.ArrayList;
import java.util.List;

public final class TuiApp {
    private final TuiConfigContext context;
    private final TuiPrompts prompts;
    private final TuiCommandRunner commandRunner;
    private final TuiExecutionPlanFactory planFactory;

    public TuiApp(TuiConfigContext context, TuiPrompts prompts, TuiCommandRunner commandRunner) {
        this(context, prompts, commandRunner, new TuiExecutionPlanFactory());
    }

    TuiApp(
            TuiConfigContext context,
            TuiPrompts prompts,
            TuiCommandRunner commandRunner,
            TuiExecutionPlanFactory planFactory
    ) {
        this.context = context;
        this.prompts = prompts;
        this.commandRunner = commandRunner;
        this.planFactory = planFactory;
    }

    public int run() {
        try {
            while (true) {
                printHeader();
                Action action = promptAction();
                if (action == Action.QUIT) {
                    prompts.println("Exiting TUI.");
                    return 0;
                }

                TuiExecutionPlan plan = switch (action) {
                    case RUN_ALL -> promptRunAllPlan();
                    case PIPELINE_RUN -> promptPipelineRunPlan();
                    case QUICK_CHECK -> promptQuickCheckPlan();
                    case FETCH_WEB -> promptFetchWebPlan();
                    case CONFIG_VALIDATE -> planFactory.buildConfigValidate(context);
                    case QUIT -> throw new IllegalStateException("QUIT must be handled earlier.");
                };

                prompts.blankLine();
                prompts.println("Review:");
                prompts.println(" " + plan.logicalCommand());
                prompts.println(" " + plan.gradleCommand());
                prompts.blankLine();
                if (!prompts.confirm("Run this command", true)) {
                    prompts.blankLine();
                    continue;
                }

                prompts.blankLine();
                prompts.println("Running " + plan.title() + "...");
                int exitCode = commandRunner.execute(plan.commandArgs());
                prompts.println("Exit code: " + exitCode);
                prompts.blankLine();
                prompts.pause("Press Enter to return to the menu.");
                prompts.blankLine();
            }
        } catch (EndOfInputException ignored) {
            prompts.blankLine();
            prompts.println("Input stream closed. Exiting TUI.");
            return 0;
        }
    }

    private void printHeader() {
        prompts.println("Go No-Go TUI");
        prompts.println(" config: " + context.configDir());
        prompts.println(" personas: " + context.personas().size()
                + " | companies: " + context.companies().size()
                + " | candidate profiles: " + context.candidateProfiles().size());
        prompts.blankLine();
    }

    private Action promptAction() {
        List<OptionItem> options = List.of(
                new OptionItem(Action.RUN_ALL.id, Action.RUN_ALL.description),
                new OptionItem(Action.PIPELINE_RUN.id, Action.PIPELINE_RUN.description),
                new OptionItem(Action.QUICK_CHECK.id, Action.QUICK_CHECK.description),
                new OptionItem(Action.FETCH_WEB.id, Action.FETCH_WEB.description),
                new OptionItem(Action.CONFIG_VALIDATE.id, Action.CONFIG_VALIDATE.description),
                new OptionItem(Action.QUIT.id, Action.QUIT.description)
        );
        String selected = prompts.selectOne("Action", options, Action.RUN_ALL.id);
        return Action.fromId(selected);
    }

    private TuiExecutionPlan promptRunAllPlan() {
        List<String> personaIds = prompts.selectMany("Personas", personaOptions(), true);
        String candidateProfileId = promptCandidateProfileId();
        List<String> companyIds = prompts.selectMany("Companies", companyOptions(), true);
        boolean skipFetchWeb = prompts.confirm("Skip fetch-web stage", false);
        boolean incrementalOnly = prompts.confirm("Evaluate only NEW and UPDATED jobs", false);
        boolean advanced = prompts.confirm("Change advanced run-all options", false);

        int fetchMaxJobs = TuiExecutionPlanFactory.DEFAULT_RUN_ALL_FETCH_MAX_JOBS;
        long requestDelayMillis = context.fetchWebDefaults().requestDelayMillis();
        int fetchMaxConcurrency = context.fetchWebDefaults().maxConcurrency();
        int evaluateMaxConcurrency = context.evaluationDefaults().maxConcurrency();
        int personaConcurrency = TuiExecutionPlanFactory.DEFAULT_PERSONA_CONCURRENCY;
        if (advanced) {
            fetchMaxJobs = prompts.promptInt("Fetch max jobs per company", fetchMaxJobs, 1);
            requestDelayMillis = prompts.promptLong("Fetch request delay millis", requestDelayMillis, 0);
            fetchMaxConcurrency = prompts.promptInt("Fetch max concurrency", fetchMaxConcurrency, 1);
            evaluateMaxConcurrency = prompts.promptInt("Evaluate max concurrency", evaluateMaxConcurrency, 1);
            personaConcurrency = prompts.promptInt("Persona concurrency", personaConcurrency, 1);
        }

        return planFactory.buildRunAll(
                context,
                new RunAllRequest(
                        personaIds,
                        candidateProfileId,
                        companyIds,
                        skipFetchWeb,
                        incrementalOnly,
                        fetchMaxJobs,
                        requestDelayMillis,
                        fetchMaxConcurrency,
                        evaluateMaxConcurrency,
                        personaConcurrency
                )
        );
    }

    private TuiExecutionPlan promptPipelineRunPlan() {
        String personaId = prompts.selectOne("Persona", personaOptions(), context.defaultPersonaId());
        String candidateProfileId = promptCandidateProfileId();
        boolean fetchWebFirst = prompts.confirm("Fetch career pages before evaluation", false);
        List<String> companyIds = fetchWebFirst ? prompts.selectMany("Fetch companies", companyOptions(), true) : List.of();
        boolean incrementalOnly = prompts.confirm("Evaluate only NEW and UPDATED jobs", false);
        boolean advanced = prompts.confirm("Change advanced pipeline options", false);

        int fetchMaxJobs = TuiExecutionPlanFactory.DEFAULT_PIPELINE_FETCH_MAX_JOBS;
        long requestDelayMillis = context.fetchWebDefaults().requestDelayMillis();
        int fetchMaxConcurrency = context.fetchWebDefaults().maxConcurrency();
        int evaluateMaxConcurrency = context.evaluationDefaults().maxConcurrency();
        if (advanced) {
            fetchMaxJobs = prompts.promptInt("Fetch max jobs per company", fetchMaxJobs, 1);
            requestDelayMillis = prompts.promptLong("Fetch request delay millis", requestDelayMillis, 0);
            fetchMaxConcurrency = prompts.promptInt("Fetch max concurrency", fetchMaxConcurrency, 1);
            evaluateMaxConcurrency = prompts.promptInt("Evaluate max concurrency", evaluateMaxConcurrency, 1);
        }

        return planFactory.buildPipelineRun(
                context,
                new PipelineRunRequest(
                        personaId,
                        candidateProfileId,
                        fetchWebFirst,
                        companyIds,
                        incrementalOnly,
                        fetchMaxJobs,
                        requestDelayMillis,
                        fetchMaxConcurrency,
                        evaluateMaxConcurrency
                )
        );
    }

    private TuiExecutionPlan promptQuickCheckPlan() {
        List<OptionItem> modeOptions = List.of(
                new OptionItem(QuickCheckMode.URL.name(), QuickCheckMode.URL.label()),
                new OptionItem(QuickCheckMode.RAW_TEXT.name(), QuickCheckMode.RAW_TEXT.label()),
                new OptionItem(QuickCheckMode.RAW_FILE.name(), QuickCheckMode.RAW_FILE.label()),
                new OptionItem(QuickCheckMode.JOB_YAML.name(), QuickCheckMode.JOB_YAML.label())
        );
        QuickCheckMode mode = QuickCheckMode.valueOf(
                prompts.selectOne("Quick check mode", modeOptions, QuickCheckMode.URL.name())
        );
        String personaId = prompts.selectOne("Persona", personaOptions(), context.defaultPersonaId());
        String candidateProfileId = promptCandidateProfileId();

        String inputValue = switch (mode) {
            case URL -> prompts.prompt("Job URL", null);
            case RAW_FILE -> prompts.prompt("Text file path", null);
            case JOB_YAML -> prompts.prompt("Job YAML path", null);
            case RAW_TEXT -> prompts.promptMultiline("Paste raw job text", "END");
        };

        int timeoutSeconds = TuiExecutionPlanFactory.DEFAULT_CHECK_TIMEOUT_SECONDS;
        String userAgent = "";
        if (mode == QuickCheckMode.URL && prompts.confirm("Change URL fetch options", false)) {
            timeoutSeconds = prompts.promptInt("Timeout seconds", timeoutSeconds, 1);
            userAgent = prompts.promptOptional("User-Agent", "");
        }

        return planFactory.buildQuickCheck(
                context,
                new QuickCheckRequest(
                        mode,
                        personaId,
                        candidateProfileId,
                        inputValue,
                        timeoutSeconds,
                        userAgent
                )
        );
    }

    private TuiExecutionPlan promptFetchWebPlan() {
        List<String> companyIds = prompts.selectMany("Companies", companyOptions(), true);
        int maxJobsPerCompany = prompts.promptInt(
                "Max jobs per company",
                TuiExecutionPlanFactory.DEFAULT_FETCH_WEB_MAX_JOBS,
                1
        );
        long requestDelayMillis = prompts.promptLong(
                "Request delay millis",
                context.fetchWebDefaults().requestDelayMillis(),
                0
        );
        int maxConcurrency = prompts.promptInt(
                "Max concurrency",
                context.fetchWebDefaults().maxConcurrency(),
                1
        );
        boolean advanced = prompts.confirm("Change advanced fetch options", false);

        int maxConcurrencyPerHost = TuiExecutionPlanFactory.DEFAULT_FETCH_WEB_MAX_CONCURRENCY_PER_HOST;
        boolean disableCache = false;
        boolean disableCompanyContext = false;
        if (advanced) {
            maxConcurrencyPerHost = prompts.promptInt("Max concurrency per host", maxConcurrencyPerHost, 1);
            disableCache = prompts.confirm("Disable fetch cache", false);
            disableCompanyContext = prompts.confirm("Disable company context extraction", false);
        }

        return planFactory.buildFetchWeb(
                context,
                new FetchWebRequest(
                        companyIds,
                        maxJobsPerCompany,
                        requestDelayMillis,
                        maxConcurrency,
                        maxConcurrencyPerHost,
                        disableCache,
                        disableCompanyContext
                )
        );
    }

    private String promptCandidateProfileId() {
        if (context.candidateProfiles().isEmpty()) {
            return "";
        }

        List<OptionItem> options = new ArrayList<>();
        options.add(new OptionItem("auto", "Use CLI default candidate profile behavior"));
        options.add(new OptionItem("none", "Disable candidate profile enrichment"));
        for (CandidateProfileConfig candidateProfile : context.candidateProfiles()) {
            String label = candidateProfile.name().isBlank()
                    ? candidateProfile.title()
                    : candidateProfile.name() + " - " + candidateProfile.title();
            options.add(new OptionItem(candidateProfile.id(), label));
        }
        String selected = prompts.selectOne("Candidate profile", options, "auto");
        return "auto".equals(selected) ? "" : selected;
    }

    private List<OptionItem> companyOptions() {
        return context.companies().stream()
                .map(this::toCompanyOption)
                .toList();
    }

    private List<OptionItem> personaOptions() {
        return context.personas().stream()
                .map(this::toPersonaOption)
                .toList();
    }

    private OptionItem toCompanyOption(CompanyConfig company) {
        return new OptionItem(company.id(), company.name());
    }

    private OptionItem toPersonaOption(PersonaConfig persona) {
        return new OptionItem(persona.id(), persona.description());
    }

    private enum Action {
        RUN_ALL("run-all", "Run all personas and companies"),
        PIPELINE_RUN("pipeline-run", "Run one persona pipeline"),
        QUICK_CHECK("quick-check", "Quick check a URL, file, or raw text"),
        FETCH_WEB("fetch-web", "Fetch career pages into raw files"),
        CONFIG_VALIDATE("config-validate", "Validate config"),
        QUIT("quit", "Quit");

        private final String id;
        private final String description;

        Action(String id, String description) {
            this.id = id;
            this.description = description;
        }

        private static Action fromId(String value) {
            for (Action action : values()) {
                if (action.id.equals(value)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("Unknown action id: " + value);
        }
    }
}
