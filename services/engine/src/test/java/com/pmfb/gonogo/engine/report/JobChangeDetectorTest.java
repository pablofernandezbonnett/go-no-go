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

final class JobChangeDetectorTest {
    @Test
    void marksFirstRunAsNewAndSecondRunAsUnchanged() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-change-detector-test");
        Path stateFile = tempDir.resolve("state.yaml");
        JobChangeDetector detector = new JobChangeDetector();

        List<BatchEvaluationItem> firstItems = List.of(
                item("jobs/a.generated.yaml", "Money Forward", "Backend Engineer", "Tokyo", "JPY 10M", "Hybrid", "desc")
        );
        JobChangeDetector.ChangeDetectionResult first = detector.detectAndPersist(firstItems, stateFile);

        assertEquals(1, first.newCount());
        assertEquals(0, first.updatedCount());
        assertEquals(0, first.unchangedCount());
        assertEquals(0, first.removedCount());
        assertEquals("NEW", first.items().get(0).changeStatus());
        assertTrue(Files.exists(stateFile));

        JobChangeDetector.ChangeDetectionResult second = detector.detectAndPersist(firstItems, stateFile);
        assertEquals(0, second.newCount());
        assertEquals(0, second.updatedCount());
        assertEquals(1, second.unchangedCount());
        assertEquals(0, second.removedCount());
        assertEquals("UNCHANGED", second.items().get(0).changeStatus());
    }

    @Test
    void detectsUpdatedNewAndRemovedBetweenRuns() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-change-detector-diff-test");
        Path stateFile = tempDir.resolve("state.yaml");
        JobChangeDetector detector = new JobChangeDetector();

        List<BatchEvaluationItem> baseline = List.of(
                item("jobs/a.generated.yaml", "Money Forward", "Backend Engineer", "Tokyo", "JPY 10M", "Hybrid", "desc-a"),
                item("jobs/b.generated.yaml", "Mercari", "Frontend Engineer", "Tokyo", "JPY 9M", "Remote", "desc-b")
        );
        detector.detectAndPersist(baseline, stateFile);

        List<BatchEvaluationItem> changed = List.of(
                item("jobs/a.generated.yaml", "Money Forward", "Backend Engineer", "Tokyo", "JPY 12M", "Hybrid", "desc-a-updated"),
                item("jobs/c.generated.yaml", "freee", "Platform Engineer", "Tokyo", "JPY 11M", "Hybrid", "desc-c")
        );
        JobChangeDetector.ChangeDetectionResult result = detector.detectAndPersist(changed, stateFile);

        assertEquals(1, result.newCount());
        assertEquals(1, result.updatedCount());
        assertEquals(0, result.unchangedCount());
        assertEquals(1, result.removedCount());
        assertTrue(result.items().stream().anyMatch(item -> "UPDATED".equals(item.changeStatus())));
        assertTrue(result.items().stream().anyMatch(item -> "NEW".equals(item.changeStatus())));
        assertEquals("Mercari", result.removedItems().get(0).company());
    }

    @Test
    void ignoresFetchedAtLineWhenCalculatingFingerprint() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-change-detector-fetched-at-test");
        Path stateFile = tempDir.resolve("state.yaml");
        JobChangeDetector detector = new JobChangeDetector();

        List<BatchEvaluationItem> firstRun = List.of(
                item(
                        "jobs/a.generated.yaml",
                        "Money Forward",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 10M",
                        "Hybrid",
                        """
                                Company: Money Forward
                                Source URL: https://example.com/jobs/backend
                                Fetched At: 2026-02-22T10:00:00Z
                                Description: Stable content
                                """
                )
        );
        detector.detectAndPersist(firstRun, stateFile);

        List<BatchEvaluationItem> secondRun = List.of(
                item(
                        "jobs/a.generated.yaml",
                        "Money Forward",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 10M",
                        "Hybrid",
                        """
                                Company: Money Forward
                                Source URL: https://example.com/jobs/backend
                                Fetched At: 2026-02-23T10:00:00Z
                                Description: Stable content
                                """
                )
        );
        JobChangeDetector.ChangeDetectionResult result = detector.detectAndPersist(secondRun, stateFile);

        assertEquals(1, result.unchangedCount());
        assertEquals(0, result.updatedCount());
        assertEquals("UNCHANGED", result.items().get(0).changeStatus());
    }

    private BatchEvaluationItem item(
            String sourceFile,
            String company,
            String title,
            String location,
            String salary,
            String remote,
            String description
    ) {
        return new BatchEvaluationItem(
                sourceFile,
                new JobInput(company, title, location, salary, remote, description),
                new EvaluationResult(
                        Verdict.GO,
                        80,
                        8,
                        -17,
                        16,
                        10,
                        50,
                        List.of(),
                        List.of("salary_transparency"),
                        List.of(),
                        List.of("reason")
                )
        );
    }
}
