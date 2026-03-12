package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.decision.DecisionEngineV1;
import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.decision.Verdict;
import com.pmfb.gonogo.engine.job.CareerPageFetchService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class PipelineRunCommandTest {
    @Test
    void runsPipelineEndToEnd() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-pipeline-test");
        Path configDir = tempDir.resolve("config");
        Path rawDir = tempDir.resolve("raw");
        Path jobsDir = tempDir.resolve("output/jobs");
        Path outputDir = tempDir.resolve("output");
        Path weeklyPath = outputDir.resolve("weekly.md");

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
                "run",
                "--persona", "product_expat_engineer",
                "--raw-input-dir", rawDir.toString(),
                "--raw-pattern", "*.txt",
                "--config-dir", configDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", outputDir.toString(),
                "--weekly-output-file", weeklyPath.toString()
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(jobsDir.resolve("job1.generated.yaml")));
        assertTrue(Files.exists(outputDir.resolve("batch-evaluation-product_expat_engineer.json")));
        assertTrue(Files.exists(outputDir.resolve("batch-evaluation-product_expat_engineer.md")));
        assertTrue(Files.exists(weeklyPath));
    }

    @Test
    void runsPipelineWithFetchWebFirst() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/careers",
                exchange -> {
                    String html = """
                            <html>
                              <body>
                                <a href="/jobs/backend">Backend Engineer</a>
                                <a href="/jobs/frontend">Frontend Engineer</a>
                              </body>
                            </html>
                            """;
                    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                }
        );
        server.start();

        try {
            String careerUrl = "http://localhost:" + server.getAddress().getPort() + "/careers";
            Path tempDir = Files.createTempDirectory("gonogo-pipeline-fetch-web-test");
            Path configDir = tempDir.resolve("config");
            Path rawDir = tempDir.resolve("raw");
            Path jobsDir = tempDir.resolve("output/jobs");
            Path outputDir = tempDir.resolve("output");
            Path weeklyPath = outputDir.resolve("weekly.md");

            writeConfig(configDir, careerUrl);

            int exitCode = new CommandLine(new GoNoGoCommand()).execute(
                    "pipeline",
                    "run",
                    "--persona", "product_expat_engineer",
                    "--raw-input-dir", rawDir.toString(),
                    "--raw-pattern", "*.txt",
                    "--fetch-web-first",
                    "--fetch-web-company-ids", "moneyforward",
                    "--config-dir", configDir.toString(),
                    "--jobs-output-dir", jobsDir.toString(),
                    "--batch-output-dir", outputDir.toString(),
                    "--weekly-output-file", weeklyPath.toString()
            );

            assertEquals(0, exitCode);
            assertTrue(Files.exists(rawDir.resolve("moneyforward/01-backend-engineer.txt")));
            assertTrue(Files.exists(jobsDir.resolve("moneyforward/01-backend-engineer.generated.yaml")));
            assertTrue(Files.exists(outputDir.resolve("batch-evaluation-product_expat_engineer.json")));
            assertTrue(Files.exists(weeklyPath));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsWhenWarningsExistAndFailOnWarningsIsEnabled() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-pipeline-warn-test");
        Path configDir = tempDir.resolve("config");
        Path rawDir = tempDir.resolve("raw");
        Path jobsDir = tempDir.resolve("output/jobs");
        Path outputDir = tempDir.resolve("output");
        Path weeklyPath = outputDir.resolve("weekly.md");

        writeConfig(configDir);
        Files.createDirectories(rawDir);
        Files.writeString(
                rawDir.resolve("job-warning.txt"),
                """
                        Company: Money Forward
                        Title: Backend Engineer
                        We are hiring in Tokyo.
                        Hybrid team with English-first communication.
                        """,
                StandardCharsets.UTF_8
        );

        int exitCode = new CommandLine(new GoNoGoCommand()).execute(
                "pipeline",
                "run",
                "--persona", "product_expat_engineer",
                "--raw-input-dir", rawDir.toString(),
                "--raw-pattern", "*.txt",
                "--config-dir", configDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", outputDir.toString(),
                "--weekly-output-file", weeklyPath.toString(),
                "--fail-on-warnings"
        );

        assertEquals(2, exitCode);
        assertTrue(Files.exists(jobsDir.resolve("job-warning.generated.yaml")));
        assertTrue(Files.exists(outputDir.resolve("batch-evaluation-product_expat_engineer.json")));
        assertTrue(Files.exists(weeklyPath));
    }

    @Test
    void incrementalOnlyEvaluatesNewAndUpdatedJobs() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-pipeline-incremental-test");
        Path configDir = tempDir.resolve("config");
        Path rawDir = tempDir.resolve("raw");
        Path jobsDir = tempDir.resolve("output/jobs");
        Path outputDir = tempDir.resolve("output");
        Path weeklyPath = outputDir.resolve("weekly.md");
        Path stateFile = outputDir.resolve("job-change-state.yaml");

        writeConfig(configDir);
        Files.createDirectories(rawDir);
        Files.writeString(
                rawDir.resolve("a.txt"),
                """
                        Company: Money Forward
                        Title: Backend Engineer
                        Location: Tokyo
                        Salary: JPY 9,000,000 - 12,000,000
                        Work style: Hybrid
                        English-first team with product ownership.
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                rawDir.resolve("b.txt"),
                """
                        Company: Mercari
                        Title: Frontend Engineer
                        Location: Tokyo
                        Salary: JPY 8,000,000 - 10,000,000
                        Work style: Hybrid
                        Product role.
                        """,
                StandardCharsets.UTF_8
        );

        int firstExit = new CommandLine(new GoNoGoCommand()).execute(
                "pipeline",
                "run",
                "--persona", "product_expat_engineer",
                "--raw-input-dir", rawDir.toString(),
                "--raw-pattern", "*.txt",
                "--config-dir", configDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", outputDir.toString(),
                "--weekly-output-file", weeklyPath.toString(),
                "--change-state-file", stateFile.toString(),
                "--incremental-only"
        );
        assertEquals(0, firstExit);

        Files.writeString(
                rawDir.resolve("b.txt"),
                """
                        Company: Mercari
                        Title: Frontend Engineer
                        Location: Tokyo
                        Salary: JPY 8,500,000 - 10,500,000
                        Work style: Hybrid
                        Product role with code review.
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                rawDir.resolve("c.txt"),
                """
                        Company: freee
                        Title: Platform Engineer
                        Location: Tokyo
                        Salary: JPY 10,000,000 - 13,000,000
                        Work style: Hybrid
                        English-first environment.
                        """,
                StandardCharsets.UTF_8
        );

        int secondExit = new CommandLine(new GoNoGoCommand()).execute(
                "pipeline",
                "run",
                "--persona", "product_expat_engineer",
                "--raw-input-dir", rawDir.toString(),
                "--raw-pattern", "*.txt",
                "--config-dir", configDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", outputDir.toString(),
                "--weekly-output-file", weeklyPath.toString(),
                "--change-state-file", stateFile.toString(),
                "--incremental-only"
        );
        assertEquals(0, secondExit);

        String json = Files.readString(outputDir.resolve("batch-evaluation-product_expat_engineer.json"));
        assertTrue(json.contains("\"evaluated\": 2"));
        assertTrue(json.contains("\"new\": 1"));
        assertTrue(json.contains("\"updated\": 1"));
        assertTrue(json.contains("\"unchanged\": 1"));
    }

    @Test
    void autoSelectsSingleCandidateProfileAtRuntime() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-pipeline-candidate-test");
        Path configDir = tempDir.resolve("config");
        Path rawDir = tempDir.resolve("raw");
        Path jobsDir = tempDir.resolve("output/jobs");
        Path outputDir = tempDir.resolve("output");
        Path weeklyPath = outputDir.resolve("weekly.md");

        writeConfig(configDir);
        writeCandidateProfile(configDir);
        Files.createDirectories(rawDir);
        Files.writeString(
                rawDir.resolve("job1.txt"),
                """
                        Company: avatarin
                        Title: Senior Backend Engineer
                        Location: Tokyo
                        Salary: JPY 8,000,000 - 10,000,000
                        Work style: Hybrid
                        Requirements:
                        - 5+ years of experience in Java and Spring Boot backend development
                        - Experience with AWS and system design
                        """,
                StandardCharsets.UTF_8
        );

        int exitCode = new CommandLine(new GoNoGoCommand()).execute(
                "pipeline",
                "run",
                "--persona", "product_expat_engineer",
                "--raw-input-dir", rawDir.toString(),
                "--raw-pattern", "*.txt",
                "--config-dir", configDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", outputDir.toString(),
                "--weekly-output-file", weeklyPath.toString()
        );

        Path batchJsonPath = outputDir.resolve("batch-evaluation-product_expat_engineer--pmfb.json");

        assertEquals(0, exitCode);
        assertTrue(Files.exists(batchJsonPath));
        assertTrue(Files.readString(batchJsonPath).contains("\"candidate_profile\": \"pmfb\""));
        assertTrue(Files.readString(weeklyPath).contains("candidate_profile: pmfb"));
    }

    @Test
    void keepsBatchItemOrderStableWhenEvaluationRunsInParallel() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-pipeline-eval-order-test");
        Path configDir = tempDir.resolve("config");
        Path rawDir = tempDir.resolve("raw");
        Path jobsDir = tempDir.resolve("output/jobs");
        Path outputDir = tempDir.resolve("output");
        Path weeklyPath = outputDir.resolve("weekly.md");

        writeConfig(configDir);
        Files.createDirectories(rawDir);
        Files.writeString(
                rawDir.resolve("a.txt"),
                """
                        Company: Money Forward
                        Title: Slow Job
                        Location: Tokyo
                        Salary: JPY 9,000,000 - 12,000,000
                        Work style: Hybrid
                        English-first team.
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                rawDir.resolve("b.txt"),
                """
                        Company: Money Forward
                        Title: Fast Job
                        Location: Tokyo
                        Salary: JPY 9,000,000 - 12,000,000
                        Work style: Hybrid
                        English-first team.
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                rawDir.resolve("c.txt"),
                """
                        Company: Money Forward
                        Title: Medium Job
                        Location: Tokyo
                        Salary: JPY 9,000,000 - 12,000,000
                        Work style: Hybrid
                        English-first team.
                        """,
                StandardCharsets.UTF_8
        );

        PipelineRunCommand.JobEvaluationService evaluator = (job, persona, candidateProfile, config, externalContext) -> {
            sleepForTitle(job.title());
            return new EvaluationResult(
                    Verdict.GO,
                    80,
                    10,
                    -20,
                    20,
                    0,
                    50,
                    java.util.List.of(),
                    java.util.List.of("english_environment"),
                    java.util.List.of(),
                    java.util.List.of(job.title())
            );
        };

        int exitCode = new CommandLine(new PipelineRunCommand(
                new DecisionEngineV1(),
                new CareerPageFetchService(),
                evaluator
        )).execute(
                "--persona", "product_expat_engineer",
                "--raw-input-dir", rawDir.toString(),
                "--raw-pattern", "*.txt",
                "--config-dir", configDir.toString(),
                "--jobs-output-dir", jobsDir.toString(),
                "--batch-output-dir", outputDir.toString(),
                "--weekly-output-file", weeklyPath.toString(),
                "--disable-change-detection",
                "--evaluate-max-concurrency", "3"
        );

        assertEquals(0, exitCode);

        String json = Files.readString(outputDir.resolve("batch-evaluation-product_expat_engineer.json"));
        int first = json.indexOf("\"source_file\": \"a.generated.yaml\"");
        int second = json.indexOf("\"source_file\": \"b.generated.yaml\"");
        int third = json.indexOf("\"source_file\": \"c.generated.yaml\"");

        assertTrue(first >= 0);
        assertTrue(second > first);
        assertTrue(third > second);
    }

    private void writeConfig(Path configDir) throws IOException {
        writeConfig(configDir, "https://corp.moneyforward.com/recruit/");
    }

    private static void sleepForTitle(String title) {
        long delayMillis = switch (title) {
            case "Slow Job" -> 200L;
            case "Medium Job" -> 100L;
            default -> 10L;
        };
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating evaluation latency.", e);
        }
    }

    private void writeConfig(Path configDir, String careerUrl) throws IOException {
        Files.createDirectories(configDir);
        Files.writeString(
                configDir.resolve("companies.yaml"),
                """
                        companies:
                          - id: moneyforward
                            name: Money Forward
                            career_url: %s
                            type_hint: fintech_product
                            region: japan
                            notes: "Product company."
                        """.formatted(careerUrl),
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
                        blacklisted_companies:
                          - name: Randstad
                            reason: "Recruitment / dispatch company"
                        """,
                StandardCharsets.UTF_8
        );
    }

    private void writeCandidateProfile(Path configDir) throws IOException {
        Path candidateProfilesDir = configDir.resolve("candidate-profiles");
        Files.createDirectories(candidateProfilesDir);
        Files.writeString(
                candidateProfilesDir.resolve("demo_candidate.yaml"),
                """
                        candidate:
                          name: "Demo Candidate"
                          title: "Senior Backend Engineer"
                          location: "Tokyo"
                          total_experience_years: 8
                        stack:
                          production_proven:
                            backend:
                              - Java
                              - Spring Boot
                              - AWS
                          actively_learning:
                            - Kubernetes
                          gaps_honest:
                            - Flutter
                        domain_expertise:
                          strong:
                            - enterprise_java
                            - system_design
                          moderate:
                            - cloud_basics
                          limited:
                            - mobile_cross_platform
                        """,
                StandardCharsets.UTF_8
        );
    }
}
