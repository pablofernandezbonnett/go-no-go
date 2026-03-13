package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.ConfigSelections;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.decision.DecisionEngineV1;
import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import com.pmfb.gonogo.engine.exception.JobInputLoadException;
import com.pmfb.gonogo.engine.job.CareerPageFetcher;
import com.pmfb.gonogo.engine.job.CareerPageHttpFetcher;
import com.pmfb.gonogo.engine.job.JobInput;
import com.pmfb.gonogo.engine.job.JobPostingCandidate;
import com.pmfb.gonogo.engine.job.JobPostingExtractor;
import com.pmfb.gonogo.engine.job.RawJobExtractionResult;
import com.pmfb.gonogo.engine.job.RawJobParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "evaluate-input",
        mixinStandardHelpOptions = true,
        description = "Evaluate a job directly from URL or raw text (without creating a YAML file first)."
)
public final class EvaluateInputCommand implements Callable<Integer> {
    private static final String DEFAULT_USER_AGENT = "go-no-go-engine/0.1 (+https://local)";
    private static final String DEFAULT_UNKNOWN = "Unknown";
    private static final String SOURCE_URL_PREFIX = "Source URL: ";
    private static final String PAGE_CONTENT_ROOT_SELECTOR = "main,article,.job-description,.content,body";
    private static final String PAGE_NOISE_SELECTOR = "script,style,noscript,svg,footer,header,nav";
    private static final int MAX_URL_DESCRIPTION_LENGTH = 16000;
    private static final String OUTPUT_FORMAT_TEXT = "text";
    private static final String OUTPUT_FORMAT_JSON = "json";

    private final DecisionEngineV1 engine;
    private final RawJobParser rawJobParser;
    private final JobPostingExtractor extractor;
    private final CareerPageFetcher httpFetcher;
    private final DirectInputSecurity directInputSecurity;
    private final EvaluateInputArtifactWriter artifactWriter;
    private final EvaluateInputOutputFormatter outputFormatter;

    @Option(
            names = {"--persona"},
            description = "Persona id from config/personas.yaml",
            required = true
    )
    private String personaId;

    @Option(
            names = {"--config-dir"},
            description = "Directory containing config YAML files.",
            defaultValue = "config"
    )
    private Path configDir;

    @Option(
            names = {"--candidate-profile"},
            description = "Optional candidate profile id from config/candidate-profiles (auto-selects when exactly one exists)."
    )
    private String candidateProfileId;

    @Option(
            names = {"--job-url"},
            description = "Direct URL of a job/careers page to evaluate."
    )
    private String jobUrl;

    @Option(
            names = {"--raw-text-file"},
            description = "Raw text file containing one job post."
    )
    private Path rawTextFile;

    @Option(
            names = {"--raw-text"},
            description = "Raw text content containing one job post."
    )
    private String rawText;

    @Option(
            names = {"--stdin"},
            description = "Read raw job text from STDIN."
    )
    private boolean readFromStdin;

    @Option(
            names = {"--company-name"},
            description = "Optional company override for raw text parsing or URL mode."
    )
    private String companyNameOverride;

    @Option(
            names = {"--title"},
            description = "Optional title override for raw text parsing or URL mode."
    )
    private String titleOverride;

    @Option(
            names = {"--timeout-seconds"},
            description = "HTTP timeout for --job-url mode.",
            defaultValue = "20"
    )
    private int timeoutSeconds;

    @Option(
            names = {"--user-agent"},
            description = "User-Agent for --job-url mode.",
            defaultValue = DEFAULT_USER_AGENT
    )
    private String userAgent;

    @Option(
            names = {"--output-format"},
            description = "Output format: text, json.",
            defaultValue = OUTPUT_FORMAT_TEXT
    )
    private String outputFormat;

    @Option(
            names = {"--output-analysis-file"},
            description = "Optional YAML artifact path for normalized input and evaluation output."
    )
    private Path outputAnalysisFile;

    public EvaluateInputCommand() {
        this(
                new DecisionEngineV1(),
                new RawJobParser(),
                new JobPostingExtractor(),
                new CareerPageHttpFetcher(new DirectInputSecurity()::ensureSafeHttpUri),
                new DirectInputSecurity(),
                new EvaluateInputArtifactWriter(),
                new EvaluateInputOutputFormatter()
        );
    }

