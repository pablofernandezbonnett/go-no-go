package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.ConfigLoadException;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.decision.DecisionEngineV1;
import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.job.JobInputLoadException;
import com.pmfb.gonogo.engine.job.YamlJobInputLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "evaluate",
        description = "Evaluate a single job input using persona-aware rules."
)
public final class EvaluateCommand implements Callable<Integer> {
    @Option(
            names = {"--persona"},
            description = "Persona id from config/personas.yaml",
            required = true
    )
    private String personaId;

    @Option(
            names = {"--job-file"},
            description = "YAML file with job input fields.",
            required = true
    )
    private Path jobFile;

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
            printErrors("Configuration loading failed", e.errors());
            return 1;
        }

        List<String> configValidationErrors = new ConfigValidator().validate(config);
        if (!configValidationErrors.isEmpty()) {
            printErrors("Configuration validation failed", configValidationErrors);
            return 1;
        }

        Optional<PersonaConfig> persona = findPersona(config.personas(), personaId);
        if (persona.isEmpty()) {
            System.err.println("Unknown persona id '" + personaId + "'.");
            System.err.println("Available personas:");
            for (PersonaConfig item : config.personas()) {
                System.err.println(" - " + item.id());
            }
            return 1;
        }

        JobInput job;
        try {
            job = new YamlJobInputLoader(jobFile).load();
        } catch (JobInputLoadException e) {
            printErrors("Job input validation failed", e.errors());
            return 1;
        }

        EvaluationResult result = new DecisionEngineV1().evaluate(job, persona.get(), config);
        printResult(persona.get(), job, result);
        return 0;
    }

    private Optional<PersonaConfig> findPersona(List<PersonaConfig> personas, String id) {
        String normalized = normalize(id);
        return personas.stream()
                .filter(persona -> normalize(persona.id()).equals(normalized))
                .findFirst();
    }

    private void printResult(PersonaConfig persona, JobInput job, EvaluationResult result) {
        System.out.println("verdict: " + result.verdict());
        System.out.println("score: " + result.score() + "/100");
        System.out.println(
                "raw_score: " + result.rawScore()
                        + " (range " + result.rawScoreMin() + ".." + result.rawScoreMax() + ")"
        );
        System.out.println("language_friction_index: " + result.languageFrictionIndex() + "/100");
        System.out.println("company_reputation_index: " + result.companyReputationIndex() + "/100");
        System.out.println("persona: " + persona.id());
        System.out.println("company: " + job.companyName());
        System.out.println("role: " + job.title());
        printList("hard_reject_reasons", result.hardRejectReasons());
        printList("positive_signals", result.positiveSignals());
        printList("risk_signals", result.riskSignals());
        printList("reasoning", result.reasoning());
    }

    private void printList(String key, List<String> values) {
        if (values.isEmpty()) {
            System.out.println(key + ": []");
            return;
        }
        System.out.println(key + ":");
        for (String value : values) {
            System.out.println(" - " + value);
        }
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
