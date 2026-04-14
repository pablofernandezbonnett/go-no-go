package com.pmfb.gonogo.engine;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
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
    private static final String SOURCE_URL_PREFIX = "Source URL: ";
    private static final String PAGE_CONTENT_ROOT_SELECTOR = "main,article,.job-description,.content";
    private static final String PAGE_NOISE_SELECTOR = "script,style,noscript,svg,footer,nav";
    private static final String PAGE_DESCRIPTION_META_SELECTOR =
            "meta[name=description],meta[property=og:description],meta[name=twitter:description]";
    private static final String PAGE_TITLE_META_SELECTOR =
            "meta[property=og:title],meta[name=twitter:title]";
    private static final List<String> PAGE_TITLE_SELECTOR_PRIORITY = List.of(
            "header h1",
            "#job-header h1",
            "main h1",
            "article h1",
            "h1",
            "header h2",
            "#job-header h2",
            "main h2",
            "article h2",
            "h2"
    );
    private static final List<Pattern> PAGE_SECTION_STOP_PATTERNS = List.of(
            Pattern.compile("(?i)^related jobs$"),
            Pattern.compile("(?i)^more jobs like this$"),
            Pattern.compile("(?i)^other openings$"),
            Pattern.compile("(?i)^similar jobs$"),
            Pattern.compile("(?i)^you may also like$"),
            Pattern.compile("(?i)^meet .+ developers$"),
            Pattern.compile("(?i)^get the newsletter$")
    );
    private static final Pattern ABOUT_COMPANY_HEADING_PATTERN = Pattern.compile("(?i)^about\\s+(.+)$");
    private static final Set<String> GENERIC_ABOUT_SECTION_LABELS = Set.of(
            "the job",
            "the position",
            "the role",
            "the company",
            "the team",
            "us"
    );
    private static final Set<String> CONTENT_ROOT_METADATA_HINTS = Set.of(
            "remote",
            "hybrid",
            "onsite",
            "japan residents only",
            "apply from abroad",
            "no japanese required",
            "experience required",
            "requirements",
            "responsibilities",
            "salary",
            "¥"
    );
    private static final List<String> PAGE_CHALLENGE_HINTS = List.of(
            "just a moment",
            "enable javascript and cookies to continue",
            "__cf_chl_opt"
    );
    private static final int MAX_URL_DESCRIPTION_LENGTH = 16000;
    private static final String OUTPUT_FORMAT_TEXT = "text";
    private static final String OUTPUT_FORMAT_JSON = "json";
    private static final String URL_METADATA_FALLBACK_WARNING =
            "Page body was unavailable; evaluated the URL using title and metadata only.";

    private final DecisionEngineV1 engine;
    private final RawJobParser rawJobParser;
    private final JobPostingExtractor extractor;
    private final CareerPageFetcher httpFetcher;
    private final DirectInputSecurity directInputSecurity;
    private final EvaluateInputArtifactWriter artifactWriter;
    private final EvaluateInputOutputFormatter outputFormatter;
    private final CompanyNameResolver companyNameResolver;

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
                new EvaluateInputOutputFormatter(),
                new CompanyNameResolver()
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
        this(
                engine,
                rawJobParser,
                extractor,
                httpFetcher,
                directInputSecurity,
                artifactWriter,
                outputFormatter,
                new CompanyNameResolver()
        );
    }

    EvaluateInputCommand(
            DecisionEngineV1 engine,
            RawJobParser rawJobParser,
            JobPostingExtractor extractor,
            CareerPageFetcher httpFetcher,
            DirectInputSecurity directInputSecurity,
            EvaluateInputArtifactWriter artifactWriter,
            EvaluateInputOutputFormatter outputFormatter,
            CompanyNameResolver companyNameResolver
    ) {
        this.engine = engine;
        this.rawJobParser = rawJobParser;
        this.extractor = extractor;
        this.httpFetcher = httpFetcher;
        this.directInputSecurity = directInputSecurity;
        this.artifactWriter = artifactWriter;
        this.outputFormatter = outputFormatter;
        this.companyNameResolver = companyNameResolver;
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
        List<String> warnings = new ArrayList<>();
        String companyName = companyNameResolver.resolveConfiguredCompanyName(response.finalUrl(), config.companies());
        if (companyNameOverride != null && !companyNameOverride.isBlank()) {
            companyName = companyNameOverride.trim();
        }

        String title = resolveTitle(candidate, doc);
        if (title == null || title.isBlank()) {
            throw new JobInputLoadException(List.of(
                    "Could not infer a job title from URL page content."
            ));
        }
        companyName = companyNameResolver.resolve(
                companyName,
                title,
                readMetaContent(doc, PAGE_DESCRIPTION_META_SELECTOR),
                inferCompanyNameFromAboutHeading(doc),
                response.finalUrl()
        );

        String pageText = extractPageText(doc, title);
        if (pageText.isBlank()) {
            pageText = candidate.map(JobPostingCandidate::snippet)
                    .map(this::normalizeMultilineText)
                    .orElse("");
        }
        if (pageText.isBlank()) {
            pageText = extractMetadataFallbackText(doc, title);
            if (!pageText.isBlank()) {
                warnings.add(URL_METADATA_FALLBACK_WARNING);
            }
        }
        if (pageText.isBlank()) {
            throw new JobInputLoadException(List.of(
                    "Could not extract meaningful job content from URL page."
            ));
        }

        RawJobExtractionResult extraction = rawJobParser.parse(pageText, companyName, title);
        warnings.addAll(extraction.warnings());
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
        return new LoadedUrlJobInput(jobInput, warnings);
    }

    private String resolveTitle(Optional<JobPostingCandidate> candidate, Document doc) {
        if (titleOverride != null && !titleOverride.isBlank()) {
            return titleOverride.trim();
        }
        for (String selector : PAGE_TITLE_SELECTOR_PRIORITY) {
            Element heading = doc.selectFirst(selector);
            if (heading == null) {
                continue;
            }
            String headingText = normalizeText(heading.text());
            if (!headingText.isBlank()) {
                return headingText;
            }
        }
        if (candidate.isPresent()) {
            String extractedTitle = normalizeText(candidate.get().title());
            if (!extractedTitle.isBlank()) {
                return extractedTitle;
            }
        }
        return normalizeText(doc.title());
    }

    private String extractPageText(Document doc, String resolvedTitle) {
        List<Element> roots = collectContentRoots(doc);
        String bestText = "";
        int bestScore = Integer.MIN_VALUE;
        for (Element root : roots) {
            String candidateText = normalizeMultilineText(root.wholeText());
            if (candidateText.isBlank()) {
                candidateText = normalizeMultilineText(root.text());
            }
            candidateText = trimTrailingNonJobSections(trimLeadingPageChrome(candidateText, resolvedTitle));
            int score = scoreContentRootText(candidateText, resolvedTitle);
            if (score > bestScore) {
                bestScore = score;
                bestText = candidateText;
            }
        }
        return bestText;
    }

    private String extractMetadataFallbackText(Document doc, String resolvedTitle) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addMetadataPart(parts, resolvedTitle);
        addMetadataPart(parts, readMetaContent(doc, PAGE_TITLE_META_SELECTOR));
        addMetadataPart(parts, readMetaContent(doc, PAGE_DESCRIPTION_META_SELECTOR));
        if (parts.size() < 2) {
            return "";
        }
        return String.join("\n", parts);
    }

    private String readMetaContent(Document doc, String selector) {
        Element element = doc.selectFirst(selector);
        if (element == null) {
            return "";
        }
        return normalizeText(element.attr("content"));
    }

    private void addMetadataPart(LinkedHashSet<String> parts, String value) {
        String normalized = normalizeText(value);
        if (!normalized.isBlank()) {
            parts.add(normalized);
        }
    }

    private String inferCompanyNameFromAboutHeading(Document doc) {
        for (Element heading : doc.select("main h2, main h3, article h2, article h3, h2, h3")) {
            String text = normalizeText(heading.text());
            java.util.regex.Matcher matcher = ABOUT_COMPANY_HEADING_PATTERN.matcher(text);
            if (!matcher.matches()) {
                continue;
            }
            String candidate = normalizeText(matcher.group(1));
            if (candidate.isBlank()) {
                continue;
            }
            String lowered = candidate.toLowerCase(Locale.ROOT);
            if (GENERIC_ABOUT_SECTION_LABELS.contains(lowered)) {
                continue;
            }
            if (candidate.length() > 80) {
                continue;
            }
            return candidate;
        }
        return "";
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

    private List<Element> collectContentRoots(Document doc) {
        LinkedHashSet<Element> roots = new LinkedHashSet<>();
        if (doc.body() != null) {
            roots.add(doc.body());
        }
        roots.addAll(doc.select(PAGE_CONTENT_ROOT_SELECTOR));
        return List.copyOf(roots);
    }

    private String trimLeadingPageChrome(String text, String resolvedTitle) {
        if (text.isBlank()) {
            return "";
        }
        String normalizedTitle = normalizeText(resolvedTitle);
        if (normalizedTitle.isBlank()) {
            return text;
        }
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(this::normalizeText)
                .filter(line -> !line.isBlank())
                .toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.equalsIgnoreCase(normalizedTitle)) {
                return String.join("\n", lines.subList(i, lines.size()));
            }
        }
        return String.join("\n", lines);
    }

    private int scoreContentRootText(String text, String resolvedTitle) {
        if (text.isBlank()) {
            return Integer.MIN_VALUE;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String hint : PAGE_CHALLENGE_HINTS) {
            if (lowered.contains(hint)) {
                return Integer.MIN_VALUE / 2;
            }
        }
        int score = Math.min(text.length(), 320);
        String normalizedTitle = normalizeText(resolvedTitle);
        if (!normalizedTitle.isBlank()) {
            if (text.startsWith(normalizedTitle + "\n") || text.equals(normalizedTitle)) {
                score += 220;
            } else if (lowered.contains(normalizedTitle.toLowerCase(Locale.ROOT))) {
                score += 120;
            }
        }
        for (String hint : CONTENT_ROOT_METADATA_HINTS) {
            if (lowered.contains(hint)) {
                score += 30;
            }
        }
        return score;
    }

    private String trimTrailingNonJobSections(String text) {
        if (text.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (isNonJobSectionHeading(line)) {
                break;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    private boolean isNonJobSectionHeading(String line) {
        String normalized = normalizeText(line);
        for (Pattern pattern : PAGE_SECTION_STOP_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return true;
            }
        }
        return false;
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
