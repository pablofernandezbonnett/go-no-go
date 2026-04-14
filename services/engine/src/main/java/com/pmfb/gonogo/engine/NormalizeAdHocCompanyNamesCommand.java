package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.exception.AdHocEvaluationArtifactLoadException;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.job.JobInputFieldKeys;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "normalize-ad-hoc-company-names",
        mixinStandardHelpOptions = true,
        description = "Normalize saved ad-hoc company names in place without re-running evaluation."
)
public final class NormalizeAdHocCompanyNamesCommand implements Callable<Integer> {
    private static final String DEFAULT_INPUT_DIR = "output/ad-hoc-evaluations";
    private static final String DEFAULT_PATTERN = "*.yaml";
    private static final String LABEL_COMPLETED = "normalize-ad-hoc-company-names completed.";
    private static final String FIELD_ARTIFACTS_TOTAL = "artifacts_total";
    private static final String FIELD_NORMALIZED_UPDATED = "normalized_updated";
    private static final String FIELD_NORMALIZED_UNCHANGED = "normalized_unchanged";
    private static final String FIELD_NORMALIZED_FAILED = "normalized_failed";
    private static final String FIELD_INPUT_DIR = "input_dir";
    private static final String FIELD_PATTERN = "pattern";
    private static final String FIELD_UPDATED_ARTIFACT = "updated_artifact";

    private final CompanyNameResolver companyNameResolver;

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Option(
            names = {"--input-dir"},
            description = "Directory containing ad-hoc evaluation YAML artifacts.",
            defaultValue = DEFAULT_INPUT_DIR
    )
    private Path inputDir;

    @Option(
            names = {"--pattern"},
            description = "Glob pattern for artifact files (relative to input dir).",
            defaultValue = DEFAULT_PATTERN
    )
    private String pattern;

    @Option(
            names = {"--fail-fast"},
            description = "Stop on the first failed artifact rewrite."
    )
    private boolean failFast;

    public NormalizeAdHocCompanyNamesCommand() {
        this(new CompanyNameResolver());
    }

    NormalizeAdHocCompanyNamesCommand(CompanyNameResolver companyNameResolver) {
        this.companyNameResolver = companyNameResolver;
    }

    @Override
    public Integer call() throws IOException {
        EngineConfig config;
        try {
            config = new YamlConfigLoader(configDir).load();
        } catch (ConfigLoadException e) {
            printErrors("Configuration loading failed", e.errors());
            return 1;
        }

        List<Path> artifactFiles = findArtifactFiles(inputDir, pattern);
        if (artifactFiles.isEmpty()) {
            System.err.println("No ad-hoc evaluation artifacts found in " + inputDir + " matching '" + pattern + "'.");
            return 1;
        }

        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        for (Path artifactFile : artifactFiles) {
            try {
                if (normalizeArtifact(artifactFile, config.companies()).updated()) {
                    updated++;
                    System.out.println(FIELD_UPDATED_ARTIFACT + ": " + artifactFile);
                } else {
                    unchanged++;
                }
            } catch (IOException | AdHocEvaluationArtifactLoadException e) {
                failed++;
                System.err.println("Failed to normalize ad-hoc artifact '" + artifactFile + "': " + e.getMessage());
                if (e instanceof AdHocEvaluationArtifactLoadException loadException) {
                    printNestedErrors(loadException.errors());
                }
                if (failFast) {
                    break;
                }
            }
        }

        System.out.println(LABEL_COMPLETED);
        System.out.println(FIELD_ARTIFACTS_TOTAL + ": " + artifactFiles.size());
        System.out.println(FIELD_NORMALIZED_UPDATED + ": " + updated);
        System.out.println(FIELD_NORMALIZED_UNCHANGED + ": " + unchanged);
        System.out.println(FIELD_NORMALIZED_FAILED + ": " + failed);
        System.out.println(FIELD_INPUT_DIR + ": " + inputDir);
        System.out.println(FIELD_PATTERN + ": " + pattern);
        return failed == 0 ? 0 : 1;
    }

