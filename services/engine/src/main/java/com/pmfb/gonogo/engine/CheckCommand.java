package com.pmfb.gonogo.engine;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "check",
        aliases = {"quick-check", "qc"},
        mixinStandardHelpOptions = true,
        description = "Short smart evaluator. Auto-detects URL, raw text, text file, or job YAML."
)
public final class CheckCommand implements Callable<Integer> {
    private static final String DEFAULT_PERSONA = "product_expat_engineer";
    private static final String DEFAULT_CONFIG_DIR = "config";
    private static final String MODE_AUTO = "auto";
    private static final String MODE_URL = "url";
    private static final String MODE_RAW_TEXT = "raw-text";
    private static final String MODE_RAW_FILE = "raw-file";
    private static final String MODE_JOB_YAML = "job-yaml";
    private static final String URL_SCHEME_HTTP = "http";
    private static final String URL_SCHEME_HTTPS = "https";

    @Option(
            names = {"--persona"},
            defaultValue = DEFAULT_PERSONA,
            description = "Persona id from config/personas.yaml (default: ${DEFAULT-VALUE})."
    )
    private String personaId;

    @Option(
            names = {"--config-dir"},
            defaultValue = DEFAULT_CONFIG_DIR,
            description = "Directory containing config YAML files (default: ${DEFAULT-VALUE})."
    )
    private Path configDir;

    @Option(
            names = {"--candidate-profile"},
            description = "Optional candidate profile id from config/candidate-profiles (auto-selects when exactly one exists)."
    )
    private String candidateProfileId;

    @Option(
            names = {"--mode"},
            defaultValue = MODE_AUTO,
            description = "Detection mode: auto, url, raw-text, raw-file, job-yaml."
    )
    private String mode;

    @Option(
            names = {"--stdin"},
            description = "Read raw text from STDIN (for multiline copy/paste input)."
    )
    private boolean readFromStdin;

    @Option(
            names = {"--timeout-seconds"},
            defaultValue = "20",
            description = "HTTP timeout used for URL mode (default: ${DEFAULT-VALUE})."
    )
    private int timeoutSeconds;

    @Option(
            names = {"--user-agent"},
            description = "Optional custom User-Agent for URL mode."
    )
    private String userAgent;

    @Parameters(
            arity = "0..*",
            paramLabel = "INPUT",
            description = "URL, file path, or raw text."
    )
    private List<String> inputTokens;

    @Override
    public Integer call() {
        String normalizedMode = normalize(mode);
        ResolvedInput resolved;

        if (readFromStdin) {
            if (hasInputTokens()) {
                System.err.println("Use either --stdin or INPUT arguments, not both.");
                return 1;
            }
            if (!isStdinCompatibleMode(normalizedMode)) {
                System.err.println("When using --stdin, mode must be auto or raw-text.");
                return 1;
            }
            String stdinText = readStdinText();
            if (stdinText == null) {
                return 1;
            }
            if (stdinText.isBlank()) {
                System.err.println("STDIN input is empty.");
                return 1;
            }
            resolved = new ResolvedInput(InputKind.RAW_TEXT, stdinText);
        } else {
            if (!hasInputTokens()) {
                System.err.println("Provide INPUT or use --stdin.");
                return 1;
            }
            String rawInput = String.join(" ", inputTokens).trim();
            if (rawInput.isBlank()) {
                System.err.println("Input cannot be blank.");
                return 1;
            }
            resolved = resolveInput(rawInput, inputTokens, normalizedMode);
        }

        if (resolved.kind() == InputKind.INVALID) {
            return 1;
        }

        if (resolved.kind() == InputKind.JOB_YAML) {
            return runEvaluate(resolved.value());
        }
        return runEvaluateInput(resolved);
    }

    private Integer runEvaluate(String jobFilePath) {
        List<String> args = new ArrayList<>();
        args.add("--persona");
        args.add(personaId);
        args.add("--config-dir");
        args.add(configDir.toString());
        if (!normalize(candidateProfileId).isBlank()) {
            args.add("--candidate-profile");
            args.add(candidateProfileId.trim());
        }
        args.add("--job-file");
        args.add(jobFilePath);
        return new CommandLine(new EvaluateCommand()).execute(args.toArray(new String[0]));
    }

