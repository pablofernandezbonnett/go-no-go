package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.job.JobInputFieldKeys;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EvaluateInputOutputFormatter {
    String toText(EvaluateInputAnalysis analysis, String analysisFile) {
        StringBuilder sb = new StringBuilder();
        EvaluationResult result = analysis.evaluation();
        JobInput job = analysis.jobInput();

        appendLine(sb, EvaluateInputFieldKeys.VERDICT, result.verdict().name());
        appendLine(sb, EvaluateInputFieldKeys.SCORE, result.score() + "/100");
        appendLine(
                sb,
                EvaluateInputFieldKeys.RAW_SCORE,
                result.rawScore() + " (range " + result.rawScoreMin() + ".." + result.rawScoreMax() + ")"
        );
        appendLine(sb, EvaluateInputFieldKeys.LANGUAGE_FRICTION_INDEX, result.languageFrictionIndex() + "/100");
        appendLine(sb, EvaluateInputFieldKeys.COMPANY_REPUTATION_INDEX, result.companyReputationIndex() + "/100");
        appendLine(sb, EvaluateInputFieldKeys.PERSONA, analysis.personaId());
        appendLine(sb, EvaluateInputFieldKeys.CANDIDATE_PROFILE, analysis.candidateProfileId());
        appendLine(sb, EvaluateInputFieldKeys.SOURCE_KIND, analysis.source().kind());
        if (!analysis.source().url().isBlank()) {
            appendLine(sb, EvaluateInputFieldKeys.SOURCE_URL, analysis.source().url());
        }
        if (!analysis.source().file().isBlank()) {
            appendLine(sb, EvaluateInputFieldKeys.SOURCE_FILE, analysis.source().file());
        }
        if (!analysis.source().rawText().isBlank()) {
            appendMultiline(sb, EvaluateInputFieldKeys.SOURCE_RAW_TEXT, analysis.source().rawText());
        }
        appendLine(sb, EvaluateInputFieldKeys.COMPANY, job.companyName());
        appendLine(sb, EvaluateInputFieldKeys.ROLE, job.title());
        appendList(sb, EvaluateInputFieldKeys.HARD_REJECT_REASONS, result.hardRejectReasons());
        appendList(sb, EvaluateInputFieldKeys.POSITIVE_SIGNALS, result.positiveSignals());
        appendList(sb, EvaluateInputFieldKeys.RISK_SIGNALS, result.riskSignals());
        appendList(sb, EvaluateInputFieldKeys.REASONING, result.reasoning());
        if (!analysis.normalizationWarnings().isEmpty()) {
            appendList(sb, EvaluateInputFieldKeys.NORMALIZATION_WARNINGS, analysis.normalizationWarnings());
        }
        if (!analysisFile.isBlank()) {
            appendLine(sb, EvaluateInputFieldKeys.ANALYSIS_FILE, analysisFile);
        }
        return sb.toString();
    }

    String toJson(EvaluateInputAnalysis analysis, String analysisFile) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put(EvaluateInputFieldKeys.GENERATED_AT, analysis.generatedAt());
        root.put(EvaluateInputFieldKeys.PERSONA, analysis.personaId());
        root.put(EvaluateInputFieldKeys.CANDIDATE_PROFILE, analysis.candidateProfileId());

        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put(EvaluateInputFieldKeys.KIND, analysis.source().kind());
        source.put(EvaluateInputFieldKeys.URL, analysis.source().url());
        source.put(EvaluateInputFieldKeys.FILE, analysis.source().file());
        source.put(EvaluateInputFieldKeys.RAW_TEXT, analysis.source().rawText());
        root.put(EvaluateInputFieldKeys.SOURCE, source);

        LinkedHashMap<String, Object> jobInput = new LinkedHashMap<>();
        jobInput.put(JobInputFieldKeys.COMPANY_NAME, analysis.jobInput().companyName());
        jobInput.put(JobInputFieldKeys.TITLE, analysis.jobInput().title());
        jobInput.put(JobInputFieldKeys.LOCATION, analysis.jobInput().location());
        jobInput.put(JobInputFieldKeys.SALARY_RANGE, analysis.jobInput().salaryRange());
        jobInput.put(JobInputFieldKeys.REMOTE_POLICY, analysis.jobInput().remotePolicy());
        jobInput.put(JobInputFieldKeys.DESCRIPTION, analysis.jobInput().description());
        root.put(EvaluateInputFieldKeys.JOB_INPUT, jobInput);

        LinkedHashMap<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put(EvaluateInputFieldKeys.VERDICT, analysis.evaluation().verdict().name());
        evaluation.put(EvaluateInputFieldKeys.SCORE, analysis.evaluation().score());
        evaluation.put(EvaluateInputFieldKeys.RAW_SCORE, analysis.evaluation().rawScore());
        evaluation.put(EvaluateInputFieldKeys.RAW_SCORE_MIN, analysis.evaluation().rawScoreMin());
        evaluation.put(EvaluateInputFieldKeys.RAW_SCORE_MAX, analysis.evaluation().rawScoreMax());
        evaluation.put(EvaluateInputFieldKeys.LANGUAGE_FRICTION_INDEX, analysis.evaluation().languageFrictionIndex());
        evaluation.put(EvaluateInputFieldKeys.COMPANY_REPUTATION_INDEX, analysis.evaluation().companyReputationIndex());
        evaluation.put(EvaluateInputFieldKeys.HARD_REJECT_REASONS, analysis.evaluation().hardRejectReasons());
        evaluation.put(EvaluateInputFieldKeys.POSITIVE_SIGNALS, analysis.evaluation().positiveSignals());
        evaluation.put(EvaluateInputFieldKeys.RISK_SIGNALS, analysis.evaluation().riskSignals());
        evaluation.put(EvaluateInputFieldKeys.REASONING, analysis.evaluation().reasoning());
        root.put(EvaluateInputFieldKeys.EVALUATION, evaluation);

        root.put(EvaluateInputFieldKeys.NORMALIZATION_WARNINGS, analysis.normalizationWarnings());
        root.put(EvaluateInputFieldKeys.ANALYSIS_FILE, analysisFile);
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
