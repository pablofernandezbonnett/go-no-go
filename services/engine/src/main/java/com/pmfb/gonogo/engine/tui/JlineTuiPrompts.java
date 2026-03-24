package com.pmfb.gonogo.engine.tui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jline.consoleui.elements.ConfirmChoice;
import org.jline.consoleui.prompt.CheckboxResult;
import org.jline.consoleui.prompt.ConfirmResult;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.ListResult;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.CheckboxPromptBuilder;
import org.jline.consoleui.prompt.builder.ConfirmPromptBuilder;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public final class JlineTuiPrompts implements TuiPrompts {
    private static final int MAX_PAGE_SIZE = 10;
    private static final String RESULT_KEY = "result";

    private final Terminal terminal;
    private final PrintWriter out;
    private final LineReader lineReader;
    private final ConsolePrompt consolePrompt;

    private JlineTuiPrompts(Terminal terminal, LineReader lineReader, ConsolePrompt consolePrompt) {
        this.terminal = terminal;
        this.out = terminal.writer();
        this.lineReader = lineReader;
        this.consolePrompt = consolePrompt;
    }

    public static JlineTuiPrompts openSystem() {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .nativeSignals(true)
                    .build();
            if (isUnsupportedTerminal(terminal)) {
                try {
                    terminal.close();
                } catch (IOException ignored) {
                    // Best-effort close before falling back.
                }
                throw new IllegalStateException("JLine requires a real terminal.");
            }
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
            ConsolePrompt consolePrompt = new ConsolePrompt(
                    lineReader,
                    terminal,
                    new ConsolePrompt.UiConfig()
            );
            return new JlineTuiPrompts(terminal, lineReader, consolePrompt);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create JLine terminal prompts.", e);
        }
    }

    @Override
    public void println(String value) {
        out.println(value);
        out.flush();
    }

    @Override
    public void blankLine() {
        out.println();
        out.flush();
    }

    @Override
    public String prompt(String label, String defaultValue) {
        while (true) {
            String input = readLine(label + renderDefault(defaultValue) + ": ");
            if (input.isBlank()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                println("Value cannot be blank.");
                continue;
            }
            return input;
        }
    }

    @Override
    public String promptOptional(String label, String defaultValue) {
        String input = readLine(label + renderDefault(defaultValue) + ": ");
        if (input.isBlank()) {
            return defaultValue == null ? "" : defaultValue;
        }
        return input;
    }

    @Override
    public boolean confirm(String label, boolean defaultValue) {
        PromptBuilder builder = consolePrompt.getPromptBuilder();
        ConfirmPromptBuilder prompt = builder.createConfirmPromp()
                .name(RESULT_KEY)
                .message(label)
                .defaultValue(defaultValue ? ConfirmChoice.ConfirmationValue.YES : ConfirmChoice.ConfirmationValue.NO);
        prompt.addPrompt();
        ConfirmResult result = (ConfirmResult) runPrompt(builder).get(RESULT_KEY);
        return result.getConfirmed() == ConfirmChoice.ConfirmationValue.YES;
    }

    @Override
    public int promptInt(String label, int defaultValue, int minValue) {
        while (true) {
            String input = promptOptional(label, String.valueOf(defaultValue));
            try {
                int value = Integer.parseInt(input);
                if (value < minValue) {
                    println("Value must be at least " + minValue + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                println("Enter a valid integer.");
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
                    println("Value must be at least " + minValue + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                println("Enter a valid integer.");
            }
        }
    }

    @Override
    public String selectOne(String label, List<OptionItem> options, String defaultId) {
        List<OptionItem> ordered = reorderToDefaultFirst(options, defaultId);
        PromptBuilder builder = consolePrompt.getPromptBuilder();
        ListPromptBuilder prompt = builder.createListPrompt()
                .name(RESULT_KEY)
                .message(label)
                .pageSize(computePageSize(ordered.size()));
        for (OptionItem option : ordered) {
            prompt.newItem(option.id())
                    .text(option.label())
                    .add();
        }
        prompt.addPrompt();
        ListResult result = (ListResult) runPrompt(builder).get(RESULT_KEY);
        return result.getSelectedId();
    }

    @Override
    public List<String> selectMany(String label, List<OptionItem> options, boolean blankMeansAll) {
        PromptBuilder builder = consolePrompt.getPromptBuilder();
        CheckboxPromptBuilder prompt = builder.createCheckboxPrompt()
                .name(RESULT_KEY)
                .message(label)
                .pageSize(computePageSize(options.size()));
        for (OptionItem option : options) {
            var item = prompt.newItem(option.id())
                    .text(option.label());
            if (blankMeansAll) {
                item.check();
            }
            item.add();
        }
        prompt.addPrompt();
        CheckboxResult result = (CheckboxResult) runPrompt(builder).get(RESULT_KEY);
        Set<String> selectedIds = result.getSelectedIds();
        if (selectedIds == null || selectedIds.isEmpty()) {
            return blankMeansAll ? options.stream().map(OptionItem::id).toList() : List.of();
        }
        return preserveOptionOrder(options, selectedIds);
    }

    @Override
    public String promptMultiline(String label, String terminator) {
        println(label);
        println("Finish with a line that contains only " + terminator + ".");
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = readLine("> ", true);
            if (terminator.equals(line.trim())) {
                break;
            }
            lines.add(line);
        }
        return String.join(System.lineSeparator(), lines).trim();
    }

    @Override
    public void pause(String label) {
        readLine(label, true);
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
            // Best-effort close.
        }
    }

    private Map<String, PromptResultItemIF> runPrompt(PromptBuilder builder) {
        try {
            return consolePrompt.prompt(builder.build());
        } catch (UserInterruptException | EndOfFileException e) {
            throw new EndOfInputException();
        } catch (IOException e) {
            throw new IllegalStateException("JLine prompt failed.", e);
        }
    }

    private int computePageSize(int optionCount) {
        int terminalHeight = terminal.getHeight();
        int cappedByTerminal = terminalHeight > 0 ? Math.max(3, terminalHeight - 6) : MAX_PAGE_SIZE;
        return Math.max(1, Math.min(optionCount, Math.min(MAX_PAGE_SIZE, cappedByTerminal)));
    }

    private static boolean isUnsupportedTerminal(Terminal terminal) {
        String type = terminal.getType();
        return Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type);
    }

    private List<OptionItem> reorderToDefaultFirst(List<OptionItem> options, String defaultId) {
        return options.stream()
                .sorted(Comparator.comparingInt(option -> option.id().equals(defaultId) ? 0 : 1))
                .toList();
    }

    private List<String> preserveOptionOrder(List<OptionItem> options, Set<String> selectedIds) {
        Set<String> normalized = new LinkedHashSet<>(selectedIds);
        List<String> ordered = new ArrayList<>();
        for (OptionItem option : options) {
            if (normalized.contains(option.id())) {
                ordered.add(option.id());
            }
        }
        return List.copyOf(ordered);
    }

    private String readLine(String prompt) {
        return readLine(prompt, false);
    }

    private String readLine(String prompt, boolean preserveWhitespace) {
        try {
            String line = lineReader.readLine(prompt);
            return preserveWhitespace ? line : line.trim();
        } catch (UserInterruptException | EndOfFileException e) {
            throw new EndOfInputException();
        }
    }

    private String renderDefault(String defaultValue) {
        return defaultValue == null || defaultValue.isBlank() ? "" : " [" + defaultValue + "]";
    }
}
