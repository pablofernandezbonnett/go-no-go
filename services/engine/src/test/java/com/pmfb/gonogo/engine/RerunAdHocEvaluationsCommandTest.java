package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class RerunAdHocEvaluationsCommandTest {
    @Test
    void rerunsUrlArtifactsUsingSavedPersonaProfileAndArtifactPath() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-rerun-adhoc-url-test");
        Path artifactDir = tempDir.resolve("output/ad-hoc-evaluations");
        Files.createDirectories(artifactDir);
        Path artifactFile = artifactDir.resolve("job.yaml");
        Files.writeString(
                artifactFile,
                """
                        generated_at: '2026-03-13T00:00:00Z'
                        persona: product_expat_engineer
                        candidate_profile: pmfb
                        source:
                          kind: url
                          url: https://example.com/jobs/backend-engineer
                        """,
                StandardCharsets.UTF_8
        );

        List<List<String>> invocations = new ArrayList<>();
        RerunAdHocEvaluationsCommand command = new RerunAdHocEvaluationsCommand(
                new AdHocEvaluationArtifactLoader(),
                args -> {
                    invocations.add(List.copyOf(args));
                    return 0;
                }
        );

        int exitCode = new CommandLine(command).execute(
                "--config-dir", tempDir.resolve("config").toString(),
                "--input-dir", artifactDir.toString()
        );

        assertEquals(0, exitCode);
        assertEquals(1, invocations.size());
        List<String> args = invocations.get(0);
        assertTrue(args.contains("--persona"));
        assertTrue(args.contains("product_expat_engineer"));
        assertTrue(args.contains("--candidate-profile"));
        assertTrue(args.contains("pmfb"));
        assertTrue(args.contains("--job-url"));
        assertTrue(args.contains("https://example.com/jobs/backend-engineer"));
        assertTrue(args.contains("--output-analysis-file"));
        assertTrue(args.contains(artifactFile.toString()));
    }

    @Test
    void rerunsTextArtifactsViaTemporaryRawTextFileAndCleansItUp() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-rerun-adhoc-text-test");
        Path artifactDir = tempDir.resolve("output/ad-hoc-evaluations");
        Files.createDirectories(artifactDir);
        Path artifactFile = artifactDir.resolve("job.yaml");
        Files.writeString(
                artifactFile,
                """
                        generated_at: '2026-03-13T00:00:00Z'
                        persona: product_expat_engineer
                        candidate_profile: none
                        source:
                          kind: stdin
                          raw_text: |-
                            Company: Example
                            Title: Backend Engineer
                        """,
                StandardCharsets.UTF_8
        );

        AtomicReference<Path> tempRawTextFile = new AtomicReference<>();
        RerunAdHocEvaluationsCommand command = new RerunAdHocEvaluationsCommand(
                new AdHocEvaluationArtifactLoader(),
                args -> {
                    int rawTextIndex = args.indexOf("--raw-text-file");
                    assertTrue(rawTextIndex >= 0);
                    Path rawTextFile = Path.of(args.get(rawTextIndex + 1));
                    tempRawTextFile.set(rawTextFile);
                    try {
                        String content = Files.readString(rawTextFile);
                        assertTrue(content.contains("Company: Example"));
                        assertTrue(content.contains("Title: Backend Engineer"));
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                    assertTrue(args.contains("--candidate-profile"));
                    assertTrue(args.contains("none"));
                    return 0;
                }
        );

        int exitCode = new CommandLine(command).execute(
                "--config-dir", tempDir.resolve("config").toString(),
                "--input-dir", artifactDir.toString()
        );

        assertEquals(0, exitCode);
        assertTrue(tempRawTextFile.get() != null);
        assertFalse(Files.exists(tempRawTextFile.get()));
    }
}
