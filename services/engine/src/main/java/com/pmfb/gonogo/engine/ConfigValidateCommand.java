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
        System.out.println("companies: " + config.companies().size());
        System.out.println("personas: " + config.personas().size());
        System.out.println("blacklisted_companies: " + config.blacklistedCompanies().size());
        System.out.println("candidate_profiles: " + config.candidateProfiles().size());
        System.out.println("runtime_fetch_web_max_concurrency: " + config.runtimeSettings().fetchWeb().maxConcurrency());
        System.out.println(
                "runtime_fetch_web_max_concurrency_per_host: "
                        + config.runtimeSettings().fetchWeb().maxConcurrencyPerHost()
        );
        System.out.println(
                "runtime_evaluation_max_concurrency: "
                        + config.runtimeSettings().evaluation().maxConcurrency()
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
