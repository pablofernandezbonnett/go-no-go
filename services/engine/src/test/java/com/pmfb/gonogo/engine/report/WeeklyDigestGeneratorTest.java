package com.pmfb.gonogo.engine.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.decision.Verdict;
import com.pmfb.gonogo.engine.job.JobInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WeeklyDigestGeneratorTest {
    @Test
    void buildsDigestFromBatchJson() throws IOException {
        BatchEvaluationReport report = new BatchEvaluationReport(
                "2026-02-22T12:00:00Z",
                "product_expat_engineer",
                "demo_candidate",
                3,
                3,
                0,
                1,
                1,
                1,
                List.of(
                        new BatchEvaluationItem(
                                "go.yaml",
                                new JobInput(
                                        "Money Forward",
                                        "Backend Engineer",
                                        "Tokyo",
                                        "JPY 10,000,000 - 12,000,000",
                                        "Hybrid",
                                        "English-first team."
                                ),
                                new EvaluationResult(
                                        Verdict.GO,
                                        85,
                                        10,
                                        -17,
                                        16,
                                        8,
                                        78,
                                        List.of(),
                                        List.of("salary_transparency", "hybrid_work"),
                                        List.of(),
                                        List.of("Final verdict derived from weighted score: GO.")
                                )
                        ),
                        new BatchEvaluationItem(
                                "caution.yaml",
                                new JobInput(
                                        "Rakuten",
                                        "Backend Engineer",
                                        "Tokyo",
                                        "JPY 8,000,000 - 10,000,000",
                                        "Hybrid",
                                        "Business Japanese required."
                                ),
                                new EvaluationResult(
                                        Verdict.GO_WITH_CAUTION,
                                        56,
                                        2,
                                        -17,
                                        16,
                                        85,
                                        38,
                                        List.of(),
                                        List.of("hybrid_work"),
                                        List.of("language_friction"),
                                        List.of("Risk signals: language_friction.")
                                )
                        ),
                        new BatchEvaluationItem(
                                "nogo.yaml",
                                new JobInput(
                                        "Randstad",
                                        "Engineer",
                                        "Tokyo",
                                        "TBD",
                                        "Onsite-only",
                                        "Consulting role."
                                ),
                                new EvaluationResult(
                                        Verdict.NO_GO,
                                        8,
                                        -10,
                                        -17,
                                        16,
                                        40,
                                        22,
                                        List.of("consulting / dispatch indicators detected"),
                                        List.of(),
                                        List.of("consulting_risk"),
                                        List.of("Final verdict forced to NO_GO due to hard filters.")
                                )
                        )
                ),
                List.of()
        );

        Path tempDir = Files.createTempDirectory("gonogo-weekly-test");
        Path jsonPath = tempDir.resolve("batch.json");
        new BatchReportWriter().writeJson(jsonPath, report);

        String json = Files.readString(jsonPath);
        WeeklyDigestGenerator generator = new WeeklyDigestGenerator();
        WeeklyDigestData data = generator.fromBatchReportJson(jsonPath.toString(), json);
        String markdown = generator.toMarkdown(data, 5);

        assertEquals("product_expat_engineer", data.persona());
        assertEquals("demo_candidate", data.candidateProfileId());
        assertEquals(3, data.items().size());
        assertTrue(markdown.contains("# Weekly Digest"));
        assertTrue(markdown.contains("candidate_profile: demo_candidate"));
        assertTrue(markdown.contains("## Top GO"));
        assertTrue(markdown.contains("Money Forward - Backend Engineer"));
        assertTrue(markdown.contains("language_friction_index: 8/100"));
        assertTrue(markdown.contains("company_reputation_index: 78/100"));
        assertTrue(markdown.contains("## GO With Caution"));
        assertTrue(markdown.contains("Rakuten - Backend Engineer"));
        assertTrue(markdown.contains("language_friction_index: 85/100"));
        assertTrue(markdown.contains("company_reputation_index: 38/100"));
        assertTrue(markdown.contains("## NO_GO Highlights"));
        assertTrue(markdown.contains("Randstad - Engineer"));
    }

    @Test
    void buildsDigestFromBatchReportObject() {
        BatchEvaluationReport report = new BatchEvaluationReport(
                "2026-02-22T12:00:00Z",
                "product_expat_engineer",
                "demo_candidate",
                1,
                1,
                0,
                1,
                0,
                0,
                List.of(
                        new BatchEvaluationItem(
                                "go.yaml",
                                new JobInput(
                                        "Money Forward",
                                        "Backend Engineer",
                                        "Tokyo",
                                        "JPY 10,000,000 - 12,000,000",
                                        "Hybrid",
                                        "English-first team."
                                ),
                                new EvaluationResult(
                                        Verdict.GO,
                                        85,
                                        10,
                                        -17,
                                        16,
                                        5,
                                        66,
                                        List.of(),
                                        List.of("salary_transparency"),
                                        List.of(),
                                        List.of("Final verdict derived from weighted score: GO.")
                                )
                        )
                ),
                List.of()
        );

        WeeklyDigestGenerator generator = new WeeklyDigestGenerator();
        WeeklyDigestData data = generator.fromBatchReport("output/batch.json", report);
        String markdown = generator.toMarkdown(data, 5);

        assertEquals("product_expat_engineer", data.persona());
        assertEquals("demo_candidate", data.candidateProfileId());
        assertEquals(1, data.items().size());
        assertEquals(5, data.items().get(0).languageFrictionIndex());
        assertEquals(66, data.items().get(0).companyReputationIndex());
        assertTrue(markdown.contains("Money Forward - Backend Engineer"));
        assertTrue(markdown.contains("## Top GO"));
    }
}
