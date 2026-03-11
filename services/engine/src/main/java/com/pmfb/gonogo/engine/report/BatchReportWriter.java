package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.config.ConfigSelections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BatchReportWriter {
    public void writeJson(Path outputFile, BatchEvaluationReport report) throws IOException {
        ensureParentDirectory(outputFile);
        Files.writeString(outputFile, toJson(report), StandardCharsets.UTF_8);
    }

    public void writeMarkdown(Path outputFile, BatchEvaluationReport report) throws IOException {
        ensureParentDirectory(outputFile);
        Files.writeString(outputFile, toMarkdown(report), StandardCharsets.UTF_8);
    }

    private String toJson(BatchEvaluationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, 1, "generated_at", report.generatedAt(), true);
        appendJsonField(sb, 1, "persona", report.personaId(), true);
        appendJsonField(sb, 1, "candidate_profile", report.candidateProfileId(), true);

        sb.append(indent(1)).append("\"summary\": {\n");
        appendJsonNumberField(sb, 2, "total_files", report.totalFiles(), true);
        appendJsonNumberField(sb, 2, "evaluated", report.evaluatedCount(), true);
        appendJsonNumberField(sb, 2, "failed", report.failedCount(), true);
        appendJsonNumberField(sb, 2, "go", report.goCount(), true);
        appendJsonNumberField(sb, 2, "go_with_caution", report.goWithCautionCount(), true);
        appendJsonNumberField(sb, 2, "no_go", report.noGoCount(), true);
        appendJsonNumberField(sb, 2, "new", report.newCount(), true);
        appendJsonNumberField(sb, 2, "updated", report.updatedCount(), true);
        appendJsonNumberField(sb, 2, "unchanged", report.unchangedCount(), true);
        appendJsonNumberField(sb, 2, "removed", report.removedCount(), false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"items\": [\n");
        for (int i = 0; i < report.items().size(); i++) {
            BatchEvaluationItem item = report.items().get(i);
            boolean isLast = i == report.items().size() - 1;
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, "source_file", item.sourceFile(), true);
            appendJsonField(sb, 3, "company", item.job().companyName(), true);
            appendJsonField(sb, 3, "title", item.job().title(), true);
            appendJsonField(sb, 3, "location", item.job().location(), true);
            appendJsonField(sb, 3, "salary_range", item.job().salaryRange(), true);
            appendJsonField(sb, 3, "remote_policy", item.job().remotePolicy(), true);
            appendJsonField(sb, 3, "verdict", item.evaluation().verdict().name(), true);
            appendJsonNumberField(sb, 3, "score", item.evaluation().score(), true);
            appendJsonNumberField(sb, 3, "raw_score", item.evaluation().rawScore(), true);
            appendJsonNumberField(sb, 3, "language_friction_index", item.evaluation().languageFrictionIndex(), true);
            appendJsonNumberField(sb, 3, "company_reputation_index", item.evaluation().companyReputationIndex(), true);
            appendJsonField(sb, 3, "job_key", item.jobKey(), true);
            appendJsonField(sb, 3, "change_status", item.changeStatus(), true);

            sb.append(indent(3)).append("\"raw_score_range\": {\n");
            appendJsonNumberField(sb, 4, "min", item.evaluation().rawScoreMin(), true);
            appendJsonNumberField(sb, 4, "max", item.evaluation().rawScoreMax(), false);
            sb.append(indent(3)).append("},\n");

            appendJsonArrayField(sb, 3, "hard_reject_reasons", item.evaluation().hardRejectReasons(), true);
            appendJsonArrayField(sb, 3, "positive_signals", item.evaluation().positiveSignals(), true);
            appendJsonArrayField(sb, 3, "risk_signals", item.evaluation().riskSignals(), true);
            appendJsonArrayField(sb, 3, "reasoning", item.evaluation().reasoning(), false);
            sb.append(indent(2)).append("}");
            if (!isLast) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("],\n");

        sb.append(indent(1)).append("\"removed_items\": [\n");
        for (int i = 0; i < report.removedItems().size(); i++) {
            RemovedJobItem item = report.removedItems().get(i);
            boolean isLast = i == report.removedItems().size() - 1;
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, "job_key", item.jobKey(), true);
            appendJsonField(sb, 3, "source_file", item.sourceFile(), true);
            appendJsonField(sb, 3, "company", item.company(), true);
            appendJsonField(sb, 3, "title", item.title(), true);
            appendJsonField(sb, 3, "location", item.location(), false);
            sb.append(indent(2)).append("}");
            if (!isLast) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("],\n");

        sb.append(indent(1)).append("\"errors\": [\n");
        for (int i = 0; i < report.errors().size(); i++) {
            BatchEvaluationError error = report.errors().get(i);
            boolean isLast = i == report.errors().size() - 1;
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, "source_file", error.sourceFile(), true);
            appendJsonArrayField(sb, 3, "errors", error.errors(), false);
            sb.append(indent(2)).append("}");
            if (!isLast) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String toMarkdown(BatchEvaluationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Batch Evaluation Report\n\n");
        sb.append("- generated_at: ").append(report.generatedAt()).append("\n");
        sb.append("- persona: ").append(report.personaId()).append("\n");
        sb.append("- candidate_profile: ").append(report.candidateProfileId()).append("\n\n");

        sb.append("## Summary\n\n");
        sb.append("- total_files: ").append(report.totalFiles()).append("\n");
        sb.append("- evaluated: ").append(report.evaluatedCount()).append("\n");
        sb.append("- failed: ").append(report.failedCount()).append("\n");
        sb.append("- go: ").append(report.goCount()).append("\n");
        sb.append("- go_with_caution: ").append(report.goWithCautionCount()).append("\n");
        sb.append("- no_go: ").append(report.noGoCount()).append("\n");
        sb.append("- new: ").append(report.newCount()).append("\n");
        sb.append("- updated: ").append(report.updatedCount()).append("\n");
        sb.append("- unchanged: ").append(report.unchangedCount()).append("\n");
        sb.append("- removed: ").append(report.removedCount()).append("\n\n");

        sb.append("## Results\n\n");
        if (report.items().isEmpty()) {
            sb.append("No successful evaluations.\n\n");
        } else {
            for (BatchEvaluationItem item : report.items()) {
                sb.append("### ")
                        .append(item.evaluation().verdict().name())
                        .append(" - ")
                        .append(item.job().companyName())
                        .append(" - ")
                        .append(item.job().title())
                        .append("\n\n");
                sb.append("- source_file: ").append(item.sourceFile()).append("\n");
                sb.append("- change_status: ").append(item.changeStatus()).append("\n");
                sb.append("- score: ").append(item.evaluation().score()).append("/100").append("\n");
                sb.append("- raw_score: ").append(item.evaluation().rawScore())
                        .append(" (range ")
                        .append(item.evaluation().rawScoreMin())
                        .append("..")
                        .append(item.evaluation().rawScoreMax())
                        .append(")\n");
                sb.append("- language_friction_index: ")
                        .append(item.evaluation().languageFrictionIndex())
                        .append("/100\n");
                sb.append("- company_reputation_index: ")
                        .append(item.evaluation().companyReputationIndex())
                        .append("/100\n");
                sb.append("- positive_signals: ").append(formatInlineList(item.evaluation().positiveSignals())).append("\n");
                sb.append("- risk_signals: ").append(formatInlineList(item.evaluation().riskSignals())).append("\n");
                sb.append("- hard_reject_reasons: ")
                        .append(formatInlineList(item.evaluation().hardRejectReasons()))
                        .append("\n\n");
            }
        }

        sb.append("## Removed Jobs\n\n");
        if (report.removedItems().isEmpty()) {
            sb.append("No removed jobs detected.\n\n");
        } else {
            for (RemovedJobItem item : report.removedItems()) {
                sb.append("- ")
                        .append(item.company())
                        .append(" - ")
                        .append(item.title())
                        .append(" [")
                        .append(item.location())
                        .append("]")
                        .append(" (")
                        .append(item.sourceFile())
                        .append(")\n");
            }
            sb.append("\n");
        }

        sb.append("## Errors\n\n");
        if (report.errors().isEmpty()) {
            sb.append("No parsing/evaluation errors.\n");
        } else {
            for (BatchEvaluationError error : report.errors()) {
                sb.append("### ").append(error.sourceFile()).append("\n\n");
                for (String entry : error.errors()) {
                    sb.append("- ").append(entry).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String defaultMarkdownFileName(String personaId) {
        return defaultMarkdownFileName(personaId, "");
    }

    public String defaultMarkdownFileName(String personaId, String candidateProfileId) {
        return "batch-evaluation-" + sanitizeScope(personaId, candidateProfileId) + ".md";
    }

    public String defaultJsonFileName(String personaId) {
        return defaultJsonFileName(personaId, "");
    }

    public String defaultJsonFileName(String personaId, String candidateProfileId) {
        return "batch-evaluation-" + sanitizeScope(personaId, candidateProfileId) + ".json";
    }

    private String sanitizeScope(String personaId, String candidateProfileId) {
        if (candidateProfileId == null
                || candidateProfileId.isBlank()
                || ConfigSelections.isCandidateProfileNone(candidateProfileId)) {
            return sanitizeFileName(personaId);
        }
        return sanitizeFileName(personaId + "--" + candidateProfileId);
    }

    private String sanitizeFileName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private String formatInlineList(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        return String.join(", ", values);
    }

    private void appendJsonField(StringBuilder sb, int level, String key, String value, boolean comma) {
        sb.append(indent(level))
                .append("\"").append(escapeJson(key)).append("\": ")
                .append("\"").append(escapeJson(value)).append("\"");
        if (comma) {
            sb.append(",");
        }
        sb.append("\n");
    }

    private void appendJsonNumberField(StringBuilder sb, int level, String key, int value, boolean comma) {
        sb.append(indent(level))
                .append("\"").append(escapeJson(key)).append("\": ")
                .append(value);
        if (comma) {
            sb.append(",");
        }
        sb.append("\n");
    }

    private void appendJsonArrayField(
            StringBuilder sb,
            int level,
            String key,
            List<String> values,
            boolean comma
    ) {
        sb.append(indent(level))
                .append("\"").append(escapeJson(key)).append("\": [");
        if (!values.isEmpty()) {
            sb.append("\n");
            for (int i = 0; i < values.size(); i++) {
                sb.append(indent(level + 1))
                        .append("\"")
                        .append(escapeJson(values.get(i)))
                        .append("\"");
                if (i < values.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent(level)).append("]");
        } else {
            sb.append("]");
        }
        if (comma) {
            sb.append(",");
        }
        sb.append("\n");
    }

    private void ensureParentDirectory(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String indent(int level) {
        return "  ".repeat(level);
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
