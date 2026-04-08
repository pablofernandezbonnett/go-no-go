package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.pmfb.gonogo.engine.job.CareerPageHttpFetcher;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class EvaluateInputCommandTest {
    @Test
    void autoSelectsSingleCandidateProfileWhenNoneIsSpecified() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-evaluate-input-profile-auto-test");
        Path configDir = tempDir.resolve("config");
        writeConfig(configDir);
        writeCandidateProfile(configDir);

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
                    "--output-format", "json"
            );

            assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"candidate_profile\":\"demo_candidate\""));
    }

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

    @Test
    void extractsSalaryAndEnglishSupportSignalsFromJobUrlPages() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-evaluate-fast-retailing-test");
        Path configDir = tempDir.resolve("config");
        writeFastRetailingConfig(configDir);

        String html = """
                <html>
                  <head>
                    <title>Application Engineer | Fast Retailing Group Recruiting Information</title>
                  </head>
                  <body>
                    <main>
                      <h1>Application Engineer</h1>
                      <section>
                        <h2>Qualifications</h2>
                        <p>Individuals who can proactively engage in a work environment requiring daily English usage.</p>
                        <p>Current fluency is not a requirement. We provide an environment for learning through actual work and support to become comfortable with English.</p>
                      </section>
                      <section>
                        <h2>Work Conditions</h2>
                        <p>Location: Ariake Headquarters, Tokyo</p>
                        <p>・Annual Salary Range: JPY 6,560,000 - JPY 21,240,000</p>
                        <p>・Flextime system</p>
                      </section>
                    </main>
                  </body>
                </html>
                """;

        com.pmfb.gonogo.engine.job.CareerPageFetcher stubFetcher = new StubCareerPageFetcher(
                "https://www.fastretailing.com/careers/en/job-description/?id=1588",
                html
        );
        EvaluateInputCommand command = new EvaluateInputCommand(
                new com.pmfb.gonogo.engine.decision.DecisionEngineV1(),
                new com.pmfb.gonogo.engine.job.RawJobParser(),
                new com.pmfb.gonogo.engine.job.JobPostingExtractor(),
                stubFetcher,
                new DirectInputSecurity(),
                new EvaluateInputArtifactWriter(),
                new EvaluateInputOutputFormatter()
        );

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

            int exitCode = new CommandLine(command).execute(
                    "--persona", "product_expat_engineer",
                    "--config-dir", configDir.toString(),
                    "--job-url", "https://www.fastretailing.com/careers/en/job-description/?id=1588",
                    "--output-format", "json"
            );

            assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"salary_range\":\"JPY 6,560,000 - JPY 21,240,000\""));
        assertTrue(json.contains("\"positive_signals\":["));
        assertTrue(json.contains("\"english_support_environment\""));
        assertFalse(json.contains("\"language_friction\""));
        assertFalse(json.contains("salary information is missing or non-transparent"));
        assertFalse(json.contains("\"verdict\":\"NO_GO\""));
        assertTrue(readIntField(json, "language_friction_index") <= 20, json);
    }

    @Test
    void ignoresRelatedJobsAndTreatsNoRemoteAsOnsiteOnlyForTokyodevStylePages() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-evaluate-tokyodev-detail-test");
        Path configDir = tempDir.resolve("config");
        writeConfig(configDir);

        String html = """
                <html>
                  <head>
                    <title>Software Engineer - Lunaris | TokyoDev</title>
                  </head>
                  <body>
                    <header>
                      <section id="job-header">
                        <h1>Software Engineer</h1>
                        <p>Lunaris</p>
                        <ul>
                          <li>¥4.5M ~ ¥8M annually</li>
                          <li>No remote</li>
                          <li>Japan residents only</li>
                          <li>No Japanese required</li>
                        </ul>
                      </section>
                    </header>
                    <main>
                      <nav>
                        <a href="/jobs">Jobs</a>
                        <a href="/articles">Articles</a>
                      </nav>
                      <h1>Software Engineer</h1>
                      <section>
                        <h2>About Lunaris</h2>
                        <p>We started out as an otaku-centric online shop called Solaris Japan, and now offer solutions to other e-commerce platforms.</p>
                      </section>
                      <section>
                        <h2>About the position</h2>
                        <p>We’re looking for a software engineer to join us.</p>
                        <p>Requirements</p>
                        <p>At least 2 years of experience as a software engineer</p>
                        <p>Experience with database design and writing performant SQL queries using PostgreSQL</p>
                        <p>Nice to haves</p>
                        <p>Experience with front-end technologies such as React (preferred), Vue or Angular</p>
                      </section>
                      <section>
                        <h2>Hiring Process</h2>
                        <p>In-person or remote interview with the team</p>
                      </section>
                      <section>
                        <h2>Meet Lunaris's Developers</h2>
                        <p>Developer story content that should not be used for evaluation.</p>
                      </section>
                      <section>
                        <h2>Related jobs</h2>
                        <a href="https://www.tokyodev.com/companies/rapyuta/jobs/software-engineer">Software Engineer - Robotics Control Systems</a>
                        <p>No remote</p>
                        <p>Partially remote</p>
                      </section>
                    </main>
                  </body>
                </html>
                """;

        com.pmfb.gonogo.engine.job.CareerPageFetcher stubFetcher = new StubCareerPageFetcher(
                "https://www.tokyodev.com/companies/lunaris/jobs/software-engineer",
                html
        );
        EvaluateInputCommand command = new EvaluateInputCommand(
                new com.pmfb.gonogo.engine.decision.DecisionEngineV1(),
                new com.pmfb.gonogo.engine.job.RawJobParser(),
                new com.pmfb.gonogo.engine.job.JobPostingExtractor(),
                stubFetcher,
                new DirectInputSecurity(),
                new EvaluateInputArtifactWriter(),
                new EvaluateInputOutputFormatter()
        );

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

            int exitCode = new CommandLine(command).execute(
                    "--persona", "product_expat_engineer",
                    "--config-dir", configDir.toString(),
                    "--job-url", "https://www.tokyodev.com/companies/lunaris/jobs/software-engineer",
                    "--output-format", "json"
            );

            assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"company_name\":\"Lunaris\""), json);
        assertTrue(json.contains("\"title\":\"Software Engineer\""), json);
        assertTrue(json.contains("\"remote_policy\":\"Onsite-only\""), json);
        assertTrue(json.contains("\"onsite_bias\""), json);
        assertFalse(json.contains("onsite-only work policy detected"), json);
        assertFalse(json.contains("\"remote_friendly\""), json);
        assertFalse(json.contains("Robotics Control Systems"), json);
    }

    @Test
    void usesMetadataFallbackWhenUrlPageIsClientRenderedShell() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-evaluate-workable-shell-test");
        Path configDir = tempDir.resolve("config");
        writeConfig(configDir);

        String html = """
                <html>
                  <head>
                    <title>Backend Engineer, Ruby - KOMOJU</title>
                    <meta property="og:title" content="Backend Engineer, Ruby - KOMOJU">
                    <meta name="description" content="About KOMOJU. Strong experience building backend systems using Ruby on Rails in production environments. We prefer Ruby experience, but candidates with solid experience in other backend web frameworks are also welcome. Tokyo, Japan.">
                  </head>
                  <body>
                    <div id="app"></div>
                  </body>
                </html>
                """;

        com.pmfb.gonogo.engine.job.CareerPageFetcher stubFetcher = new StubCareerPageFetcher(
                "https://apply.workable.com/degica-hiring/j/6E3C340851/",
                html
        );
        EvaluateInputCommand command = new EvaluateInputCommand(
                new com.pmfb.gonogo.engine.decision.DecisionEngineV1(),
                new com.pmfb.gonogo.engine.job.RawJobParser(),
                new com.pmfb.gonogo.engine.job.JobPostingExtractor(),
                stubFetcher,
                new DirectInputSecurity(),
                new EvaluateInputArtifactWriter(),
                new EvaluateInputOutputFormatter()
        );

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

            int exitCode = new CommandLine(command).execute(
                    "--persona", "product_expat_engineer",
                    "--config-dir", configDir.toString(),
                    "--job-url", "https://apply.workable.com/degica-hiring/j/6E3C340851/",
                    "--output-format", "json"
            );

            assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String json = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"company_name\":\"KOMOJU\""));
        assertTrue(json.contains("\"title\":\"Backend Engineer, Ruby - KOMOJU\""));
        assertTrue(json.contains("other backend web frameworks are also welcome"));
        assertTrue(json.contains("Page body was unavailable; evaluated the URL using title and metadata only."));
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

    private void writeCandidateProfile(Path configDir) throws IOException {
        Path profilesDir = configDir.resolve("candidate-profiles");
        Files.createDirectories(profilesDir);
        Files.writeString(
                profilesDir.resolve("demo_candidate.yaml"),
                """
                        candidate:
                          name: Example Candidate
                          title: Senior Product Backend Engineer
                          location: Tokyo, Japan
                          total_experience_years: 12
                        stack:
                          production_proven:
                            backend:
                              - Java
                              - Spring Boot
                          actively_learning:
                            - Kotlin
                          gaps_honest:
                            - Kafka in production
                        domain_expertise:
                          strong:
                            - ecommerce_platforms
                          moderate:
                            - cloud_basics
                          limited:
                            - kubernetes
                        """,
                StandardCharsets.UTF_8
        );
    }

    private void writeFastRetailingConfig(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Files.writeString(
                configDir.resolve("companies.yaml"),
                """
                        companies:
                          - id: fast_retailing
                            name: Fast Retailing
                            career_url: https://www.fastretailing.com/employment/en/
                            corporate_url: https://www.fastretailing.com/eng/
                            type_hint: retail_product
                            region: japan
                            notes: "Retail tech transformation focus."
                            profile_tags:
                              - stable_public
                              - product_leader
                            risk_tags:
                              - language_friction_high
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

    private int readIntField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\":(\\d+)");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), "Missing integer field '" + fieldName + "' in JSON: " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static final class StubCareerPageFetcher implements com.pmfb.gonogo.engine.job.CareerPageFetcher {
        private final String finalUrl;
        private final String body;

        private StubCareerPageFetcher(String finalUrl, String body) {
            this.finalUrl = finalUrl;
            this.body = body;
        }

        @Override
        public CareerPageHttpFetcher.FetchResult fetch(String url, Duration timeout, String userAgent) {
            return new CareerPageHttpFetcher.FetchResult(200, finalUrl, body);
        }
    }
}
