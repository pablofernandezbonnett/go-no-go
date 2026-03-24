package com.pmfb.gonogo.engine.tui;

import java.util.List;

public record TuiExecutionPlan(
        String title,
        List<String> commandArgs,
        List<String> displayArgs
) {
    private static final String ROOT_COMMAND = "gonogo";
    private static final String GRADLE_PREFIX = "./gradlew run --args=";
    private static final String SINGLE_QUOTE = "'";
    private static final String SINGLE_QUOTE_ESCAPE = "'\"'\"'";

    public TuiExecutionPlan {
        commandArgs = List.copyOf(commandArgs);
        displayArgs = displayArgs == null ? List.copyOf(commandArgs) : List.copyOf(displayArgs);
    }

    public String logicalCommand() {
        return ROOT_COMMAND + " " + joinTokens(displayArgs);
    }

    public String gradleCommand() {
        return GRADLE_PREFIX + shellQuote(String.join(" ", displayArgs));
    }

    private String joinTokens(List<String> tokens) {
        return tokens.stream()
                .map(this::quoteTokenIfNeeded)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String quoteTokenIfNeeded(String value) {
        if (value == null || value.isBlank()) {
            return shellQuote("");
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isWhitespace(current) || current == '\'' || current == '"') {
                return shellQuote(value);
            }
        }
        return value;
    }

    private String shellQuote(String value) {
        return SINGLE_QUOTE + value.replace(SINGLE_QUOTE, SINGLE_QUOTE_ESCAPE) + SINGLE_QUOTE;
    }
}
