package com.pmfb.gonogo.engine.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrendAlertSinkFactory {
    public static final String SINK_NONE = "none";
    public static final String SINK_STDOUT = "stdout";
    public static final String SINK_JSON_FILE = "json-file";
    public static final String SUPPORTED_SINKS = SINK_NONE + ", " + SINK_STDOUT + ", " + SINK_JSON_FILE;

    private TrendAlertSinkFactory() {
    }

    public static List<TrendAlertSink> create(List<String> sinkIds, Path jsonFilePath) {
        if (sinkIds == null || sinkIds.isEmpty()) {
            return List.of(new NoopTrendAlertSink());
        }

        List<TrendAlertSink> sinks = new ArrayList<>();
        for (String rawId : sinkIds) {
            String id = normalize(rawId);
            switch (id) {
                case "", SINK_NONE -> sinks.add(new NoopTrendAlertSink());
                case SINK_STDOUT -> sinks.add(new StdoutTrendAlertSink());
                case SINK_JSON_FILE -> sinks.add(new JsonFileTrendAlertSink(jsonFilePath));
                default -> throw new IllegalArgumentException(
                        "Unknown alert sink '" + rawId + "'. Supported: " + SUPPORTED_SINKS
                );
            }
        }
        return List.copyOf(sinks);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