    EvaluateInputCommand(
            DecisionEngineV1 engine,
            RawJobParser rawJobParser,
            JobPostingExtractor extractor,
            CareerPageFetcher httpFetcher,
            DirectInputSecurity directInputSecurity,
            EvaluateInputArtifactWriter artifactWriter,
            EvaluateInputOutputFormatter outputFormatter
    ) {
        this.engine = engine;
        this.rawJobParser = rawJobParser;
        this.extractor = extractor;
        this.httpFetcher = httpFetcher;
        this.directInputSecurity = directInputSecurity;
        this.artifactWriter = artifactWriter;
        this.outputFormatter = outputFormatter;
    }

    @Override
    public Integer call() {
        EngineConfig config;
        try {
            config = new YamlConfigLoader(configDir).load();
        } catch (ConfigLoadException e) {
            printErrors("Configuration loading failed", e.errors());
            return 1;
        }

        List<String> configValidationErrors = new ConfigValidator().validate(config);
        if (!configValidationErrors.isEmpty()) {
            printErrors("Configuration validation failed", configValidationErrors);
            return 1;
        }

        Optional<PersonaConfig> persona = ConfigSelections.findPersona(config.personas(), personaId);
        if (persona.isEmpty()) {
            System.err.println("Unknown persona id '" + personaId + "'.");
            System.err.println("Available personas:");
            for (PersonaConfig item : config.personas()) {
                System.err.println(" - " + item.id());
            }
            return 1;
        }

        ConfigSelections.CandidateProfileResolution candidateProfileResolution =
                ConfigSelections.resolveCandidateProfile(config.candidateProfiles(), candidateProfileId);
        if (!candidateProfileResolution.errorMessage().isBlank()) {
            System.err.println(candidateProfileResolution.errorMessage());
            if (!config.candidateProfiles().isEmpty()) {
                System.err.println("Available candidate profiles:");
                for (CandidateProfileConfig item : config.candidateProfiles()) {
                    System.err.println(" - " + item.id());
                }
            }
            return 1;
        }

        String normalizedOutputFormat = normalize(outputFormat);
        if (!OUTPUT_FORMAT_TEXT.equals(normalizedOutputFormat)
                && !OUTPUT_FORMAT_JSON.equals(normalizedOutputFormat)) {
            System.err.println("Unknown output format '" + outputFormat + "'. Use: text, json.");
            return 1;
        }

        InputSource source = detectSource();
        if (source == InputSource.INVALID) {
            return 1;
        }

        JobInput jobInput;
        List<String> warnings;
        String sourceUrl = "";
        String sourceFile = "";
        String sourceRawText = "";
        try {
            if (source == InputSource.URL) {
                LoadedUrlJobInput loadedUrlJobInput = fromUrl(jobUrl, config);
                jobInput = loadedUrlJobInput.jobInput();
                warnings = loadedUrlJobInput.warnings();
                sourceUrl = jobUrl.trim();
            } else {
                LoadedRawText loadedRawText = loadRawText(source);
                RawJobExtractionResult extraction = rawJobParser.parse(
                        loadedRawText.rawText(),
                        companyNameOverride,
                        titleOverride
                );
                jobInput = extraction.jobInput();
                warnings = extraction.warnings();
                sourceFile = loadedRawText.sourceFile();
                sourceRawText = loadedRawText.rawText();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to load input: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return 1;
        } catch (JobInputLoadException e) {
            printErrors("Input validation failed", e.errors());
            return 1;
        }

        EvaluationResult result = engine.evaluate(
                jobInput,
                persona.get(),
                candidateProfileResolution.profile().orElse(null),
                config
        );
        EvaluateInputAnalysis analysis = new EvaluateInputAnalysis(
                java.time.Instant.now().toString(),
                persona.get().id(),
                ConfigSelections.candidateProfileIdOrNone(candidateProfileResolution.profile().orElse(null)),
                new EvaluateInputAnalysis.SourceDetails(
                        source.value(),
                        sourceUrl,
                        sourceFile,
                        sourceRawText
                ),
                jobInput,
                result,
                warnings
        );
        String analysisFile = outputAnalysisFile == null ? "" : outputAnalysisFile.toString();
        if (outputAnalysisFile != null) {
            try {
                artifactWriter.write(outputAnalysisFile, analysis);
            } catch (IOException e) {
                System.err.println("Failed to write analysis artifact: " + e.getMessage());
                return 1;
            }
        }

        if (OUTPUT_FORMAT_JSON.equals(normalizedOutputFormat)) {
            System.out.println(outputFormatter.toJson(analysis, analysisFile));
        } else {
            System.out.print(outputFormatter.toText(analysis, analysisFile));
        }
        return 0;
    }

    private InputSource detectSource() {
        int count = 0;
        if (jobUrl != null && !jobUrl.isBlank()) {
            count++;
        }
        if (rawTextFile != null) {
            count++;
        }
        if (rawText != null && !rawText.isBlank()) {
            count++;
        }
        if (readFromStdin) {
            count++;
        }
        if (count == 0) {
            System.err.println("Provide one input source: --job-url OR --raw-text-file OR --raw-text OR --stdin.");
            return InputSource.INVALID;
        }
        if (count > 1) {
            System.err.println("Use only one input source at a time: --job-url OR --raw-text-file OR --raw-text OR --stdin.");
            return InputSource.INVALID;
        }
        if (jobUrl != null && !jobUrl.isBlank()) {
            return InputSource.URL;
        }
        if (rawTextFile != null) {
            return InputSource.RAW_TEXT_FILE;
        }
        if (readFromStdin) {
            return InputSource.STDIN;
        }
        return InputSource.RAW_TEXT;
    }

    private LoadedRawText loadRawText(InputSource source) throws IOException {
        String effectiveRawText = rawText;
        String sourceFile = "";
        if (source == InputSource.RAW_TEXT_FILE && rawTextFile != null) {
            effectiveRawText = Files.readString(rawTextFile, StandardCharsets.UTF_8);
            sourceFile = rawTextFile.toString();
        } else if (source == InputSource.STDIN) {
            effectiveRawText = readStdinText();
        }
        if (effectiveRawText == null || effectiveRawText.isBlank()) {
            throw new JobInputLoadException(List.of("Raw text is empty."));
        }
        return new LoadedRawText(directInputSecurity.sanitizeRawText(effectiveRawText), sourceFile);
    }

    private LoadedUrlJobInput fromUrl(String url, EngineConfig config) throws IOException, InterruptedException {
        String normalizedUrl = directInputSecurity.validateUrl(url);
        if (normalizedUrl.isBlank()) {
            throw new JobInputLoadException(List.of("URL cannot be blank."));
        }
        CareerPageHttpFetcher.FetchResult response = httpFetcher.fetch(
                normalizedUrl,
                Duration.ofSeconds(Math.max(5, timeoutSeconds)),
                normalize(userAgent).isBlank() ? DEFAULT_USER_AGENT : userAgent
        );
        if (response.statusCode() >= 400) {
            throw new JobInputLoadException(List.of(
                    "HTTP " + response.statusCode() + " while fetching URL."
            ));
        }

        Optional<JobPostingCandidate> candidate = extractor.extract(response.body(), response.finalUrl(), 1)
                .stream()
                .findFirst();
        return buildUrlJobInput(response, config, candidate);
    }

    private LoadedUrlJobInput buildUrlJobInput(
            CareerPageHttpFetcher.FetchResult response,
            EngineConfig config,
            Optional<JobPostingCandidate> candidate
    ) {
        Document doc = Jsoup.parse(response.body(), response.finalUrl());
        doc.select(PAGE_NOISE_SELECTOR).remove();
        String companyName = resolveCompanyName(response.finalUrl(), config.companies());
        if (companyNameOverride != null && !companyNameOverride.isBlank()) {
            companyName = companyNameOverride.trim();
        }

        String title = resolveTitle(candidate, doc);
        if (title == null || title.isBlank()) {
            throw new JobInputLoadException(List.of(
                    "Could not infer a job title from URL page content."
            ));
        }

        String pageText = extractPageText(doc);
        if (pageText.isBlank()) {
            pageText = candidate.map(JobPostingCandidate::snippet)
                    .map(this::normalizeMultilineText)
                    .orElse("");
        }
        if (pageText.isBlank()) {
            throw new JobInputLoadException(List.of(
                    "Could not extract meaningful job content from URL page."
            ));
        }

        RawJobExtractionResult extraction = rawJobParser.parse(pageText, companyName, title);
        String description = extraction.jobInput().description();
        if (description.length() > MAX_URL_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_URL_DESCRIPTION_LENGTH).trim() + "...";
        }
        description = appendSourceUrl(description, response.finalUrl());

        JobInput jobInput = new JobInput(
                companyName,
                title,
                extraction.jobInput().location(),
                extraction.jobInput().salaryRange(),
                extraction.jobInput().remotePolicy(),
                description
        );
        return new LoadedUrlJobInput(jobInput, extraction.warnings());
    }

