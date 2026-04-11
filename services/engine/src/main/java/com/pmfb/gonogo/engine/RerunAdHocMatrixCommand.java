package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.ConfigSelections;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.exception.AdHocEvaluationArtifactLoadException;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "rerun-ad-hoc-matrix",
        mixinStandardHelpOptions = true,
        description = "Re-evaluate saved ad-hoc sources for a fixed candidate profile across one or more personas."
)
public final class RerunAdHocMatrixCommand implements Callable<Integer> {
    private static final String FIELD_INPUT_ARTIFACTS_TOTAL = "input_artifacts_total";
    private static final String FIELD_UNIQUE_SOURCES_TOTAL = "unique_sources_total";
    private static final String FIELD_PERSONAS_TOTAL = "personas_total";
    private static final String FIELD_ARTIFACTS_TOTAL = "artifacts_total";
    private static final String FIELD_RERUN_SUCCEEDED = "rerun_succeeded";
    private static final String FIELD_RERUN_FAILED = "rerun_failed";
    private static final String FIELD_CANDIDATE_PROFILE = "candidate_profile";
    private static final String FIELD_INPUT_DIR = "input_dir";
    private static final String FIELD_PATTERN = "pattern";
    private static final String FIELD_RERUN_ARTIFACT = "rerun_artifact";
    private static final String LABEL_RERUN_COMPLETED = "rerun-ad-hoc-matrix completed.";
    private static final String DEFAULT_PATTERN = "*.yaml";
    private static final String DEFAULT_INPUT_DIR = "output/ad-hoc-evaluations";

    private final AdHocEvaluationRerunSupport rerunSupport;
    private final AdHocArtifactFileNameResolver fileNameResolver;

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Option(
            names = {"--input-dir"},
            description = "Directory containing ad-hoc evaluation YAML artifacts.",
            defaultValue = DEFAULT_INPUT_DIR
    )
    private Path inputDir;

    @Option(
            names = {"--pattern"},
            description = "Glob pattern for artifact files (relative to input dir).",
            defaultValue = DEFAULT_PATTERN
    )
    private String pattern;

    @Option(
            names = {"--candidate-profile"},
            description = "Candidate profile id from config/candidate-profiles. Use 'none' to disable candidate-aware scoring.",
            required = true
    )
    private String candidateProfileId;

    @Option(
            names = {"--persona-ids", "--personas"},
            split = ",",
            description = "Optional comma-separated persona ids. Defaults to all personas in config."
    )
    private List<String> personaIds;

    @Option(
            names = {"--fail-fast"},
            description = "Stop on the first failed source/persona rerun."
    )
    private boolean failFast;

    public RerunAdHocMatrixCommand() {
        this(
                new AdHocEvaluationRerunSupport(
                        new AdHocEvaluationArtifactLoader(),
                        args -> new picocli.CommandLine(new EvaluateInputCommand()).execute(args.toArray(String[]::new))
                ),
                new AdHocArtifactFileNameResolver()
        );
    }

    RerunAdHocMatrixCommand(
            AdHocEvaluationRerunSupport rerunSupport,
            AdHocArtifactFileNameResolver fileNameResolver
    ) {
        this.rerunSupport = rerunSupport;
        this.fileNameResolver = fileNameResolver;
    }

