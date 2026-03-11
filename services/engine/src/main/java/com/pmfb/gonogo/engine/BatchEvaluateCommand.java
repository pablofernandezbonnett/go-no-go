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
import com.pmfb.gonogo.engine.decision.Verdict;
import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.exception.JobInputLoadException;
import com.pmfb.gonogo.engine.job.YamlJobInputLoader;
import com.pmfb.gonogo.engine.report.BatchEvaluationError;
import com.pmfb.gonogo.engine.report.BatchEvaluationItem;
import com.pmfb.gonogo.engine.report.BatchEvaluationReport;
import com.pmfb.gonogo.engine.report.BatchReportWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "evaluate-batch",
        description = "Evaluate multiple job YAML files and generate JSON/Markdown reports."
)
public final class BatchEvaluateCommand implements Callable<Integer> {
    private final DecisionEngineV1 engine;

    public BatchEvaluateCommand() {
        this(new DecisionEngineV1());
    }

    public BatchEvaluateCommand(DecisionEngineV1 engine) {
        this.engine = engine;
    }

    @Option(
            names = {"--persona"},
            description = "Persona id from config/personas.yaml",
            required = true
    )
    private String personaId;

    @Option(
            names = {"--input-dir"},
            description = "Directory containing job YAML files.",
            required = true
    )
    private Path inputDir;

    @Option(
            names = {"--pattern"},
            description = "Glob pattern for input files (relative to input dir).",
            defaultValue = "*.yaml"
    )
    private String pattern;

    @Option(
            names = {"--recursive"},
            description = "Walk input directory recursively.",
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
            names = {"--candidate-profile"},
            description = "Optional candidate profile id from config/candidate-profiles (auto-selects when exactly one exists)."
    )
    private String candidateProfileId;

    @Option(
            names = {"--output-dir"},
            description = "Directory for generated reports when output file options are omitted.",
            defaultValue = "output"
    )
    private Path outputDir;

    @Option(
            names = {"--output-json"},
            description = "Output JSON report path."
    )
    private Path outputJson;

    @Option(
            names = {"--output-markdown"},
            description = "Output Markdown report path."
    )
    private Path outputMarkdown;

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

        List<Path> files = collectInputFiles();
        if (files.isEmpty()) {
            System.err.println(
                    "No input files found in '" + inputDir + "' with pattern '" + pattern + "'."
            );
            return 1;
        }

        List<BatchEvaluationItem> items = new ArrayList<>();
        List<BatchEvaluationError> errors = new ArrayList<>();

        for (Path file : files) {
            JobInput job;
            try {
                job = new YamlJobInputLoader(file).load();
            } catch (JobInputLoadException e) {
                errors.add(new BatchEvaluationError(
                        relativizeToInputDir(file),
                        e.errors()
                ));
                continue;
            } catch (RuntimeException e) {
                errors.add(new BatchEvaluationError(
                        relativizeToInputDir(file),
                        List.of("Unexpected error while reading file: " + e.getMessage())
                ));
                continue;
            }

            EvaluationResult result = engine.evaluate(
                    job,
                    persona.get(),
                    candidateProfileResolution.profile().orElse(null),
                    config
            );
            items.add(new BatchEvaluationItem(relativizeToInputDir(file), job, result));
        }

        String effectiveCandidateProfileId = effectiveCandidateProfileId(candidateProfileResolution);
        BatchEvaluationReport report = buildReport(
                persona.get().id(),
                effectiveCandidateProfileId,
                files.size(),
                items,
                errors
        );
        BatchReportWriter writer = new BatchReportWriter();

        Path markdownPath = outputMarkdown != null
                ? outputMarkdown
                : outputDir.resolve(writer.defaultMarkdownFileName(
                        persona.get().id(),
                        effectiveCandidateProfileId
                ));
        Path jsonPath = outputJson != null
                ? outputJson
                : outputDir.resolve(writer.defaultJsonFileName(
                        persona.get().id(),
                        effectiveCandidateProfileId
                ));

        try {
            writer.writeMarkdown(markdownPath, report);
            writer.writeJson(jsonPath, report);
        } catch (IOException e) {
            System.err.println("Failed to write report files: " + e.getMessage());
            return 1;
        }

        printSummary(report, markdownPath, jsonPath);
        return 0;
    }

    private List<Path> collectInputFiles() {
        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            return List.of();
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<Path> matches = new ArrayList<>();
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;

        try (Stream<Path> stream = Files.walk(inputDir, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> matchesPattern(path, matcher))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(matches::add);
        } catch (IOException e) {
            return List.of();
        }
        return matches;
    }

    private boolean matchesPattern(Path file, PathMatcher matcher) {
        Path relative = inputDir.relativize(file);
        return matcher.matches(relative) || matcher.matches(file.getFileName());
    }

    private BatchEvaluationReport buildReport(
            String personaIdValue,
            String candidateProfileIdValue,
            int totalFiles,
            List<BatchEvaluationItem> items,
            List<BatchEvaluationError> errors
    ) {
        int go = 0;
        int caution = 0;
        int noGo = 0;
        for (BatchEvaluationItem item : items) {
            if (item.evaluation().verdict() == Verdict.GO) {
                go++;
            } else if (item.evaluation().verdict() == Verdict.GO_WITH_CAUTION) {
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
                items,
                errors
        );
    }

    private String relativizeToInputDir(Path file) {
        return inputDir.relativize(file).toString();
    }

    private String effectiveCandidateProfileId(ConfigSelections.CandidateProfileResolution resolution) {
        return resolution.profile()
                .map(CandidateProfileConfig::id)
                .orElse(ConfigSelections.CANDIDATE_PROFILE_NONE);
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    private void printSummary(BatchEvaluationReport report, Path markdownPath, Path jsonPath) {
        System.out.println("Batch evaluation completed.");
        System.out.println("persona: " + report.personaId());
        System.out.println("candidate_profile: " + report.candidateProfileId());
        System.out.println("total_files: " + report.totalFiles());
        System.out.println("evaluated: " + report.evaluatedCount());
        System.out.println("failed: " + report.failedCount());
        System.out.println("go: " + report.goCount());
        System.out.println("go_with_caution: " + report.goWithCautionCount());
        System.out.println("no_go: " + report.noGoCount());
        System.out.println("markdown_report: " + markdownPath);
        System.out.println("json_report: " + jsonPath);
    }
}
