package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class NormalizeAdHocCompanyNamesCommandTest {
    @Test
    void rewritesGenericRecruiterPlaceholdersFromSavedUrlArtifacts() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-normalize-adhoc-company-test");
        Path configDir = tempDir.resolve("config");
        writeConfig(configDir);
        Path artifactDir = tempDir.resolve("output/ad-hoc-evaluations");
        Files.createDirectories(artifactDir);
        Path artifactFile = artifactDir.resolve("job.yaml");
        Files.writeString(
                artifactFile,
                """
                        generated_at: '2026-04-14T00:00:00Z'
                        persona: product_expat_engineer
                        candidate_profile: none
                        source:
                          kind: url
                          url: https://www.michaelpage.co.jp/en/job-detail/senior-software-engineer/jn-042026-6994215
                        job_input:
                          company_name: Our Client
                          title: Senior Software Engineer - Based in Japan
                          location: Tokyo, Japan
                          salary_range: ""
                          remote_policy: ""
                          description: |-
                            About Our Client. Build backend services for a growing platform team in Tokyo, Japan.
                            Source URL: https://www.michaelpage.co.jp/en/job-detail/senior-software-engineer/jn-042026-6994215
                        evaluation:
                          verdict: GO
                        normalization_warnings: []
                        """,
                StandardCharsets.UTF_8
        );

        int exitCode = new CommandLine(new NormalizeAdHocCompanyNamesCommand()).execute(
                "--config-dir", configDir.toString(),
                "--input-dir", artifactDir.toString()
        );

        assertEquals(0, exitCode);
        String yaml = Files.readString(artifactFile);
        assertTrue(yaml.contains("company_name: Michael Page"), yaml);
        assertFalse(yaml.contains("company_name: Our Client"), yaml);
        assertTrue(yaml.contains("verdict: GO"), yaml);
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
}
