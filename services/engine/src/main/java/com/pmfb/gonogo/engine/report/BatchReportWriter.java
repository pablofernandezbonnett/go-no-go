package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.config.ConfigSelections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BatchReportWriter {
    private static final String FIELD_GENERATED_AT = "generated_at";
    private static final String FIELD_PERSONA = "persona";
    private static final String FIELD_CANDIDATE_PROFILE = "candidate_profile";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_TOTAL_FILES = "total_files";
    private static final String FIELD_EVALUATED = "evaluated";
    private static final String FIELD_FAILED = "failed";
    private static final String FIELD_GO = "go";
    private static final String FIELD_GO_WITH_CAUTION = "go_with_caution";
    private static final String FIELD_NO_GO = "no_go";
    private static final String FIELD_NEW = "new";
    private static final String FIELD_UPDATED = "updated";
    private static final String FIELD_UNCHANGED = "unchanged";
    private static final String FIELD_REMOVED = "removed";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_REMOVED_ITEMS = "removed_items";
    private static final String FIELD_ERRORS = "errors";
    private static final String FIELD_SOURCE_FILE = "source_file";
    private static final String FIELD_COMPANY = "company";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_SALARY_RANGE = "salary_range";
    private static final String FIELD_REMOTE_POLICY = "remote_policy";
    private static final String FIELD_VERDICT = "verdict";
    private static final String FIELD_SCORE = "score";
    private static final String FIELD_RAW_SCORE = "raw_score";
    private static final String FIELD_LANGUAGE_FRICTION_INDEX = "language_friction_index";
    private static final String FIELD_COMPANY_REPUTATION_INDEX = "company_reputation_index";
    private static final String FIELD_JOB_KEY = "job_key";
    private static final String FIELD_CHANGE_STATUS = "change_status";
    private static final String FIELD_RAW_SCORE_RANGE = "raw_score_range";
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";
    private static final String FIELD_HARD_REJECT_REASONS = "hard_reject_reasons";
    private static final String FIELD_POSITIVE_SIGNALS = "positive_signals";
    private static final String FIELD_RISK_SIGNALS = "risk_signals";
    private static final String FIELD_REASONING = "reasoning";
    private static final String FIELD_HUMAN_READING = "human_reading";
    private static final String FIELD_HUMAN_SUMMARY = "human_summary";
    private static final String FIELD_ACCESS_FIT = "access_fit";
    private static final String FIELD_EXECUTION_FIT = "execution_fit";
    private static final String FIELD_DOMAIN_FIT = "domain_fit";
    private static final String FIELD_OPPORTUNITY_QUALITY = "opportunity_quality";
    private static final String FIELD_INTERVIEW_ROI = "interview_roi";
    private static final String FIELD_WHY_STILL_INTERESTING = "why_still_interesting";
    private static final String FIELD_WHY_WASTE_OF_TIME = "why_waste_of_time";

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
        appendJsonField(sb, 1, FIELD_GENERATED_AT, report.generatedAt(), true);
        appendJsonField(sb, 1, FIELD_PERSONA, report.personaId(), true);
        appendJsonField(sb, 1, FIELD_CANDIDATE_PROFILE, report.candidateProfileId(), true);

        sb.append(indent(1)).append('"').append(FIELD_SUMMARY).append("\": {\n");
        appendJsonNumberField(sb, 2, FIELD_TOTAL_FILES, report.totalFiles(), true);
        appendJsonNumberField(sb, 2, FIELD_EVALUATED, report.evaluatedCount(), true);
        appendJsonNumberField(sb, 2, FIELD_FAILED, report.failedCount(), true);
        appendJsonNumberField(sb, 2, FIELD_GO, report.goCount(), true);
        appendJsonNumberField(sb, 2, FIELD_GO_WITH_CAUTION, report.goWithCautionCount(), true);
        appendJsonNumberField(sb, 2, FIELD_NO_GO, report.noGoCount(), true);
        appendJsonNumberField(sb, 2, FIELD_NEW, report.newCount(), true);
        appendJsonNumberField(sb, 2, FIELD_UPDATED, report.updatedCount(), true);
        appendJsonNumberField(sb, 2, FIELD_UNCHANGED, report.unchangedCount(), true);
        appendJsonNumberField(sb, 2, FIELD_REMOVED, report.removedCount(), false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append('"').append(FIELD_ITEMS).append("\": [\n");
        for (int i = 0; i < report.items().size(); i++) {
            BatchEvaluationItem item = report.items().get(i);
            boolean isLast = i == report.items().size() - 1;
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, FIELD_SOURCE_FILE, item.sourceFile(), true);
            appendJsonField(sb, 3, FIELD_COMPANY, item.job().companyName(), true);
            appendJsonField(sb, 3, FIELD_TITLE, item.job().title(), true);
            appendJsonField(sb, 3, FIELD_LOCATION, item.job().location(), true);
            appendJsonField(sb, 3, FIELD_SALARY_RANGE, item.job().salaryRange(), true);
            appendJsonField(sb, 3, FIELD_REMOTE_POLICY, item.job().remotePolicy(), true);
            appendJsonField(sb, 3, FIELD_VERDICT, item.evaluation().verdict().name(), true);
            appendJsonNumberField(sb, 3, FIELD_SCORE, item.evaluation().score(), true);
            appendJsonNumberField(sb, 3, FIELD_RAW_SCORE, item.evaluation().rawScore(), true);
            appendJsonNumberField(sb, 3, FIELD_LANGUAGE_FRICTION_INDEX, item.evaluation().languageFrictionIndex(), true);
            appendJsonNumberField(sb, 3, FIELD_COMPANY_REPUTATION_INDEX, item.evaluation().companyReputationIndex(), true);
            appendJsonField(sb, 3, FIELD_JOB_KEY, item.jobKey(), true);
            appendJsonField(sb, 3, FIELD_CHANGE_STATUS, item.changeStatus(), true);

            sb.append(indent(3)).append('"').append(FIELD_RAW_SCORE_RANGE).append("\": {\n");
            appendJsonNumberField(sb, 4, FIELD_MIN, item.evaluation().rawScoreMin(), true);
            appendJsonNumberField(sb, 4, FIELD_MAX, item.evaluation().rawScoreMax(), false);
            sb.append(indent(3)).append("},\n");

            appendJsonArrayField(sb, 3, FIELD_HARD_REJECT_REASONS, item.evaluation().hardRejectReasons(), true);
            appendJsonArrayField(sb, 3, FIELD_POSITIVE_SIGNALS, item.evaluation().positiveSignals(), true);
            appendJsonArrayField(sb, 3, FIELD_RISK_SIGNALS, item.evaluation().riskSignals(), true);
            appendJsonArrayField(sb, 3, FIELD_REASONING, item.evaluation().reasoning(), true);
            appendJsonHumanReadingField(sb, 3, item.evaluation().humanReading(), false);
            sb.append(indent(2)).append("}");
            if (!isLast) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("],\n");

        sb.append(indent(1)).append('"').append(FIELD_REMOVED_ITEMS).append("\": [\n");
        for (int i = 0; i < report.removedItems().size(); i++) {
            RemovedJobItem item = report.removedItems().get(i);
            boolean isLast = i == report.removedItems().size() - 1;
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, FIELD_JOB_KEY, item.jobKey(), true);
            appendJsonField(sb, 3, FIELD_SOURCE_FILE, item.sourceFile(), true);
            appendJsonField(sb, 3, FIELD_COMPANY, item.company(), true);
            appendJsonField(sb, 3, FIELD_TITLE, item.title(), true);
            appendJsonField(sb, 3, FIELD_LOCATION, item.location(), false);
            sb.append(indent(2)).append("}");
            if (!isLast) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("],\n");

        sb.append(indent(1)).append('"').append(FIELD_ERRORS).append("\": [\n");
        for (int i = 0; i < report.errors().size(); i++) {
            BatchEvaluationError error = report.errors().get(i);
            boolean isLast = i == report.errors().size() - 1;
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, FIELD_SOURCE_FILE, error.sourceFile(), true);
            appendJsonArrayField(sb, 3, FIELD_ERRORS, error.errors(), false);
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
                sb.append("- human_summary: ").append(item.evaluation().humanReading().summary()).append("\n");
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

    private void appendJsonHumanReadingField(
            StringBuilder sb,
            int level,
            com.pmfb.gonogo.engine.decision.HumanReading humanReading,
            boolean comma
    ) {
        sb.append(indent(level))
                .append("\"").append(escapeJson(FIELD_HUMAN_READING)).append("\": {\n");
        appendJsonField(sb, level + 1, FIELD_ACCESS_FIT, humanReading.accessFit().serialized(), true);
        appendJsonField(sb, level + 1, FIELD_EXECUTION_FIT, humanReading.executionFit().serialized(), true);
        appendJsonField(sb, level + 1, FIELD_DOMAIN_FIT, humanReading.domainFit().serialized(), true);
        appendJsonField(sb, level + 1, FIELD_OPPORTUNITY_QUALITY, humanReading.opportunityQuality().serialized(), true);
        appendJsonField(sb, level + 1, FIELD_INTERVIEW_ROI, humanReading.interviewRoi().serialized(), true);
        appendJsonField(sb, level + 1, FIELD_HUMAN_SUMMARY, humanReading.summary(), true);
        appendJsonArrayField(sb, level + 1, FIELD_WHY_STILL_INTERESTING, humanReading.whyStillInteresting(), true);
        appendJsonArrayField(sb, level + 1, FIELD_WHY_WASTE_OF_TIME, humanReading.whyWasteOfTime(), false);
        sb.append(indent(level)).append("}");
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
