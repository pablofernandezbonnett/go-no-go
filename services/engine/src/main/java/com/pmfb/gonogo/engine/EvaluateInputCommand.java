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
    private static final String DEFAULT_LOCATION = "Unspecified";
    private static final String DEFAULT_SALARY_RANGE = "TBD";
    private static final String DEFAULT_REMOTE_POLICY = "Unknown";
    private static final String DEFAULT_UNKNOWN = "Unknown";
    private static final String SOURCE_URL_PREFIX = "Source URL: ";
    private static final int MAX_FALLBACK_DESCRIPTION_LENGTH = 1200;

    private final DecisionEngineV1 engine;
    private final RawJobParser rawJobParser;
    private final JobPostingExtractor extractor;
    private final CareerPageHttpFetcher httpFetcher;

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

    public EvaluateInputCommand() {
        this(new DecisionEngineV1(), new RawJobParser(), new JobPostingExtractor(), new CareerPageHttpFetcher());
    }

    EvaluateInputCommand(
            DecisionEngineV1 engine,
            RawJobParser rawJobParser,
            JobPostingExtractor extractor,
            CareerPageHttpFetcher httpFetcher
    ) {
        this.engine = engine;
        this.rawJobParser = rawJobParser;
        this.extractor = extractor;
        this.httpFetcher = httpFetcher;
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

        InputSource source = detectSource();
        if (source == InputSource.INVALID) {
            return 1;
        }

        JobInput jobInput;
        List<String> warnings;
        try {
            if (source == InputSource.URL) {
                jobInput = fromUrl(jobUrl, config);
                warnings = List.of();
            } else {
                RawJobExtractionResult extraction = fromRawText(source);
                jobInput = extraction.jobInput();
                warnings = extraction.warnings();
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
        printResult(persona.get(), candidateProfileResolution.profile().orElse(null), jobInput, result);
        if (!warnings.isEmpty()) {
            System.out.println("normalization_warnings:");
            for (String warning : warnings) {
                System.out.println(" - " + warning);
            }
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
        if (count == 0) {
            System.err.println("Provide one input source: --job-url OR --raw-text-file OR --raw-text.");
            return InputSource.INVALID;
        }
        if (count > 1) {
            System.err.println("Use only one input source at a time: --job-url OR --raw-text-file OR --raw-text.");
            return InputSource.INVALID;
        }
        if (jobUrl != null && !jobUrl.isBlank()) {
            return InputSource.URL;
        }
        return InputSource.RAW_TEXT;
    }

    private RawJobExtractionResult fromRawText(InputSource source) throws IOException {
        String effectiveRawText = rawText;
        if (source == InputSource.RAW_TEXT && rawTextFile != null) {
            effectiveRawText = Files.readString(rawTextFile, StandardCharsets.UTF_8);
        }
        if (effectiveRawText == null || effectiveRawText.isBlank()) {
            throw new JobInputLoadException(List.of("Raw text is empty."));
        }
        return rawJobParser.parse(effectiveRawText, companyNameOverride, titleOverride);
    }

    private JobInput fromUrl(String url, EngineConfig config) throws IOException, InterruptedException {
        String normalizedUrl = normalize(url);
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

        List<JobPostingCandidate> candidates = extractor.extract(response.body(), response.finalUrl(), 1);
        if (candidates.isEmpty()) {
            return fromUrlFallback(response, config);
        }
        JobPostingCandidate candidate = candidates.get(0);

        String companyName = resolveCompanyName(response.finalUrl(), config.companies());
        if (companyNameOverride != null && !companyNameOverride.isBlank()) {
            companyName = companyNameOverride.trim();
        }
        String title = candidate.title();
        if (titleOverride != null && !titleOverride.isBlank()) {
            title = titleOverride.trim();
        }
        String description = candidate.snippet();
        description = appendSourceUrl(description, candidate.url());

        return new JobInput(
                companyName,
                title,
                DEFAULT_LOCATION,
                DEFAULT_SALARY_RANGE,
                DEFAULT_REMOTE_POLICY,
                description
        );
    }

    private JobInput fromUrlFallback(CareerPageHttpFetcher.FetchResult response, EngineConfig config) {
        Document doc = Jsoup.parse(response.body(), response.finalUrl());
        doc.select("script,style,noscript,svg,footer,header,nav").remove();
        String companyName = resolveCompanyName(response.finalUrl(), config.companies());
        if (companyNameOverride != null && !companyNameOverride.isBlank()) {
            companyName = companyNameOverride.trim();
        }

        String title = titleOverride;
        if (title == null || title.isBlank()) {
            Element heading = doc.selectFirst("main h1, article h1, h1, main h2, article h2");
            if (heading != null) {
                title = normalizeText(heading.text());
            }
        }
        if (title == null || title.isBlank()) {
            title = normalizeText(doc.title());
        }
        if (title == null || title.isBlank()) {
            throw new JobInputLoadException(List.of(
                    "Could not infer a job title from URL page content."
            ));
        }

        Element descriptionRoot = doc.selectFirst("main,article,.job-description,.content,body");
        String description = descriptionRoot != null ? normalizeText(descriptionRoot.text()) : "";
        if (description.length() > MAX_FALLBACK_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_FALLBACK_DESCRIPTION_LENGTH).trim() + "...";
        }
        description = appendSourceUrl(description, response.finalUrl());

        return new JobInput(
                companyName,
                title,
                DEFAULT_LOCATION,
                DEFAULT_SALARY_RANGE,
                DEFAULT_REMOTE_POLICY,
                description
        );
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

    private void printResult(
            PersonaConfig persona,
            CandidateProfileConfig candidateProfile,
            JobInput job,
            EvaluationResult result
    ) {
        System.out.println("verdict: " + result.verdict());
        System.out.println("score: " + result.score() + "/100");
        System.out.println(
                "raw_score: " + result.rawScore()
                        + " (range " + result.rawScoreMin() + ".." + result.rawScoreMax() + ")"
        );
        System.out.println("language_friction_index: " + result.languageFrictionIndex() + "/100");
        System.out.println("company_reputation_index: " + result.companyReputationIndex() + "/100");
        System.out.println("persona: " + persona.id());
        System.out.println("candidate_profile: " + (candidateProfile == null ? "none" : candidateProfile.id()));
        System.out.println("company: " + job.companyName());
        System.out.println("role: " + job.title());
        printList("hard_reject_reasons", result.hardRejectReasons());
        printList("positive_signals", result.positiveSignals());
        printList("risk_signals", result.riskSignals());
        printList("reasoning", result.reasoning());
    }

    private void printList(String key, List<String> values) {
        if (values.isEmpty()) {
            System.out.println(key + ": []");
            return;
        }
        System.out.println(key + ":");
        for (String value : values) {
            System.out.println(" - " + value);
        }
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

    private enum InputSource {
        URL,
        RAW_TEXT,
        INVALID
    }
}
