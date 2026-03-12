package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class EvaluateInputCommandTest {
    @Test
    void writesJsonAndYamlArtifactWhenReadingRawTextFromStdin() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-evaluate-input-test");
        Path configDir = tempDir.resolve("config");
        Path artifactFile = tempDir.resolve("output/ad-hoc-evaluations/test-analysis.yaml");
        writeConfig(configDir);

        String rawText = """
                Company: Money Forward
                Title: Backend Engineer
                Location: Tokyo
                Salary: JPY 9,000,000 - 12,000,000
                Work style: Hybrid
                English-first team with product ownership and code review.
                """;

        ByteArrayInputStream stdin = new ByteArrayInputStream(rawText.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        java.io.InputStream originalIn = System.in;
        try {
            System.setIn(stdin);
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

            int exitCode = new CommandLine(new EvaluateInputCommand()).execute(
                    "--persona", "product_expat_engineer",
                    "--config-dir", configDir.toString(),
                    "--stdin",
                    "--output-format", "json",
                    "--output-analysis-file", artifactFile.toString()
            );

            assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"persona\":\"product_expat_engineer\""));
        assertTrue(json.contains("\"candidate_profile\":\"none\""));
        assertTrue(json.contains("\"analysis_file\":\"" + artifactFile + "\""));
        assertTrue(json.contains("\"source\":{\"kind\":\"stdin\""));
        assertTrue(Files.exists(artifactFile));

        String yaml = Files.readString(artifactFile);
        assertTrue(yaml.contains("persona: product_expat_engineer"));
        assertTrue(yaml.contains("candidate_profile: none"));
        assertTrue(yaml.contains("kind: stdin"));
        assertTrue(yaml.contains("raw_text:"));
        assertTrue(yaml.contains("Company: Money Forward"));
    }

    @Test
    void rejectsLocalhostUrlInUrlMode() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-evaluate-input-url-test");
        Path configDir = tempDir.resolve("config");
        writeConfig(configDir);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

            int exitCode = new CommandLine(new EvaluateInputCommand()).execute(
                    "--persona", "product_expat_engineer",
                    "--config-dir", configDir.toString(),
                    "--job-url", "http://localhost:8080/jobs/backend-engineer"
            );

            assertEquals(1, exitCode);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String errorText = stderr.toString(StandardCharsets.UTF_8);
        assertTrue(errorText.contains("Local or internal hosts are not allowed."));
    }

    private void writeConfig(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Files.writeString(
                configDir.resolve("companies.yaml"),
                """
                        companies:
                          - id: moneyforward
                            name: Money Forward
                            career_url: https://corp.moneyforward.com/recruit/
                            type_hint: fintech_product
                            region: japan
                            notes: "Product company."
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                configDir.resolve("personas.yaml"),
                """
                        personas:
                          - id: product_expat_engineer
                            description: "Product-oriented expat engineer living in Japan"
                            priorities:
                              - english_environment
                              - product_company
                              - engineering_culture
                              - hybrid_work
                              - work_life_balance
                              - salary
                              - stability
                            hard_no:
                              - consulting_company
                              - onsite_only
                              - salary_missing
                              - early_stage_startup
                            acceptable_if:
                              - hybrid_partial
                              - japanese_not_blocking
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
    }
}