    private String resolveTitle(Optional<JobPostingCandidate> candidate, Document doc) {
        if (titleOverride != null && !titleOverride.isBlank()) {
            return titleOverride.trim();
        }
        if (candidate.isPresent()) {
            String extractedTitle = normalizeText(candidate.get().title());
            if (!extractedTitle.isBlank()) {
                return extractedTitle;
            }
        }
        Element heading = doc.selectFirst("main h1, article h1, h1, main h2, article h2");
        if (heading != null) {
            String headingText = normalizeText(heading.text());
            if (!headingText.isBlank()) {
                return headingText;
            }
        }
        return normalizeText(doc.title());
    }

    private String extractPageText(Document doc) {
        Element contentRoot = doc.selectFirst(PAGE_CONTENT_ROOT_SELECTOR);
        if (contentRoot == null) {
            return "";
        }
        String wholeText = normalizeMultilineText(contentRoot.wholeText());
        if (!wholeText.isBlank()) {
            return wholeText;
        }
        return normalizeMultilineText(contentRoot.text());
    }

    private String resolveCompanyName(String url, List<CompanyConfig> companies) {
        String host = hostOf(url);
        for (CompanyConfig company : companies) {
            if (sameOrSubdomain(host, hostOf(company.careerUrl()))
                    || sameOrSubdomain(host, hostOf(company.corporateUrl()))) {
                return company.name();
            }
        }
        return host.isBlank() ? DEFAULT_UNKNOWN : host;
    }

