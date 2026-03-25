package com.pmfb.gonogo.engine.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.decision.HumanReading;
import com.pmfb.gonogo.engine.decision.HumanReadingLevel;
import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.decision.Verdict;
import com.pmfb.gonogo.engine.job.JobInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BatchReportWriterTest {
    @Test
    void writesMarkdownAndJsonReports() throws IOException {
        BatchEvaluationReport report = new BatchEvaluationReport(
                "2026-02-22T10:15:30Z",
                "product_expat_engineer",
                "demo_candidate",
                2,
                1,
                1,
                1,
                0,
                0,
                List.of(
                        new BatchEvaluationItem(
                                "job-a.yaml",
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
                                        82,
                                        9,
                                        -17,
                                        16,
                                        10,
                                        74,
                                        List.of(),
                                        List.of("salary_transparency", "hybrid_work"),
                                        List.of(),
                                        List.of("Normalized score: 82/100."),
                                        new HumanReading(
                                                HumanReadingLevel.STRONG,
                                                HumanReadingLevel.STRONG,
                                                HumanReadingLevel.MIXED,
                                                HumanReadingLevel.STRONG,
                                                HumanReadingLevel.STRONG,
                                                "Worth serious consideration: strong backend and work-style fit.",
                                                List.of("Strong backend fit.", "Hybrid policy matches preferences."),
                                                List.of()
                                        )
                                )
                        )
                ),
                List.of(
                        new BatchEvaluationError("job-b.yaml", List.of("Missing field 'salary_range'."))
                )
        );

        Path tempDir = Files.createTempDirectory("gonogo-report-test");
        Path markdownPath = tempDir.resolve("report.md");
        Path jsonPath = tempDir.resolve("report.json");

        BatchReportWriter writer = new BatchReportWriter();
        writer.writeMarkdown(markdownPath, report);
        writer.writeJson(jsonPath, report);

        String markdown = Files.readString(markdownPath);
        String json = Files.readString(jsonPath);

        assertTrue(markdown.contains("Batch Evaluation Report"));
        assertTrue(markdown.contains("GO - Money Forward - Backend Engineer"));
        assertTrue(markdown.contains("candidate_profile: demo_candidate"));
        assertTrue(markdown.contains("language_friction_index: 10/100"));
        assertTrue(markdown.contains("company_reputation_index: 74/100"));
        assertTrue(markdown.contains("human_summary: Worth serious consideration: strong backend and work-style fit."));
        assertTrue(markdown.contains("job-b.yaml"));
        assertTrue(json.contains("\"persona\": \"product_expat_engineer\""));
        assertTrue(json.contains("\"candidate_profile\": \"demo_candidate\""));
        assertTrue(json.contains("\"source_file\": \"job-a.yaml\""));
        assertTrue(json.contains("\"raw_score\": 9"));
        assertTrue(json.contains("\"language_friction_index\": 10"));
        assertTrue(json.contains("\"company_reputation_index\": 74"));
        assertTrue(json.contains("\"human_reading\": {"));
        assertTrue(json.contains("\"access_fit\": \"strong\""));
        assertTrue(json.contains("\"human_summary\": \"Worth serious consideration: strong backend and work-style fit.\""));
    }
}