    private Integer runEvaluateInput(ResolvedInput resolved) {
        List<String> args = new ArrayList<>();
        args.add("--persona");
        args.add(personaId);
        args.add("--config-dir");
        args.add(configDir.toString());
        if (!normalize(candidateProfileId).isBlank()) {
            args.add("--candidate-profile");
            args.add(candidateProfileId.trim());
        }

        switch (resolved.kind()) {
            case URL -> {
                args.add("--job-url");
                args.add(resolved.value());
                args.add("--timeout-seconds");
                args.add(String.valueOf(timeoutSeconds));
                if (!normalize(userAgent).isBlank()) {
                    args.add("--user-agent");
                    args.add(userAgent.trim());
                }
            }
            case RAW_FILE -> {
                args.add("--raw-text-file");
                args.add(resolved.value());
            }
            case RAW_TEXT -> {
                args.add("--raw-text");
                args.add(resolved.value());
            }
            case JOB_YAML, INVALID -> {
                System.err.println("Unexpected resolved input mode.");
                return 1;
            }
        }

        return new CommandLine(new EvaluateInputCommand()).execute(args.toArray(new String[0]));
    }

    private String readStdinText() {
        try {
            byte[] bytes = System.in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read from STDIN: " + e.getMessage());
            return null;
        }
    }

    private ResolvedInput resolveInput(String rawInput, List<String> tokens, String normalizedMode) {
        if (!isSupportedMode(normalizedMode)) {
            System.err.println("Unknown mode '" + mode + "'. Use: auto, url, raw-text, raw-file, job-yaml.");
            return new ResolvedInput(InputKind.INVALID, rawInput);
        }

        if (MODE_URL.equals(normalizedMode)) {
            if (!looksLikeHttpUrl(rawInput)) {
                System.err.println("Mode url requires an http/https URL.");
                return new ResolvedInput(InputKind.INVALID, rawInput);
            }
            return new ResolvedInput(InputKind.URL, rawInput);
        }

        if (MODE_RAW_TEXT.equals(normalizedMode)) {
            return new ResolvedInput(InputKind.RAW_TEXT, rawInput);
        }

        if (MODE_RAW_FILE.equals(normalizedMode)) {
            Path file = resolveExistingFile(tokens);
            if (file == null) {
                System.err.println("Mode raw-file requires one existing file path.");
                return new ResolvedInput(InputKind.INVALID, rawInput);
            }
            return new ResolvedInput(InputKind.RAW_FILE, file.toString());
        }

        if (MODE_JOB_YAML.equals(normalizedMode)) {
            Path file = resolveExistingFile(tokens);
            if (file == null) {
                System.err.println("Mode job-yaml requires one existing YAML file path.");
                return new ResolvedInput(InputKind.INVALID, rawInput);
            }
            if (!isYaml(file)) {
                System.err.println("Mode job-yaml requires a .yaml or .yml file.");
                return new ResolvedInput(InputKind.INVALID, rawInput);
            }
            return new ResolvedInput(InputKind.JOB_YAML, file.toString());
        }

        if (tokens.size() == 1) {
            String first = tokens.get(0).trim();
            if (looksLikeHttpUrl(first)) {
                return new ResolvedInput(InputKind.URL, first);
            }

            Path file = resolvePath(first);
            if (file != null && Files.isRegularFile(file)) {
                if (isYaml(file)) {
                    return new ResolvedInput(InputKind.JOB_YAML, file.toString());
                }
                return new ResolvedInput(InputKind.RAW_FILE, file.toString());
            }
        }

        return new ResolvedInput(InputKind.RAW_TEXT, rawInput);
    }

    private Path resolveExistingFile(List<String> tokens) {
        if (tokens.size() != 1) {
            return null;
        }
        Path path = resolvePath(tokens.get(0));
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        return path;
    }

    private Path resolvePath(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Path.of(token).normalize();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean looksLikeHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = normalize(uri.getScheme());
            return (URL_SCHEME_HTTP.equals(scheme) || URL_SCHEME_HTTPS.equals(scheme))
                    && !normalize(uri.getHost()).isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isYaml(Path file) {
        String path = normalize(file.toString());
        return path.endsWith(".yaml") || path.endsWith(".yml");
    }

    private boolean isSupportedMode(String value) {
        return MODE_AUTO.equals(value)
                || MODE_URL.equals(value)
                || MODE_RAW_TEXT.equals(value)
                || MODE_RAW_FILE.equals(value)
                || MODE_JOB_YAML.equals(value);
    }

    private boolean isStdinCompatibleMode(String value) {
        return MODE_AUTO.equals(value) || MODE_RAW_TEXT.equals(value);
    }

    private boolean hasInputTokens() {
        return inputTokens != null && !inputTokens.isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ResolvedInput(InputKind kind, String value) {
    }

    private enum InputKind {
        URL,
        RAW_FILE,
        RAW_TEXT,
        JOB_YAML,
        INVALID
    }
}
