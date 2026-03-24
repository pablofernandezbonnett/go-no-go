package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.tui.TuiApp;
import com.pmfb.gonogo.engine.tui.TuiCommandRunner;
import com.pmfb.gonogo.engine.tui.TuiConfigContext;
import com.pmfb.gonogo.engine.tui.TuiPrompts;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "tui",
        mixinStandardHelpOptions = true,
        description = "Interactive terminal launcher for common engine commands."
)
public final class TuiCommand implements Callable<Integer> {
    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Override
    public Integer call() {
        TuiConfigContext context;
        try {
            context = TuiConfigContext.load(configDir);
        } catch (ConfigLoadException e) {
            printErrors("Unable to start TUI", e.errors());
            return 1;
        }

        TuiCommandRunner runner = commandArgs -> new CommandLine(new GoNoGoCommand())
                .execute(commandArgs.toArray(String[]::new));
        try (TuiPrompts prompts = TuiPrompts.openSystem()) {
            return new TuiApp(context, prompts, runner).run();
        }
    }

    private void printErrors(String heading, List<String> errors) {
        System.err.println(heading + ":");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }
}