    @Override
    public Integer call() throws IOException {
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
        String effectiveCandidateProfileId =
                ConfigSelections.candidateProfileIdOrNone(candidateProfileResolution.profile().orElse(null));

        List<PersonaConfig> selectedPersonas = selectPersonas(config.personas(), personaIds);
        if (selectedPersonas.isEmpty()) {
            return 1;
        }

        List<Path> artifactFiles = rerunSupport.findArtifactFiles(inputDir, pattern);
        if (artifactFiles.isEmpty()) {
            System.err.println("No ad-hoc evaluation artifacts found in " + inputDir + " matching '" + pattern + "'.");
            return 1;
        }

        List<SourceGroup> sourceGroups = buildSourceGroups(artifactFiles);
        int artifactsPlanned = sourceGroups.size() * selectedPersonas.size();
        int successes = 0;
        int failures = 0;

        outer:
        for (SourceGroup sourceGroup : sourceGroups) {
            for (PersonaConfig persona : selectedPersonas) {
                Path outputFile = resolveOutputFile(sourceGroup, persona.id(), effectiveCandidateProfileId);
                System.out.println(FIELD_RERUN_ARTIFACT + ": " + outputFile);
                try {
                    rerunSupport.rerunSource(
                            outputFile,
                            configDir,
                            persona.id(),
                            effectiveCandidateProfileId,
                            sourceGroup.source()
                    );
                    successes++;
                } catch (IOException e) {
                    failures++;
                    System.err.println("Failed to rerun ad-hoc source into '" + outputFile + "': " + e.getMessage());
                    if (failFast) {
                        break outer;
                    }
                }
            }
        }

        System.out.println(LABEL_RERUN_COMPLETED);
        System.out.println(FIELD_INPUT_ARTIFACTS_TOTAL + ": " + artifactFiles.size());
        System.out.println(FIELD_UNIQUE_SOURCES_TOTAL + ": " + sourceGroups.size());
        System.out.println(FIELD_PERSONAS_TOTAL + ": " + selectedPersonas.size());
        System.out.println(FIELD_ARTIFACTS_TOTAL + ": " + artifactsPlanned);
        System.out.println(FIELD_RERUN_SUCCEEDED + ": " + successes);
        System.out.println(FIELD_RERUN_FAILED + ": " + failures);
        System.out.println(FIELD_CANDIDATE_PROFILE + ": " + effectiveCandidateProfileId);
        System.out.println(FIELD_INPUT_DIR + ": " + inputDir);
        System.out.println(FIELD_PATTERN + ": " + pattern);
        return failures == 0 ? 0 : 1;
    }

    private List<SourceGroup> buildSourceGroups(List<Path> artifactFiles) {
        Map<SourceKey, List<ArtifactEntry>> bySource = new LinkedHashMap<>();
        for (Path artifactFile : artifactFiles) {
            try {
                AdHocEvaluationArtifact artifact = rerunSupport.loadArtifact(artifactFile);
                SourceKey key = new SourceKey(
                        artifact.source().kind(),
                        artifact.source().url(),
                        artifact.source().rawText()
                );
                bySource.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new ArtifactEntry(artifactFile, artifact));
            } catch (AdHocEvaluationArtifactLoadException e) {
                rerunSupport.printArtifactLoadFailure(artifactFile, e);
                throw e;
            }
        }

        List<SourceGroup> groups = new ArrayList<>();
        for (List<ArtifactEntry> entries : bySource.values()) {
            entries.sort((left, right) -> left.artifactFile().toString().compareTo(right.artifactFile().toString()));
            groups.add(new SourceGroup(entries.getFirst().artifact().source(), List.copyOf(entries)));
        }
        return groups;
    }

    private Path resolveOutputFile(
            SourceGroup sourceGroup,
            String personaIdValue,
            String candidateProfileIdValue
    ) {
        Optional<Path> existing = sourceGroup.findArtifactFile(personaIdValue, candidateProfileIdValue);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (!sourceGroup.source().url().isBlank()) {
            return fileNameResolver.buildUrlArtifactPath(
                    inputDir,
                    personaIdValue,
                    candidateProfileIdValue,
                    sourceGroup.source().url()
            );
        }

        return fileNameResolver.buildTextArtifactPath(
                inputDir,
                sourceGroup.templateArtifactFile(),
                personaIdValue,
                candidateProfileIdValue,
                sourceGroup.source().rawText()
        );
    }

    private List<PersonaConfig> selectPersonas(List<PersonaConfig> personas, List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
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
        return selected;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void printErrors(String heading, List<String> errors) {
        System.err.println(heading + " with " + errors.size() + " error(s):");
        AdHocEvaluationRerunSupport.printErrors(errors);
    }

    private record ArtifactEntry(
            Path artifactFile,
            AdHocEvaluationArtifact artifact
    ) {
    }

    private record SourceKey(
            String kind,
            String url,
            String rawText
    ) {
    }

    private record SourceGroup(
            AdHocEvaluationArtifact.Source source,
            List<ArtifactEntry> entries
    ) {
        private Optional<Path> findArtifactFile(String personaId, String candidateProfileId) {
            String normalizedPersonaId = normalizeId(personaId);
            String normalizedCandidateProfileId = normalizeId(candidateProfileId);
            return entries.stream()
                    .filter(entry -> normalizeId(entry.artifact().personaId()).equals(normalizedPersonaId))
                    .filter(entry -> normalizeId(entry.artifact().candidateProfileId()).equals(normalizedCandidateProfileId))
                    .map(ArtifactEntry::artifactFile)
                    .findFirst();
        }

        private Path templateArtifactFile() {
            return entries.getFirst().artifactFile();
        }

        private static String normalizeId(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
