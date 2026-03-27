package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.AdHocEvaluationArtifactLoadException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "rerun-ad-hoc",
        mixinStandardHelpOptions = true,
        description = "Re-evaluate saved ad-hoc evaluation artifacts and overwrite them in place."
)
public final class RerunAdHocEvaluationsCommand implements Callable<Integer> {
    private static final String FIELD_ARTIFACTS_TOTAL = "artifacts_total";
    private static final String FIELD_RERUN_SUCCEEDED = "rerun_succeeded";
    private static final String FIELD_RERUN_FAILED = "rerun_failed";
    private static final String FIELD_INPUT_DIR = "input_dir";
    private static final String FIELD_PATTERN = "pattern";
    private static final String FIELD_RERUN_ARTIFACT = "rerun_artifact";
    private static final String LABEL_RERUN_COMPLETED = "rerun-ad-hoc completed.";
    private static final String DEFAULT_PATTERN = "*.yaml";
    private static final String DEFAULT_INPUT_DIR = "output/ad-hoc-evaluations";

    private final AdHocEvaluationArtifactLoader artifactLoader;
    private final EvaluateInputExecutor evaluateInputExecutor;

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
            names = {"--fail-fast"},
            description = "Stop on the first failed artifact re-run."
    )
    private boolean failFast;

    public RerunAdHocEvaluationsCommand() {
        this(
                new AdHocEvaluationArtifactLoader(),
                args -> new picocli.CommandLine(new EvaluateInputCommand()).execute(args.toArray(String[]::new))
        );
    }

    RerunAdHocEvaluationsCommand(
            AdHocEvaluationArtifactLoader artifactLoader,
            EvaluateInputExecutor evaluateInputExecutor
    ) {
        this.artifactLoader = artifactLoader;
        this.evaluateInputExecutor = evaluateInputExecutor;
    }

    @Override
    public Integer call() throws IOException {
        List<Path> artifactFiles = findArtifactFiles();
        if (artifactFiles.isEmpty()) {
            System.err.println("No ad-hoc evaluation artifacts found in " + inputDir + " matching '" + pattern + "'.");
            return 1;
        }

        int successes = 0;
        int failures = 0;
        for (Path artifactFile : artifactFiles) {
            System.out.println(FIELD_RERUN_ARTIFACT + ": " + artifactFile);
            try {
                rerunArtifact(artifactFile);
                successes++;
            } catch (IOException | AdHocEvaluationArtifactLoadException e) {
                failures++;
                System.err.println("Failed to rerun ad-hoc artifact '" + artifactFile + "': " + e.getMessage());
                if (e instanceof AdHocEvaluationArtifactLoadException loadException) {
                    printErrors(loadException.errors());
                }
                if (failFast) {
                    break;
                }
            }
        }

        System.out.println(LABEL_RERUN_COMPLETED);
        System.out.println(FIELD_ARTIFACTS_TOTAL + ": " + artifactFiles.size());
        System.out.println(FIELD_RERUN_SUCCEEDED + ": " + successes);
        System.out.println(FIELD_RERUN_FAILED + ": " + failures);
        System.out.println(FIELD_INPUT_DIR + ": " + inputDir);
        System.out.println(FIELD_PATTERN + ": " + pattern);
        return failures == 0 ? 0 : 1;
    }

    private void rerunArtifact(Path artifactFile) throws IOException {
        AdHocEvaluationArtifact artifact = artifactLoader.load(artifactFile);
        List<String> args = new ArrayList<>();
        args.add("--persona");
        args.add(artifact.personaId());
        args.add("--config-dir");
        args.add(configDir.toString());
        if (!artifact.candidateProfileId().isBlank()) {
            args.add("--candidate-profile");
            args.add(artifact.candidateProfileId());
        }
        PreparedSourceInput preparedSourceInput = prepareSourceInput(artifact);
        try {
            args.addAll(preparedSourceInput.args());
            args.add("--output-analysis-file");
            args.add(artifactFile.toString());

            int exitCode = evaluateInputExecutor.execute(args);
            if (exitCode != 0) {
                throw new IOException("evaluate-input exited with code " + exitCode);
            }
        } finally {
            preparedSourceInput.cleanup();
        }
    }

    private PreparedSourceInput prepareSourceInput(AdHocEvaluationArtifact artifact) throws IOException {
        if (!artifact.source().url().isBlank()) {
            return new PreparedSourceInput(
                    List.of("--job-url", artifact.source().url()),
                    null
            );
        }

        if (artifact.source().rawText().isBlank()) {
            throw new IOException("Artifact does not contain reusable source text or URL.");
        }

        Path tempFile = Files.createTempFile("gonogo-rerun-adhoc-", ".txt");
        Files.writeString(tempFile, artifact.source().rawText(), StandardCharsets.UTF_8);
        return new PreparedSourceInput(
                List.of("--raw-text-file", tempFile.toString()),
                tempFile
        );
    }

    private List<Path> findArtifactFiles() throws IOException {
        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            return List.of();
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.walk(inputDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(inputDir.relativize(path)) || matcher.matches(path.getFileName()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private void printErrors(List<String> errors) {
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    @FunctionalInterface
    interface EvaluateInputExecutor {
        int execute(List<String> args);
    }

    private record PreparedSourceInput(
            List<String> args,
            Path tempFile
    ) {
        private PreparedSourceInput {
            args = List.copyOf(args);
        }

        private void cleanup() throws IOException {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