    private String hostOf(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            return host.trim().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private boolean sameOrSubdomain(String candidateHost, String baseHost) {
        if (candidateHost == null || candidateHost.isBlank() || baseHost == null || baseHost.isBlank()) {
            return false;
        }
        return candidateHost.equals(baseHost) || candidateHost.endsWith("." + baseHost);
    }

    private void printErrors(String title, List<String> errors) {
        System.err.println(title + " with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println(" - " + error);
        }
    }

    private String appendSourceUrl(String description, String sourceUrl) {
        if (description == null || description.isBlank()) {
            return SOURCE_URL_PREFIX + sourceUrl;
        }
        return description + "\n" + SOURCE_URL_PREFIX + sourceUrl;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeMultilineText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Arrays.stream(value.split("\\R"))
                .map(this::normalizeText)
                .filter(line -> !line.isBlank())
                .distinct()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String readStdinText() throws IOException {
        byte[] bytes = System.in.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record LoadedRawText(
            String rawText,
            String sourceFile
    ) {
    }

    private record LoadedUrlJobInput(
            JobInput jobInput,
            List<String> warnings
    ) {
        private LoadedUrlJobInput {
            warnings = List.copyOf(warnings);
        }
    }

    private enum InputSource {
        URL(EvaluateInputFieldKeys.INPUT_SOURCE_URL),
        RAW_TEXT_FILE(EvaluateInputFieldKeys.INPUT_SOURCE_RAW_TEXT_FILE),
        RAW_TEXT(EvaluateInputFieldKeys.INPUT_SOURCE_RAW_TEXT),
        STDIN(EvaluateInputFieldKeys.INPUT_SOURCE_STDIN),
        INVALID(EvaluateInputFieldKeys.INPUT_SOURCE_INVALID);

        private final String value;

        InputSource(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
