package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.job.JobInputFieldKeys;
import com.pmfb.gonogo.engine.job.JobInputYamlWriter;
import com.pmfb.gonogo.engine.job.RawJobExtractionResult;
import com.pmfb.gonogo.engine.job.RawJobParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "fetch",
        description = "Normalize raw job text into a job input YAML file."
)
public final class FetchCommand implements Callable<Integer> {
    private static final String FIELD_WARNINGS = "warnings";
    private final RawJobParser parser;
    private final JobInputYamlWriter writer;

    public FetchCommand() {
        this(new RawJobParser(), new JobInputYamlWriter());
    }

    public FetchCommand(RawJobParser parser, JobInputYamlWriter writer) {
        this.parser = parser;
        this.writer = writer;
    }

    @Option(
            names = {"--input-file"},
            description = "Raw text file containing a job post.",
            required = true
    )
    private Path inputFile;

    @Option(
            names = {"--output-file"},
            description = "Output YAML file to generate.",
            required = true
    )
    private Path outputFile;

    @Option(
            names = {"--company-name"},
            description = "Optional company name override."
    )
    private String companyName;

    @Option(
            names = {"--title"},
            description = "Optional title override."
    )
    private String title;

    @Override
    public Integer call() {
        String rawText;
        try {
            rawText = Files.readString(inputFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read input file '" + inputFile + "': " + e.getMessage());
            return 1;
        }

        RawJobExtractionResult extraction = parser.parse(rawText, companyName, title);
        JobInput jobInput = extraction.jobInput();

        try {
            writer.write(outputFile, jobInput);
        } catch (IOException e) {
            System.err.println("Failed to write output file '" + outputFile + "': " + e.getMessage());
            return 1;
        }

        System.out.println("Generated job input YAML: " + outputFile);
        System.out.println(JobInputFieldKeys.COMPANY_NAME + ": " + jobInput.companyName());
        System.out.println(JobInputFieldKeys.TITLE + ": " + jobInput.title());
        System.out.println(JobInputFieldKeys.LOCATION + ": " + jobInput.location());
        System.out.println(JobInputFieldKeys.SALARY_RANGE + ": " + jobInput.salaryRange());
        System.out.println(JobInputFieldKeys.REMOTE_POLICY + ": " + jobInput.remotePolicy());
        if (extraction.warnings().isEmpty()) {
            System.out.println(FIELD_WARNINGS + ": []");
        } else {
            System.out.println(FIELD_WARNINGS + ":");
            for (String warning : extraction.warnings()) {
                System.out.println(" - " + warning);
            }
        }
        return 0;
    }
}
