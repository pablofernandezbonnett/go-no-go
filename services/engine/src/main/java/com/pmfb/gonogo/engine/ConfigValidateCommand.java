package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "validate",
        description = "Validate YAML config files."
)
public final class ConfigValidateCommand implements Callable<Integer> {
    private static final String FIELD_COMPANIES = "companies";
    private static final String FIELD_PERSONAS = "personas";
    private static final String FIELD_BLACKLISTED_COMPANIES = "blacklisted_companies";
    private static final String FIELD_CANDIDATE_PROFILES = "candidate_profiles";
    private static final String FIELD_RUNTIME_FETCH_WEB_MAX_CONCURRENCY = "runtime_fetch_web_max_concurrency";
    private static final String FIELD_RUNTIME_FETCH_WEB_MAX_CONCURRENCY_PER_HOST =
            "runtime_fetch_web_max_concurrency_per_host";
    private static final String FIELD_RUNTIME_EVALUATION_MAX_CONCURRENCY = "runtime_evaluation_max_concurrency";
    private static final String FIELD_DECISION_LANGUAGE_REQUIRED_KEYWORDS = "decision_language_required_keywords";
    private static final String FIELD_DECISION_OVERTIME_RISK_KEYWORDS = "decision_overtime_risk_keywords";

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Override
    public Integer call() {
        EngineConfig config;
        try {
            config = new YamlConfigLoader(configDir).load();
        } catch (ConfigLoadException e) {
            printErrors(e.errors());
            return 1;
        }

        List<String> validationErrors = new ConfigValidator().validate(config);
        if (!validationErrors.isEmpty()) {
            printErrors(validationErrors);
            return 1;
        }

        System.out.println("Config validation passed.");
        System.out.println(FIELD_COMPANIES + ": " + config.companies().size());
        System.out.println(FIELD_PERSONAS + ": " + config.personas().size());
        System.out.println(FIELD_BLACKLISTED_COMPANIES + ": " + config.blacklistedCompanies().size());
        System.out.println(FIELD_CANDIDATE_PROFILES + ": " + config.candidateProfiles().size());
        System.out.println(FIELD_RUNTIME_FETCH_WEB_MAX_CONCURRENCY + ": " + config.runtimeSettings().fetchWeb().maxConcurrency());
        System.out.println(
                FIELD_RUNTIME_FETCH_WEB_MAX_CONCURRENCY_PER_HOST + ": "
                        + config.runtimeSettings().fetchWeb().maxConcurrencyPerHost()
        );
        System.out.println(
                FIELD_RUNTIME_EVALUATION_MAX_CONCURRENCY + ": "
                        + config.runtimeSettings().evaluation().maxConcurrency()
        );
        System.out.println(
                FIELD_DECISION_LANGUAGE_REQUIRED_KEYWORDS + ": "
                        + config.decisionSignals().language().requiredKeywords().size()
        );
        System.out.println(
                FIELD_DECISION_OVERTIME_RISK_KEYWORDS + ": "
                        + config.decisionSignals().workLifeBalance().overtimeRiskKeywords().size()
        );
        return 0;
    }

    private void printErrors(List<String> errors) {
        System.err.println("Config validation failed with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }
}
