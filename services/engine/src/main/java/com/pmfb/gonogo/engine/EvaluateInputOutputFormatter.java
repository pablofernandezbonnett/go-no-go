package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.job.JobInput;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EvaluateInputOutputFormatter {
    String toText(EvaluateInputAnalysis analysis, String analysisFile) {
        StringBuilder sb = new StringBuilder();
        EvaluationResult result = analysis.evaluation();
        JobInput job = analysis.jobInput();

        appendLine(sb, "verdict", result.verdict().name());
        appendLine(sb, "score", result.score() + "/100");
        appendLine(
                sb,
                "raw_score",
                result.rawScore() + " (range " + result.rawScoreMin() + ".." + result.rawScoreMax() + ")"
        );
        appendLine(sb, "language_friction_index", result.languageFrictionIndex() + "/100");
        appendLine(sb, "company_reputation_index", result.companyReputationIndex() + "/100");
        appendLine(sb, "persona", analysis.personaId());
        appendLine(sb, "candidate_profile", analysis.candidateProfileId());
        appendLine(sb, "source_kind", analysis.source().kind());
        if (!analysis.source().url().isBlank()) {
            appendLine(sb, "source_url", analysis.source().url());
        }
        if (!analysis.source().file().isBlank()) {
            appendLine(sb, "source_file", analysis.source().file());
        }
        if (!analysis.source().rawText().isBlank()) {
            appendMultiline(sb, "source_raw_text", analysis.source().rawText());
        }
        appendLine(sb, "company", job.companyName());
        appendLine(sb, "role", job.title());
        appendList(sb, "hard_reject_reasons", result.hardRejectReasons());
        appendList(sb, "positive_signals", result.positiveSignals());
        appendList(sb, "risk_signals", result.riskSignals());
        appendList(sb, "reasoning", result.reasoning());
        if (!analysis.normalizationWarnings().isEmpty()) {
            appendList(sb, "normalization_warnings", analysis.normalizationWarnings());
        }
        if (!analysisFile.isBlank()) {
            appendLine(sb, "analysis_file", analysisFile);
        }
        return sb.toString();
    }

    String toJson(EvaluateInputAnalysis analysis, String analysisFile) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("generated_at", analysis.generatedAt());
        root.put("persona", analysis.personaId());
        root.put("candidate_profile", analysis.candidateProfileId());

        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("kind", analysis.source().kind());
        source.put("url", analysis.source().url());
        source.put("file", analysis.source().file());
        source.put("raw_text", analysis.source().rawText());
        root.put("source", source);

        LinkedHashMap<String, Object> jobInput = new LinkedHashMap<>();
        jobInput.put("company_name", analysis.jobInput().companyName());
        jobInput.put("title", analysis.jobInput().title());
        jobInput.put("location", analysis.jobInput().location());
        jobInput.put("salary_range", analysis.jobInput().salaryRange());
        jobInput.put("remote_policy", analysis.jobInput().remotePolicy());
        jobInput.put("description", analysis.jobInput().description());
        root.put("job_input", jobInput);

        LinkedHashMap<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("verdict", analysis.evaluation().verdict().name());
        evaluation.put("score", analysis.evaluation().score());
        evaluation.put("raw_score", analysis.evaluation().rawScore());
        evaluation.put("raw_score_min", analysis.evaluation().rawScoreMin());
        evaluation.put("raw_score_max", analysis.evaluation().rawScoreMax());
        evaluation.put("language_friction_index", analysis.evaluation().languageFrictionIndex());
        evaluation.put("company_reputation_index", analysis.evaluation().companyReputationIndex());
        evaluation.put("hard_reject_reasons", analysis.evaluation().hardRejectReasons());
        evaluation.put("positive_signals", analysis.evaluation().positiveSignals());
        evaluation.put("risk_signals", analysis.evaluation().riskSignals());
        evaluation.put("reasoning", analysis.evaluation().reasoning());
        root.put("evaluation", evaluation);

        root.put("normalization_warnings", analysis.normalizationWarnings());
        root.put("analysis_file", analysisFile);
        return toJsonObject(root);
    }

    private void appendLine(StringBuilder sb, String key, String value) {
        sb.append(key).append(": ").append(value).append("\n");
    }

    private void appendList(StringBuilder sb, String key, List<String> values) {
        if (values.isEmpty()) {
            sb.append(key).append(": []\n");
            return;
        }
        sb.append(key).append(":\n");
        for (String value : values) {
            sb.append(" - ").append(value).append("\n");
        }
    }

    private void appendMultiline(StringBuilder sb, String key, String value) {
        sb.append(key).append(":\n");
        for (String line : value.split("\\R")) {
            sb.append(" | ").append(line).append("\n");
        }
    }

    private String toJsonObject(Map<String, Object> object) {
        return appendJsonValue(new StringBuilder(), object).toString();
    }

    @SuppressWarnings("unchecked")
    private StringBuilder appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            return sb.append("null");
        }
        if (value instanceof String text) {
            return sb.append('"').append(escapeJson(text)).append('"');
        }
        if (value instanceof Number || value instanceof Boolean) {
            return sb.append(value);
        }
        if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendJsonValue(sb, list.get(i));
            }
            return sb.append(']');
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            int index = 0;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                if (index > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
                appendJsonValue(sb, entry.getValue());
                index++;
            }
            return sb.append('}');
        }
        return sb.append('"').append(escapeJson(value.toString())).append('"');
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
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
