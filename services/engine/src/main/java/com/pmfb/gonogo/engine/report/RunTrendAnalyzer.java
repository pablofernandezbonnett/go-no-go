package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.decision.Verdict;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunTrendAnalyzer {
    private static final int COMPANY_MOVERS_LIMIT = 5;

    public RunTrendSnapshot fromBatchReport(BatchEvaluationReport report) {
        int evaluated = report.items().size();
        double avgScore = averageScore(report.items());
        double avgLanguageFriction = averageLanguageFriction(report.items());
        double avgCompanyReputation = averageCompanyReputation(report.items());
        List<RunTrendCompanyStats> companies = aggregateByCompany(report.items());

        return new RunTrendSnapshot(
                report.generatedAt(),
                report.personaId(),
                evaluated,
                report.goCount(),
                report.goWithCautionCount(),
                report.noGoCount(),
                report.newCount(),
                report.updatedCount(),
                report.unchangedCount(),
                report.removedCount(),
                avgScore,
                avgLanguageFriction,
                avgCompanyReputation,
                companies
        );
    }

    public String toMarkdown(RunTrendSnapshot previous, RunTrendSnapshot current) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Trend vs Previous Run\n\n");
        if (previous == null) {
            sb.append("- baseline: no previous run found; trend starts from this run.\n");
            return sb.toString();
        }

        sb.append("- previous_generated_at: ").append(previous.generatedAt()).append("\n");
        sb.append("- current_generated_at: ").append(current.generatedAt()).append("\n");
        sb.append("- evaluated_delta: ").append(formatSigned(current.evaluated() - previous.evaluated())).append("\n");
        sb.append("- go_delta: ").append(formatSigned(current.goCount() - previous.goCount())).append("\n");
        sb.append("- go_with_caution_delta: ")
                .append(formatSigned(current.goWithCautionCount() - previous.goWithCautionCount()))
                .append("\n");
        sb.append("- no_go_delta: ").append(formatSigned(current.noGoCount() - previous.noGoCount())).append("\n");
        sb.append("- new_delta: ").append(formatSigned(current.newCount() - previous.newCount())).append("\n");
        sb.append("- updated_delta: ").append(formatSigned(current.updatedCount() - previous.updatedCount())).append("\n");
        sb.append("- removed_delta: ").append(formatSigned(current.removedCount() - previous.removedCount())).append("\n");
        sb.append("- avg_score_delta: ")
                .append(formatSignedDecimal(current.averageScore() - previous.averageScore()))
                .append("\n");
        sb.append("- avg_language_friction_delta: ")
                .append(formatSignedDecimal(
                        current.averageLanguageFrictionIndex() - previous.averageLanguageFrictionIndex()
                ))
                .append("\n");
        sb.append("- avg_company_reputation_delta: ")
                .append(formatSignedDecimal(
                        current.averageCompanyReputationIndex() - previous.averageCompanyReputationIndex()
                ))
                .append("\n\n");

        sb.append("### Company Movers (avg score delta)\n\n");
        List<CompanyMover> movers = companyMovers(previous, current);
        if (movers.isEmpty()) {
            sb.append("No overlapping companies between runs.\n");
            return sb.toString();
        }

        for (CompanyMover mover : movers) {
            sb.append("- ")
                    .append(mover.company())
                    .append(": ")
                    .append(formatSignedDecimal(mover.scoreDelta()))
                    .append(" (")
                    .append(formatDecimal(mover.previousScore()))
                    .append(" -> ")
                    .append(formatDecimal(mover.currentScore()))
                    .append(", jobs ")
                    .append(mover.previousJobs())
                    .append(" -> ")
                    .append(mover.currentJobs())
                    .append(")\n");
        }
        return sb.toString();
    }

    private double averageScore(List<BatchEvaluationItem> items) {
        if (items.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (BatchEvaluationItem item : items) {
            total += item.evaluation().score();
        }
        return total / items.size();
    }

    private double averageLanguageFriction(List<BatchEvaluationItem> items) {
        if (items.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (BatchEvaluationItem item : items) {
            total += item.evaluation().languageFrictionIndex();
        }
        return total / items.size();
    }

    private double averageCompanyReputation(List<BatchEvaluationItem> items) {
        if (items.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (BatchEvaluationItem item : items) {
            total += item.evaluation().companyReputationIndex();
        }
        return total / items.size();
    }

    private List<RunTrendCompanyStats> aggregateByCompany(List<BatchEvaluationItem> items) {
        Map<String, CompanyAccumulator> byCompany = new HashMap<>();
        for (BatchEvaluationItem item : items) {
            String companyName = item.job().companyName().trim();
            if (companyName.isBlank()) {
                companyName = "Unknown Company";
            }
            final String displayCompany = companyName;
            CompanyAccumulator acc = byCompany.computeIfAbsent(
                    normalize(displayCompany),
                    key -> new CompanyAccumulator(displayCompany)
            );
            acc.accept(item.evaluation());
        }

        List<RunTrendCompanyStats> output = new ArrayList<>();
        for (CompanyAccumulator acc : byCompany.values()) {
            output.add(acc.toStats());
        }
        output.sort(Comparator.comparing(stats -> stats.company().toLowerCase(Locale.ROOT)));
        return output;
    }

    private List<CompanyMover> companyMovers(RunTrendSnapshot previous, RunTrendSnapshot current) {
        Map<String, RunTrendCompanyStats> prevByCompany = new LinkedHashMap<>();
        for (RunTrendCompanyStats entry : previous.companies()) {
            prevByCompany.put(normalize(entry.company()), entry);
        }

        List<CompanyMover> movers = new ArrayList<>();
        for (RunTrendCompanyStats entry : current.companies()) {
            RunTrendCompanyStats previousEntry = prevByCompany.get(normalize(entry.company()));
            if (previousEntry == null) {
                continue;
            }
            movers.add(new CompanyMover(
                    entry.company(),
                    entry.averageScore() - previousEntry.averageScore(),
                    previousEntry.averageScore(),
                    entry.averageScore(),
                    previousEntry.jobs(),
                    entry.jobs()
            ));
        }
        movers.sort(Comparator.comparingDouble((CompanyMover item) -> Math.abs(item.scoreDelta())).reversed());
        if (movers.size() > COMPANY_MOVERS_LIMIT) {
            return List.copyOf(movers.subList(0, COMPANY_MOVERS_LIMIT));
        }
        return List.copyOf(movers);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String formatSigned(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private String formatSignedDecimal(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static final class CompanyAccumulator {
        private final String company;
        private int jobs;
        private int goCount;
        private int goWithCautionCount;
        private int noGoCount;
        private double scoreTotal;
        private double languageFrictionTotal;
        private double companyReputationTotal;

        private CompanyAccumulator(String company) {
            this.company = company;
        }

        private void accept(EvaluationResult evaluation) {
            jobs++;
            scoreTotal += evaluation.score();
            languageFrictionTotal += evaluation.languageFrictionIndex();
            companyReputationTotal += evaluation.companyReputationIndex();
            if (evaluation.verdict() == Verdict.GO) {
                goCount++;
            } else if (evaluation.verdict() == Verdict.GO_WITH_CAUTION) {
                goWithCautionCount++;
            } else {
                noGoCount++;
            }
        }

        private RunTrendCompanyStats toStats() {
            double denominator = jobs == 0 ? 1.0 : jobs;
            return new RunTrendCompanyStats(
                    company,
                    jobs,
                    scoreTotal / denominator,
                    languageFrictionTotal / denominator,
                    companyReputationTotal / denominator,
                    goCount,
                    goWithCautionCount,
                    noGoCount
            );
        }
    }

    private record CompanyMover(
            String company,
            double scoreDelta,
            double previousScore,
            double currentScore,
            int previousJobs,
            int currentJobs
    ) {
    }
}
