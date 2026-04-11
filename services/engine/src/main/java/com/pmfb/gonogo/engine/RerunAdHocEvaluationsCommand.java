package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.AdHocEvaluationArtifactLoadException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "rerun-ad-hoc",
        mixinStandardHelpOptions = true,
        description = "Re-evaluate saved ad-hoc evaluation artifacts and overwrite them in place."
)
public final class RerunAdHocEvaluationsCommand implements Callable<Integer> {
    private static final String FIELD_ARTIFACTS_TOTAL = "artifacts_total";
    private static final String FIELD_RERUN_SUCCEEDED = "rerun_succeeded";
    private static final String FIELD_RERUN_FAILED = "rerun_failed";
    private static final String FIELD_INPUT_DIR = "input_dir";
    private static final String FIELD_PATTERN = "pattern";
    private static final String FIELD_RERUN_ARTIFACT = "rerun_artifact";
    private static final String LABEL_RERUN_COMPLETED = "rerun-ad-hoc completed.";
    private static final String DEFAULT_PATTERN = "*.yaml";
    private static final String DEFAULT_INPUT_DIR = "output/ad-hoc-evaluations";

    private final AdHocEvaluationRerunSupport rerunSupport;

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
            description = "Stop on the first failed artifact re-run."
    )
    private boolean failFast;

    public RerunAdHocEvaluationsCommand() {
        this(
                new AdHocEvaluationRerunSupport(
                        new AdHocEvaluationArtifactLoader(),
                        args -> new picocli.CommandLine(new EvaluateInputCommand()).execute(args.toArray(String[]::new))
                )
        );
    }

    RerunAdHocEvaluationsCommand(AdHocEvaluationRerunSupport rerunSupport) {
        this.rerunSupport = rerunSupport;
    }

    @Override
    public Integer call() throws IOException {
        List<Path> artifactFiles = rerunSupport.findArtifactFiles(inputDir, pattern);
        if (artifactFiles.isEmpty()) {
            System.err.println("No ad-hoc evaluation artifacts found in " + inputDir + " matching '" + pattern + "'.");
            return 1;
        }

        int successes = 0;
        int failures = 0;
        for (Path artifactFile : artifactFiles) {
            System.out.println(FIELD_RERUN_ARTIFACT + ": " + artifactFile);
            try {
                rerunArtifact(artifactFile);
                successes++;
            } catch (IOException | AdHocEvaluationArtifactLoadException e) {
                failures++;
                System.err.println("Failed to rerun ad-hoc artifact '" + artifactFile + "': " + e.getMessage());
                if (e instanceof AdHocEvaluationArtifactLoadException loadException) {
                    AdHocEvaluationRerunSupport.printErrors(loadException.errors());
                }
                if (failFast) {
                    break;
                }
            }
        }

        System.out.println(LABEL_RERUN_COMPLETED);
        System.out.println(FIELD_ARTIFACTS_TOTAL + ": " + artifactFiles.size());
        System.out.println(FIELD_RERUN_SUCCEEDED + ": " + successes);
        System.out.println(FIELD_RERUN_FAILED + ": " + failures);
        System.out.println(FIELD_INPUT_DIR + ": " + inputDir);
        System.out.println(FIELD_PATTERN + ": " + pattern);
        return failures == 0 ? 0 : 1;
    }

    private void rerunArtifact(Path artifactFile) throws IOException {
        AdHocEvaluationArtifact artifact = rerunSupport.loadArtifact(artifactFile);
        rerunSupport.rerunSource(
                artifactFile,
                configDir,
                artifact.personaId(),
                artifact.candidateProfileId(),
                artifact.source()
        );
    }
}