    private NormalizationOutcome normalizeArtifact(Path artifactFile, List<CompanyConfig> companies) throws IOException {
        Map<String, Object> root = loadRoot(artifactFile);
        Map<String, Object> source = readRequiredMap(root, EvaluateInputFieldKeys.SOURCE, artifactFile);
        Map<String, Object> jobInput = readRequiredMap(root, EvaluateInputFieldKeys.JOB_INPUT, artifactFile);
        String sourceUrl = readOptionalString(source, EvaluateInputFieldKeys.URL);
        if (sourceUrl.isBlank()) {
            return new NormalizationOutcome(false);
        }

        String currentCompanyName = readOptionalString(jobInput, JobInputFieldKeys.COMPANY_NAME);
        String baseCompanyName = currentCompanyName;
        if (companyNameResolver.looksLikeGenericCompanyPlaceholder(baseCompanyName)) {
            baseCompanyName = companyNameResolver.resolveConfiguredCompanyName(sourceUrl, companies);
        }

        String resolvedCompanyName = companyNameResolver.resolve(
                baseCompanyName,
                readOptionalString(jobInput, JobInputFieldKeys.TITLE),
                readOptionalString(jobInput, JobInputFieldKeys.DESCRIPTION),
                "",
                sourceUrl
        );
        if (resolvedCompanyName.isBlank() || resolvedCompanyName.equals(currentCompanyName)) {
            return new NormalizationOutcome(false);
        }

        jobInput.put(JobInputFieldKeys.COMPANY_NAME, resolvedCompanyName);
        writeRoot(artifactFile, root);
        return new NormalizationOutcome(true);
    }

    private List<Path> findArtifactFiles(Path directory, String globPattern) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return List.of();
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(directory.relativize(path)) || matcher.matches(path.getFileName()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private Map<String, Object> loadRoot(Path artifactFile) {
        List<String> errors = new ArrayList<>();
        if (!Files.exists(artifactFile)) {
            errors.add("Ad-hoc artifact does not exist: " + artifactFile);
        } else if (!Files.isRegularFile(artifactFile)) {
            errors.add("Ad-hoc artifact path is not a regular file: " + artifactFile);
        }
        if (!errors.isEmpty()) {
            throw new AdHocEvaluationArtifactLoadException(errors);
        }

        try (InputStream inputStream = Files.newInputStream(artifactFile)) {
            Object loaded = new Yaml().load(inputStream);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new AdHocEvaluationArtifactLoadException(List.of(
                        "Ad-hoc artifact YAML root must be a mapping object: " + artifactFile
                ));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) map;
            return root;
        } catch (YAMLException e) {
            throw new AdHocEvaluationArtifactLoadException(List.of(
                    "Invalid YAML in ad-hoc artifact " + artifactFile + ": " + e.getMessage()
            ));
        } catch (IOException e) {
            throw new AdHocEvaluationArtifactLoadException(List.of(
                    "Unable to read ad-hoc artifact " + artifactFile + ": " + e.getMessage()
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequiredMap(Map<String, Object> map, String key, Path artifactFile) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> nested)) {
            throw new AdHocEvaluationArtifactLoadException(List.of(
                    "Missing mapping field '" + key + "' in " + artifactFile
            ));
        }
        return (Map<String, Object>) nested;
    }

    private String readOptionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            return "";
        }
        return text.trim();
    }

    private void writeRoot(Path artifactFile, Map<String, Object> root) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        String yaml = new Yaml(options).dump(root);
        Files.writeString(artifactFile, yaml, StandardCharsets.UTF_8);
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        printNestedErrors(errors);
    }

    private void printNestedErrors(List<String> errors) {
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    private record NormalizationOutcome(boolean updated) {
    }
}
