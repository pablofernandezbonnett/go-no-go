package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class RerunAdHocMatrixCommandTest {
    private final AdHocArtifactFileNameResolver fileNameResolver = new AdHocArtifactFileNameResolver();

    @Test
    void rerunsUniqueUrlSourcesAgainstAllConfiguredPersonasForFixedCandidateProfile() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-rerun-adhoc-matrix-url-test");
        Path configDir = tempDir.resolve("config");
        writeMatrixConfig(configDir);

        Path artifactDir = tempDir.resolve("output/ad-hoc-evaluations");
        Files.createDirectories(artifactDir);
        Files.writeString(
                artifactDir.resolve("first.yaml"),
                """
                        generated_at: '2026-04-09T00:00:00Z'
                        persona: persona_one
                        candidate_profile: none
                        source:
                          kind: url
                          url: https://example.com/jobs/backend-engineer
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                artifactDir.resolve("second.yaml"),
                """
                        generated_at: '2026-04-09T00:00:00Z'
                        persona: persona_two
                        candidate_profile: none
                        source:
                          kind: url
                          url: https://example.com/jobs/backend-engineer
                        """,
                StandardCharsets.UTF_8
        );

        List<List<String>> invocations = new ArrayList<>();
        RerunAdHocMatrixCommand command = new RerunAdHocMatrixCommand(
                new AdHocEvaluationRerunSupport(new AdHocEvaluationArtifactLoader(), args -> {
                    invocations.add(List.copyOf(args));
                    return 0;
                }),
                new AdHocArtifactFileNameResolver()
        );

        int exitCode = new CommandLine(command).execute(
                "--config-dir", configDir.toString(),
                "--input-dir", artifactDir.toString(),
                "--candidate-profile", "pmfb"
        );

        assertEquals(0, exitCode);
        assertEquals(2, invocations.size());
        assertInvocation(invocations, "persona_one", "pmfb",
                fileNameResolver.buildUrlArtifactPath(
                        artifactDir,
                        "persona_one",
                        "pmfb",
                        "https://example.com/jobs/backend-engineer"
                ));
        assertInvocation(invocations, "persona_two", "pmfb",
                fileNameResolver.buildUrlArtifactPath(
                        artifactDir,
                        "persona_two",
                        "pmfb",
                        "https://example.com/jobs/backend-engineer"
                ));
    }

    @Test
    void reusesTextArtifactTimestampWhenCreatingMissingPersonaScopeArtifacts() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-rerun-adhoc-matrix-text-test");
        Path configDir = tempDir.resolve("config");
        writeMatrixConfig(configDir);

        Path artifactDir = tempDir.resolve("output/ad-hoc-evaluations");
        Files.createDirectories(artifactDir);
        Files.writeString(
                artifactDir.resolve("adhoc-20260409T010203000000Z-persona-one-none-text.yaml"),
                """
                        generated_at: '2026-04-09T00:00:00Z'
                        persona: persona_one
                        candidate_profile: none
                        source:
                          kind: stdin
                          raw_text: |-
                            Company: Example
                            Title: Backend Engineer
                        """,
                StandardCharsets.UTF_8
        );

        List<List<String>> invocations = new ArrayList<>();
        RerunAdHocMatrixCommand command = new RerunAdHocMatrixCommand(
                new AdHocEvaluationRerunSupport(new AdHocEvaluationArtifactLoader(), args -> {
                    invocations.add(List.copyOf(args));
                    return 0;
                }),
                new AdHocArtifactFileNameResolver()
        );

        int exitCode = new CommandLine(command).execute(
                "--config-dir", configDir.toString(),
                "--input-dir", artifactDir.toString(),
                "--candidate-profile", "pmfb"
        );

        assertEquals(0, exitCode);
        assertEquals(2, invocations.size());
        assertInvocation(invocations, "persona_one", "pmfb",
                artifactDir.resolve("adhoc-20260409T010203000000Z-persona-one-pmfb-text.yaml"));
        assertInvocation(invocations, "persona_two", "pmfb",
                artifactDir.resolve("adhoc-20260409T010203000000Z-persona-two-pmfb-text.yaml"));
    }

    private void assertInvocation(
            List<List<String>> invocations,
            String personaId,
            String candidateProfileId,
            Path outputFile
    ) {
        List<String> invocation = invocations.stream()
                .filter(args -> hasFlagValue(args, "--persona", personaId))
                .findFirst()
                .orElseThrow();
        assertTrue(hasFlagValue(invocation, "--candidate-profile", candidateProfileId));
        assertTrue(hasFlagValue(invocation, "--output-analysis-file", outputFile.toString()));
    }

    private boolean hasFlagValue(List<String> args, String flag, String value) {
        int index = args.indexOf(flag);
        return index >= 0 && index + 1 < args.size() && args.get(index + 1).equals(value);
    }

    private void writeMatrixConfig(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Files.writeString(
                configDir.resolve("companies.yaml"),
                """
                        companies:
                          - id: example_company
                            name: Example Company
                            career_url: https://example.com/careers
                            corporate_url: https://example.com
                            type_hint: product_company
                            region: japan
                            notes: Example
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                configDir.resolve("personas.yaml"),
                """
                        personas:
                          - id: persona_one
                            description: "Persona one"
                            priorities:
                              - english_environment
                            hard_no:
                              - consulting_company
                            acceptable_if:
                              - hybrid_partial
                          - id: persona_two
                            description: "Persona two"
                            priorities:
                              - salary
                            hard_no:
                              - early_stage_startup
                            acceptable_if:
                              - stable_scaleup
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                configDir.resolve("blacklist.yaml"),
                """
                        blacklisted_companies: []
                        """,
                StandardCharsets.UTF_8
        );
        Path profilesDir = configDir.resolve("candidate-profiles");
        Files.createDirectories(profilesDir);
        Files.writeString(
                profilesDir.resolve("pmfb.yaml"),
                """
                        candidate:
                          name: PMFB
                          title: Senior Backend Engineer
                          location: Tokyo, Japan
                          total_experience_years: 20
                        stack:
                          production_proven:
                            backend:
                              - Go
                          actively_learning:
                            - TypeScript
                          gaps_honest:
                            - Kubernetes
                        domain_expertise:
                          strong:
                            - payments
                          moderate:
                            - ecommerce_platforms
                          limited:
                            - gaming
                        """,
                StandardCharsets.UTF_8
        );
    }
}
