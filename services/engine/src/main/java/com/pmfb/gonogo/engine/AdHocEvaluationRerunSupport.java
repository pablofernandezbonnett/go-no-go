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
import java.util.stream.Stream;

final class AdHocEvaluationRerunSupport {
    private final AdHocEvaluationArtifactLoader artifactLoader;
    private final EvaluateInputExecutor evaluateInputExecutor;

    AdHocEvaluationRerunSupport(
            AdHocEvaluationArtifactLoader artifactLoader,
            EvaluateInputExecutor evaluateInputExecutor
    ) {
        this.artifactLoader = artifactLoader;
        this.evaluateInputExecutor = evaluateInputExecutor;
    }

    AdHocEvaluationArtifact loadArtifact(Path artifactFile) {
        return artifactLoader.load(artifactFile);
    }

    List<Path> findArtifactFiles(Path inputDir, String pattern) throws IOException {
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

    void rerunSource(
            Path outputFile,
            Path configDir,
            String personaId,
            String candidateProfileId,
            AdHocEvaluationArtifact.Source source
    ) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("--persona");
        args.add(personaId);
        args.add("--config-dir");
        args.add(configDir.toString());
        if (!candidateProfileId.isBlank()) {
            args.add("--candidate-profile");
            args.add(candidateProfileId);
        }
        PreparedSourceInput preparedSourceInput = prepareSourceInput(source);
        try {
            args.addAll(preparedSourceInput.args());
            args.add("--output-analysis-file");
            args.add(outputFile.toString());

            int exitCode = evaluateInputExecutor.execute(args);
            if (exitCode != 0) {
                throw new IOException("evaluate-input exited with code " + exitCode);
            }
        } finally {
            preparedSourceInput.cleanup();
        }
    }

    private PreparedSourceInput prepareSourceInput(AdHocEvaluationArtifact.Source source) throws IOException {
        if (!source.url().isBlank()) {
            return new PreparedSourceInput(
                    List.of("--job-url", source.url()),
                    null
            );
        }

        if (source.rawText().isBlank()) {
            throw new IOException("Artifact does not contain reusable source text or URL.");
        }

        Path tempFile = Files.createTempFile("gonogo-rerun-adhoc-", ".txt");
        Files.writeString(tempFile, source.rawText(), StandardCharsets.UTF_8);
        return new PreparedSourceInput(
                List.of("--raw-text-file", tempFile.toString()),
                tempFile
        );
    }

    static void printErrors(List<String> errors) {
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    void printArtifactLoadFailure(Path artifactFile, AdHocEvaluationArtifactLoadException exception) {
        System.err.println("Failed to load ad-hoc artifact '" + artifactFile + "': " + exception.getMessage());
        printErrors(exception.errors());
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
