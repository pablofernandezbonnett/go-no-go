package com.pmfb.gonogo.engine.tui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public interface TuiPrompts extends AutoCloseable {
    static TuiPrompts openSystem() {
        try {
            return JlineTuiPrompts.openSystem();
        } catch (RuntimeException e) {
            System.err.println(
                    "JLine TUI unavailable, falling back to line-based prompts. "
                            + "For the full terminal UI, use the installed launcher from a real terminal: "
                            + "'./gradlew installDist' then './build/install/go-no-go-engine/bin/go-no-go-engine tui'."
            );
            return new LineBasedTuiPrompts(
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                    new PrintWriter(System.out, true, StandardCharsets.UTF_8)
            );
        }
    }

    void println(String value);

    void blankLine();

    String prompt(String label, String defaultValue);

    String promptOptional(String label, String defaultValue);

    boolean confirm(String label, boolean defaultValue);

    int promptInt(String label, int defaultValue, int minValue);

    long promptLong(String label, long defaultValue, long minValue);

    String selectOne(String label, java.util.List<OptionItem> options, String defaultId);

    java.util.List<String> selectMany(String label, java.util.List<OptionItem> options, boolean blankMeansAll);

    String promptMultiline(String label, String terminator);

    void pause(String label);

    @Override
    default void close() {
        // Default no-op.
    }

    record OptionItem(String id, String label) {
    }

    final class EndOfInputException extends RuntimeException {
    }
}
