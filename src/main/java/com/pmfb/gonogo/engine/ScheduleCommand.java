package com.pmfb.gonogo.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "schedule",
        mixinStandardHelpOptions = true,
        description = "Generate scheduled-run artifacts (script + cron entry) without activating them."
)
public final class ScheduleCommand implements Callable<Integer> {
    private static final String DEFAULT_CRON = "15 8 * * 1-5";
    private static final String DEFAULT_SCRIPT_FILE = "scripts/run-pipeline-scheduled.sh";
    private static final String DEFAULT_CRON_FILE = "scripts/cron-pipeline.example";
    private static final String DEFAULT_LOG_FILE = "output/scheduled-pipeline.log";
    private static final String DEFAULT_GRADLE_COMMAND = "./gradlew";
    private static final String DEFAULT_WORKING_DIR = ".";
    private static final String DEFAULT_RUN_COMMAND =
            "pipeline run --company-ids mercari,moneyforward";
    private static final int EXPECTED_CRON_PARTS = 5;
    private static final String CRON_MACRO_PREFIX = "@";
    private static final String CRON_INSTALL_HINT = "Install manually when ready: crontab ";
    private static final String SINGLE_QUOTE = "'";
    private static final String SINGLE_QUOTE_ESCAPE = "'\"'\"'";

    @Option(
            names = {"--cron"},
            description = "Cron expression (default: weekdays at 08:15 local time).",
            defaultValue = DEFAULT_CRON
    )
    private String cron;

    @Option(
            names = {"--run-command"},
            description = "Command passed to Gradle --args (default: full pipeline run).",
            defaultValue = DEFAULT_RUN_COMMAND
    )
    private String runCommand;

    @Option(
            names = {"--script-file"},
            description = "Output path for generated scheduler shell script.",
            defaultValue = DEFAULT_SCRIPT_FILE
    )
    private Path scriptFile;

    @Option(
            names = {"--cron-file"},
            description = "Output path for generated cron example entry.",
            defaultValue = DEFAULT_CRON_FILE
    )
    private Path cronFile;

    @Option(
            names = {"--log-file"},
            description = "Log file used by the generated scheduler script.",
            defaultValue = DEFAULT_LOG_FILE
    )
    private Path logFile;

    @Option(
            names = {"--gradle-command"},
            description = "Gradle command used by the generated scheduler script.",
            defaultValue = DEFAULT_GRADLE_COMMAND
    )
    private String gradleCommand;

    @Option(
            names = {"--working-dir"},
            description = "Project root directory used by the generated scheduler script.",
            defaultValue = DEFAULT_WORKING_DIR
    )
    private Path workingDir;

    @Override
    public Integer call() {
        if (runCommand == null || runCommand.isBlank()) {
            System.err.println("--run-command cannot be blank.");
            return 1;
        }
        if (!isCronValid(cron)) {
            System.err.println("Invalid cron expression '" + cron + "'.");
            System.err.println("Use 5-part cron format (minute hour day month weekday) or @daily/@hourly style macros.");
            return 1;
        }

        Path projectRoot = workingDir.toAbsolutePath().normalize();
        Path resolvedScript = resolvePath(projectRoot, scriptFile);
        Path resolvedCronFile = resolvePath(projectRoot, cronFile);
        Path resolvedLogFile = resolvePath(projectRoot, logFile);
        Path logDirectory = resolvedLogFile.getParent();
        if (logDirectory == null) {
            logDirectory = projectRoot.resolve("output");
        }

        String scriptContent = renderScript(projectRoot, logDirectory, resolvedLogFile);
        String cronContent = renderCronEntry(resolvedScript);

        try {
            writeFile(resolvedScript, scriptContent);
            writeFile(resolvedCronFile, cronContent);
        } catch (IOException e) {
            System.err.println("Failed to generate scheduled-run artifacts: " + e.getMessage());
            return 1;
        }

        if (!resolvedScript.toFile().setExecutable(true, false)) {
            System.err.println("Warning: could not mark script as executable. Run: chmod +x " + resolvedScript);
        }

        System.out.println("Scheduled-run artifacts generated (not active).");
        System.out.println("script_file: " + resolvedScript);
        System.out.println("cron_file: " + resolvedCronFile);
        System.out.println("log_file: " + resolvedLogFile);
        System.out.println(CRON_INSTALL_HINT + resolvedCronFile);
        return 0;
    }

    private boolean isCronValid(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.startsWith(CRON_MACRO_PREFIX)) {
            return true;
        }
        String[] parts = normalized.split("\\s+");
        return parts.length == EXPECTED_CRON_PARTS;
    }

    private Path resolvePath(Path projectRoot, Path value) {
        if (value == null) {
            return projectRoot;
        }
        return value.isAbsolute() ? value : projectRoot.resolve(value).normalize();
    }

    private String renderScript(Path projectRoot, Path logDirectory, Path logFilePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/usr/bin/env bash\n");
        sb.append("set -euo pipefail\n\n");
        sb.append("cd ").append(shellQuote(projectRoot.toString())).append("\n");
        sb.append("mkdir -p ").append(shellQuote(logDirectory.toString())).append("\n");
        sb.append(shellQuote(gradleCommand.trim()))
                .append(" --no-daemon run --args=")
                .append(shellQuote(runCommand.trim()))
                .append(" >> ")
                .append(shellQuote(logFilePath.toString()))
                .append(" 2>&1\n");
        return sb.toString();
    }

    private String renderCronEntry(Path scriptPath) {
        return cron.trim() + " /bin/bash " + shellQuote(scriptPath.toString()) + "\n";
    }

    private void writeFile(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String shellQuote(String value) {
        return SINGLE_QUOTE + value.replace(SINGLE_QUOTE, SINGLE_QUOTE_ESCAPE) + SINGLE_QUOTE;
    }
}
