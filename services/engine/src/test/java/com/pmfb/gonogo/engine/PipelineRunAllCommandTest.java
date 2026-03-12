package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class PipelineRunAllCommandTest {
    @Test
    void isolatesJobsOutputPerPersonaWhenRunningConcurrently() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-run-all-test");
        Path configDir = tempDir.resolve("config");
        Path rawDir = tempDir.resolve("raw");
        Path jobsDir = tempDir.resolve("output/jobs");
        Path batchDir = tempDir.resolve("output");
        Path weeklyDir = tempDir.resolve("weekly");

        writeConfig(configDir);
        Files.createDirectories(rawDir);
        Files.writeString(
                rawDir.resolve("job1.txt"),
                """
                        Company: Money Forward
                        Title: Backend Engineer
                        Location: Tokyo
                        Salary: JPY 9,000,000 - 12,000,000
                        Work style: Hybrid
                        English-first team with product ownership and code review.
                        """,
                StandardCharsets.UTF_8
        );

        int exitCode = new CommandLine(new GoNoGoCommand()).execute(
                "pipeline",
                "run-all",
                "--config-dir", configDir.toString(),
                "--raw-input-dir", rawDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", batchDir.toString(),
                "--weekly-output-dir", weeklyDir.toString(),
                "--skip-fetch-web",
                "--disable-trend-history",
                "--persona-concurrency", "2"
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(jobsDir.resolve("product_expat_engineer/job1.generated.yaml")));
        assertTrue(Files.exists(jobsDir.resolve("product_pragmatic_engineer/job1.generated.yaml")));
        assertTrue(Files.exists(batchDir.resolve("batch-evaluation-product_expat_engineer.json")));
        assertTrue(Files.exists(batchDir.resolve("batch-evaluation-product_pragmatic_engineer.json")));
        assertTrue(Files.exists(weeklyDir.resolve("weekly-product_expat_engineer.md")));
        assertTrue(Files.exists(weeklyDir.resolve("weekly-product_pragmatic_engineer.md")));
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
                          - id: product_pragmatic_engineer
                            description: "Pragmatic product engineer living in Japan"
                            priorities:
                              - english_environment
                              - engineering_culture
                              - salary
                              - stability
                            hard_no:
                              - onsite_only
                              - salary_missing
                            acceptable_if:
                              - hybrid_partial
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
