package com.pmfb.gonogo.engine.tui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LineBasedTuiPrompts implements TuiPrompts {
    private final BufferedReader reader;
    private final PrintWriter out;

    public LineBasedTuiPrompts(BufferedReader reader, PrintWriter out) {
        this.reader = reader;
        this.out = out;
    }

    @Override
    public void println(String value) {
        out.println(value);
    }

    @Override
    public void blankLine() {
        out.println();
    }

    @Override
    public String prompt(String label, String defaultValue) {
        while (true) {
            String input = readPromptLine(label + renderDefault(defaultValue) + ": ").trim();
            if (input.isBlank()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                out.println("Value cannot be blank.");
                continue;
            }
            return input;
        }
    }

    @Override
    public String promptOptional(String label, String defaultValue) {
        String input = readPromptLine(label + renderDefault(defaultValue) + ": ").trim();
        if (input.isBlank()) {
            return defaultValue == null ? "" : defaultValue;
        }
        return input;
    }

    @Override
    public boolean confirm(String label, boolean defaultValue) {
        String defaultLabel = defaultValue ? "Y/n" : "y/N";
        while (true) {
            out.print(label + " [" + defaultLabel + ", Enter confirms]: ");
            out.flush();
            String input = readPromptLine("").trim().toLowerCase(Locale.ROOT);
            if (input.isBlank()) {
                return defaultValue;
            }
            if ("y".equals(input) || "yes".equals(input)) {
                return true;
            }
            if ("n".equals(input) || "no".equals(input)) {
                return false;
            }
            out.println("Enter y or n.");
        }
    }

    @Override
    public int promptInt(String label, int defaultValue, int minValue) {
        while (true) {
            String input = promptOptional(label, String.valueOf(defaultValue));
            try {
                int value = Integer.parseInt(input);
                if (value < minValue) {
                    out.println("Value must be at least " + minValue + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                out.println("Enter a valid integer.");
            }
        }
    }

    @Override
    public long promptLong(String label, long defaultValue, long minValue) {
        while (true) {
            String input = promptOptional(label, String.valueOf(defaultValue));
            try {
                long value = Long.parseLong(input);
                if (value < minValue) {
                    out.println("Value must be at least " + minValue + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                out.println("Enter a valid integer.");
            }
        }
    }

    @Override
    public String selectOne(String label, List<OptionItem> options, String defaultId) {
        printOptions(label, options);
        out.println(" Type the number or id, then press Enter.");
        while (true) {
            out.print(label + renderDefault(defaultId) + " (number/id + Enter): ");
            out.flush();
            String input = readPromptLine("").trim();
            if (input.isBlank()) {
                return defaultId;
            }
            OptionItem resolved = resolveOption(input, options);
            if (resolved != null) {
                return resolved.id();
            }
            out.println("Enter a listed number or id.");
        }
    }

    @Override
    public List<String> selectMany(String label, List<OptionItem> options, boolean blankMeansAll) {
        printOptions(label, options);
        out.println(" Type listed numbers or ids separated by commas, then press Enter.");
        while (true) {
            out.print(
                    label
                            + (blankMeansAll ? " [all]" : "")
                            + " (comma-separated numbers or ids, then Enter): "
            );
            out.flush();
            String input = readPromptLine("").trim();
            if (input.isBlank()) {
                return blankMeansAll
                        ? options.stream().map(OptionItem::id).toList()
                        : List.of();
            }
            if ("all".equalsIgnoreCase(input)) {
                return options.stream().map(OptionItem::id).toList();
            }
            String[] parts = input.split(",");
            Set<String> ids = new LinkedHashSet<>();
            boolean valid = true;
            for (String rawPart : parts) {
                OptionItem resolved = resolveOption(rawPart.trim(), options);
                if (resolved == null) {
                    valid = false;
                    break;
                }
                ids.add(resolved.id());
            }
            if (valid) {
                return List.copyOf(ids);
            }
            out.println("Enter listed numbers or ids, separated by commas.");
        }
    }

    @Override
    public String promptMultiline(String label, String terminator) {
        out.println(label);
        out.println("Finish with a line that contains only " + terminator + ".");
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = readPromptLinePreservingWhitespace("");
            if (terminator.equals(line.trim())) {
                break;
            }
            lines.add(line);
        }
        return String.join(System.lineSeparator(), lines).trim();
    }

    @Override
    public void pause(String label) {
        out.print(label);
        out.flush();
        readPromptLinePreservingWhitespace("");
    }

    private void printOptions(String label, List<OptionItem> options) {
        out.println(label + ":");
        for (int i = 0; i < options.size(); i++) {
            OptionItem option = options.get(i);
            out.println(" " + (i + 1) + ". " + option.id() + " - " + option.label());
        }
    }

    private OptionItem resolveOption(String rawInput, List<OptionItem> options) {
        if (rawInput.isBlank()) {
            return null;
        }
        try {
            int index = Integer.parseInt(rawInput);
            if (index >= 1 && index <= options.size()) {
                return options.get(index - 1);
            }
        } catch (NumberFormatException ignored) {
            // Fall back to id matching.
        }

        for (OptionItem option : options) {
            if (option.id().equalsIgnoreCase(rawInput)) {
                return option;
            }
        }
        OptionItem prefixMatch = null;
        String normalized = rawInput.toLowerCase(Locale.ROOT);
        for (OptionItem option : options) {
            if (!option.id().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                continue;
            }
            if (prefixMatch != null) {
                return null;
            }
            prefixMatch = option;
        }
        return prefixMatch;
    }

    private String renderDefault(String defaultValue) {
        return defaultValue == null || defaultValue.isBlank() ? "" : " [" + defaultValue + "]";
    }

    private String readPromptLine(String prompt) {
        return readLine(prompt, false);
    }

    private String readPromptLinePreservingWhitespace(String prompt) {
        return readLine(prompt, true);
    }

    private String readLine(String prompt, boolean preserveWhitespace) {
        try {
            out.print(prompt);
            out.flush();
            String line = reader.readLine();
            if (line == null) {
                throw new EndOfInputException();
            }
            return preserveWhitespace ? line : line.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
