package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.ConfigSelections;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.decision.DecisionEngineV1;
import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.exception.JobInputLoadException;
import com.pmfb.gonogo.engine.job.YamlJobInputLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "evaluate", description = "Evaluate a single job input using persona-aware rules.")
public final class EvaluateCommand implements Callable<Integer> {
    private final DecisionEngineV1 engine;

    public EvaluateCommand() {
        this(new DecisionEngineV1());
    }

    public EvaluateCommand(DecisionEngineV1 engine) {
        this.engine = engine;
    }

    @Option(names = { "--persona" }, description = "Persona id from config/personas.yaml", required = true)
    private String personaId;

    @Option(names = { "--job-file" }, description = "YAML file with job input fields.", required = true)
    private Path jobFile;

    @Option(names = {
            "--candidate-profile" }, description = "Optional candidate profile id from config/candidate-profiles (auto-selects when exactly one exists).")
    private String candidateProfileId;

    @Option(names = {
            "--config-dir" }, description = "Directory containing config YAML files.", defaultValue = "config")
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

        Optional<PersonaConfig> persona = ConfigSelections.findPersona(config.personas(), personaId);
        if (persona.isEmpty()) {
            System.err.println("Unknown persona id '" + personaId + "'.");
            System.err.println("Available personas:");
            for (PersonaConfig item : config.personas()) {
                System.err.println(" - " + item.id());
            }
            return 1;
        }

        ConfigSelections.CandidateProfileResolution candidateProfileResolution = ConfigSelections
                .resolveCandidateProfile(config.candidateProfiles(), candidateProfileId);
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

        JobInput job;
        try {
            job = new YamlJobInputLoader(jobFile).load();
        } catch (JobInputLoadException e) {
            printErrors("Job input validation failed", e.errors());
            return 1;
        }

        EvaluationResult result = engine.evaluate(
                job,
                persona.get(),
                candidateProfileResolution.profile().orElse(null),
                config);
        printResult(persona.get(), candidateProfileResolution.profile().orElse(null), job, result);
        return 0;
    }

    private void printResult(
            PersonaConfig persona,
            CandidateProfileConfig candidateProfile,
            JobInput job,
            EvaluationResult result) {
        System.out.println(EvaluateInputFieldKeys.VERDICT + ": " + result.verdict());
        System.out.println(EvaluateInputFieldKeys.SCORE + ": " + result.score() + "/100");
        System.out.println(
                EvaluateInputFieldKeys.RAW_SCORE + ": " + result.rawScore()
                        + " (range " + result.rawScoreMin() + ".." + result.rawScoreMax() + ")");
        System.out.println(
                EvaluateInputFieldKeys.LANGUAGE_FRICTION_INDEX + ": " + result.languageFrictionIndex() + "/100");
        System.out.println(
                EvaluateInputFieldKeys.COMPANY_REPUTATION_INDEX + ": " + result.companyReputationIndex() + "/100");
        System.out.println(EvaluateInputFieldKeys.PERSONA + ": " + persona.id());
        System.out.println(EvaluateInputFieldKeys.CANDIDATE_PROFILE + ": "
                + ConfigSelections.candidateProfileIdOrNone(candidateProfile));
        System.out.println(EvaluateInputFieldKeys.COMPANY + ": " + job.companyName());
        System.out.println(EvaluateInputFieldKeys.ROLE + ": " + job.title());
        printList(EvaluateInputFieldKeys.HARD_REJECT_REASONS, result.hardRejectReasons());
        printList(EvaluateInputFieldKeys.POSITIVE_SIGNALS, result.positiveSignals());
        printList(EvaluateInputFieldKeys.RISK_SIGNALS, result.riskSignals());
        printList(EvaluateInputFieldKeys.REASONING, result.reasoning());
        printHumanReading(result.humanReading());
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

    private void printHumanReading(com.pmfb.gonogo.engine.decision.HumanReading humanReading) {
        System.out.println(EvaluateInputFieldKeys.HUMAN_READING + ":");
        System.out.println(" - " + EvaluateInputFieldKeys.ACCESS_FIT + ": " + humanReading.accessFit().serialized());
        System.out.println(" - " + EvaluateInputFieldKeys.EXECUTION_FIT + ": " + humanReading.executionFit().serialized());
        System.out.println(" - " + EvaluateInputFieldKeys.DOMAIN_FIT + ": " + humanReading.domainFit().serialized());
        System.out.println(" - " + EvaluateInputFieldKeys.OPPORTUNITY_QUALITY + ": "
                + humanReading.opportunityQuality().serialized());
        System.out.println(" - " + EvaluateInputFieldKeys.INTERVIEW_ROI + ": " + humanReading.interviewRoi().serialized());
        System.out.println(" - " + EvaluateInputFieldKeys.SUMMARY + ": " + humanReading.summary());
        printNestedList(EvaluateInputFieldKeys.WHY_STILL_INTERESTING, humanReading.whyStillInteresting());
        printNestedList(EvaluateInputFieldKeys.WHY_WASTE_OF_TIME, humanReading.whyWasteOfTime());
    }

    private void printNestedList(String key, List<String> values) {
        if (values.isEmpty()) {
            System.out.println(" - " + key + ": []");
            return;
        }
        System.out.println(" - " + key + ":");
        for (String value : values) {
            System.out.println("   - " + value);
        }
    }

}
