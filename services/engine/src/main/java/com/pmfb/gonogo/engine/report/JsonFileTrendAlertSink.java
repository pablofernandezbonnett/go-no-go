package com.pmfb.gonogo.engine.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JsonFileTrendAlertSink implements TrendAlertSink {
    private static final int ZERO_ALERTS = 0;
    private final Path outputFile;

    public JsonFileTrendAlertSink(Path outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public String id() {
        return TrendAlertSinkFactory.SINK_JSON_FILE;
    }

    @Override
    public DispatchResult dispatch(List<TrendAlert> alerts, TrendAlertDispatchContext context) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, toJson(alerts, context), StandardCharsets.UTF_8);
        int delivered = alerts == null ? ZERO_ALERTS : alerts.size();
        return new DispatchResult(id(), delivered, "Wrote alerts JSON to " + outputFile + ".");
    }

    private String toJson(List<TrendAlert> alerts, TrendAlertDispatchContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, 1, "persona", context.personaId(), true);
        appendJsonField(sb, 1, "candidate_profile", context.candidateProfileId(), true);
        appendJsonField(sb, 1, "generated_at", context.generatedAt(), true);
        appendJsonField(sb, 1, "batch_json", context.batchJsonPath().toString(), true);
        appendJsonField(sb, 1, "weekly_digest", context.weeklyDigestPath().toString(), true);
        appendJsonNumberField(sb, 1, "alerts_count", alerts == null ? ZERO_ALERTS : alerts.size(), true);
        sb.append(indent(1)).append("\"alerts\": [");
        if (alerts != null && !alerts.isEmpty()) {
            sb.append("\n");
            for (int i = 0; i < alerts.size(); i++) {
                TrendAlert alert = alerts.get(i);
                sb.append(indent(2)).append("{\n");
                appendJsonField(sb, 3, "severity", alert.severity(), true);
                appendJsonField(sb, 3, "code", alert.code(), true);
                appendJsonField(sb, 3, "message", alert.message(), false);
                sb.append(indent(2)).append("}");
                if (i < alerts.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent(1)).append("]\n");
        } else {
            sb.append("]\n");
        }
        sb.append("}\n");
        return sb.toString();
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
