package com.pmfb.gonogo.engine.decision;

import com.pmfb.gonogo.engine.config.BlacklistedCompanyConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.job.JobInput;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class DecisionEngineV1 {
    private static final int POSITIVE_PRIORITY_WEIGHT = 2;
    private static final int POSITIVE_DEFAULT_WEIGHT = 1;
    private static final int RISK_PRIORITY_WEIGHT = 3;
    private static final int RISK_DEFAULT_WEIGHT = 2;
    private static final int VERDICT_GO_MIN_RAW_SCORE = 8;
    private static final int VERDICT_GO_WITH_CAUTION_MIN_RAW_SCORE = 1;
    private static final int NORMALIZED_SCORE_MIN = 0;
    private static final int NORMALIZED_SCORE_MAX = 100;
    private static final int NORMALIZED_SCORE_NEUTRAL = 50;

    private static final int LANGUAGE_INDEX_REQUIRED_BASE = 75;
    private static final int LANGUAGE_INDEX_OPTIONAL_BASE = 5;
    private static final int LANGUAGE_INDEX_SOFT_BASE = 45;
    private static final int LANGUAGE_INDEX_MEDIUM_HIGH = 85;
    private static final int LANGUAGE_INDEX_HIGH = 95;
    private static final int LANGUAGE_INDEX_COMPANY_RISK_FLOOR = 70;
    private static final int LANGUAGE_INDEX_ENGLISH_FRIENDLY_BONUS = 10;
    private static final int LANGUAGE_INDEX_CORPORATE_ENGLISH_BONUS = 5;
    private static final int LANGUAGE_INDEX_CORPORATE_JAPANESE_PENALTY = 3;
    private static final int LANGUAGE_INDEX_OPTIONAL_MAX = 15;
    private static final int REPUTATION_INDEX_NEUTRAL_BASE = 50;
    private static final int REPUTATION_INDEX_TAG_STRONG_BONUS = 20;
    private static final int REPUTATION_INDEX_TAG_STABILITY_BONUS = 8;
    private static final int REPUTATION_INDEX_TAG_RISK_PENALTY = 22;
    private static final int REPUTATION_INDEX_TAG_LAYOFF_PENALTY = 22;
    private static final int REPUTATION_INDEX_TEXT_POSITIVE_HIT_BONUS = 6;
    private static final int REPUTATION_INDEX_TEXT_POSITIVE_MAX_BONUS = 18;
    private static final int REPUTATION_INDEX_TEXT_RISK_HIT_PENALTY = 8;
    private static final int REPUTATION_INDEX_TEXT_RISK_MAX_PENALTY = 32;
    private static final int REPUTATION_INDEX_POSITIVE_THRESHOLD = 65;
    private static final int REPUTATION_INDEX_STRONG_POSITIVE_THRESHOLD = 85;
    private static final int REPUTATION_INDEX_RISK_THRESHOLD = 40;
    private static final int REPUTATION_INDEX_HIGH_RISK_THRESHOLD = 20;

    private static final List<String> SALARY_MISSING_KEYWORDS = List.of(
            "tbd",
            "to be discussed",
            "not disclosed",
            "n/a",
            "na",
            "negotiable",
            "competitive"
    );
    private static final List<String> ONSITE_ONLY_KEYWORDS = List.of(
            "onsite-only",
            "on-site only",
            "on site only",
            "office only",
            "fully onsite",
            "5 days office",
            "five days office"
    );
    private static final List<String> REMOTE_OR_HYBRID_KEYWORDS = List.of(
            "remote",
            "hybrid",
            "work from home",
            "wfh"
    );
    private static final List<String> CONSULTING_HARD_KEYWORDS = List.of(
            "consulting",
            "dispatch",
            "staffing",
            "system engineer service",
            "ses",
            "client assignment"
    );
    private static final List<String> STARTUP_RISK_KEYWORDS = List.of(
            "seed",
            "pre-seed",
            "series a",
            "early stage startup",
            "early-stage startup",
            "stealth startup"
    );
    private static final List<String> ABUSIVE_OVERTIME_KEYWORDS = List.of(
            "unpaid overtime",
            "mandatory overtime",
            "weekend work required",
            "late nights required",
            "60 hours",
            "70 hours",
            "996"
    );
    private static final List<String> OVERTIME_RISK_KEYWORDS = List.of(
            "fixed overtime",
            "fast-paced",
            "high-pressure",
            "tight deadlines",
            "deadlines are tight",
            "occasional weekend work"
    );
    private static final List<String> LANGUAGE_REQUIRED_KEYWORDS = List.of(
            "japanese required",
            "japanese is required",
            "japanese language required",
            "must speak japanese",
            "japanese is mandatory",
            "mandatory japanese",
            "japanese proficiency required",
            "native japanese",
            "native-level japanese",
            "native level japanese",
            "fluent japanese",
            "advanced japanese",
            "professional japanese",
            "business-level japanese",
            "business level japanese",
            "jlpt n1",
            "jlpt n2",
            "n1 level japanese",
            "n2 level japanese",
            "日本語必須",
            "ビジネスレベルの日本語",
            "日本語ネイティブ",
            "日本語能力試験n1",
            "日本語能力試験n2"
    );
    private static final List<String> LANGUAGE_FRICTION_SOFT_KEYWORDS = List.of(
            "business japanese",
            "japanese fluency",
            "japanese proficiency",
            "japanese communication",
            "japanese skill"
    );
    private static final List<String> LANGUAGE_HIGH_FRICTION_KEYWORDS = List.of(
            "native japanese",
            "native-level japanese",
            "native level japanese",
            "jlpt n1",
            "n1 level japanese",
            "日本語ネイティブ",
            "日本語能力試験n1",
            "日本語能力試験ｎ1",
            "日本語能力試験Ｎ1",
            "日本語能力試験Ｎ１"
    );
    private static final List<String> LANGUAGE_MEDIUM_HIGH_FRICTION_KEYWORDS = List.of(
            "business-level japanese",
            "business level japanese",
            "jlpt n2",
            "n2 level japanese",
            "ビジネスレベルの日本語",
            "日本語能力試験n2",
            "日本語能力試験ｎ2",
            "日本語能力試験Ｎ2",
            "日本語能力試験Ｎ２"
    );
    private static final List<String> LANGUAGE_OPTIONAL_OR_EXEMPT_KEYWORDS = List.of(
            "no japanese required",
            "japanese not required",
            "japanese is a plus",
            "japanese preferred",
            "japanese optional",
            "japanese nice to have",
            "basic japanese welcome",
            "conversational japanese preferred",
            "english only",
            "日本語不問",
            "日本語歓迎",
            "日本語は必須ではありません"
    );
    private static final List<String> ENGLISH_FRIENDLY_KEYWORDS = List.of(
            "english-first",
            "english first",
            "international team",
            "no japanese required",
            "japanese not required",
            "english only",
            "english speaking environment",
            "english communication",
            "global team"
    );
    private static final List<Pattern> LANGUAGE_REQUIRED_PATTERNS = List.of(
            Pattern.compile("(?i)\\bjapanese\\b.{0,24}\\b(must|required for this role|mandatory)\\b"),
            Pattern.compile("(?i)\\b(must|mandatory)\\b.{0,24}\\bjapanese\\b"),
            Pattern.compile("(?i)\\bjlpt\\s*n[12]\\b"),
            Pattern.compile("日本語.*必須"),
            Pattern.compile("必須.*日本語"),
            Pattern.compile("日本語能力試験\\s*n[12N１２]")
    );
    private static final List<Pattern> LANGUAGE_OPTIONAL_PATTERNS = List.of(
            Pattern.compile("(?i)\\bjapanese\\b.{0,24}\\b(optional|preferred|plus|nice to have|welcome)\\b"),
            Pattern.compile("(?i)\\b(no japanese required|japanese not required|english only)\\b"),
            Pattern.compile("日本語.*(不問|歓迎|必須ではありません)")
    );
    private static final List<String> PRODUCT_COMPANY_KEYWORDS = List.of(
            "product ownership",
            "own the product",
            "build product",
            "product roadmap"
    );
    private static final List<String> ENGINEERING_CULTURE_KEYWORDS = List.of(
            "engineering culture",
            "code review",
            "automated testing",
            "developer productivity",
            "quality-first"
    );
    private static final List<String> ENGINEERING_ENVIRONMENT_POSITIVE_KEYWORDS = List.of(
            "clear ownership boundaries",
            "service ownership",
            "end-to-end ownership",
            "end to end ownership",
            "blameless postmortem",
            "blameless incident review",
            "documented runbooks",
            "runbook culture",
            "sustainable on-call",
            "sustainable on call",
            "on-call compensation",
            "on call compensation",
            "error budget",
            "service level objective",
            "slo"
    );
    private static final List<String> ENGINEERING_ENVIRONMENT_RISK_KEYWORDS = List.of(
            "24/7 on-call",
            "24/7 on call",
            "frequent incidents",
            "constant firefighting",
            "legacy monolith",
            "legacy stack",
            "single point of failure",
            "no runbook",
            "no runbooks",
            "pager duty every night",
            "high pager load",
            "always-on support",
            "always on support",
            "unbounded on-call",
            "unbounded on call"
    );
    private static final List<String> WORK_LIFE_BALANCE_KEYWORDS = List.of(
            "work-life balance",
            "flexible hours",
            "no overtime",
            "mental health",
            "well-being"
    );
    private static final List<String> STABILITY_KEYWORDS = List.of(
            "profitable",
            "public company",
            "listed company",
            "established company"
    );
    private static final List<String> REPUTATION_POSITIVE_KEYWORDS = List.of(
            "profitable growth",
            "consistent profitability",
            "high retention",
            "low attrition",
            "employee retention",
            "best place to work",
            "strong glassdoor",
            "transparent leadership",
            "long-term roadmap",
            "multi-year product roadmap"
    );
    private static final List<String> REPUTATION_RISK_KEYWORDS = List.of(
            "layoff",
            "laid off",
            "workforce reduction",
            "restructuring",
            "hiring freeze",
            "high attrition",
            "toxic culture",
            "compliance scandal",
            "fraud investigation",
            "going concern",
            "salary cuts"
    );

    private static final String PRIORITY_SALARY = "salary";
    private static final String PRIORITY_HYBRID_WORK = "hybrid_work";
    private static final String PRIORITY_ENGLISH_ENVIRONMENT = "english_environment";
    private static final String PRIORITY_PRODUCT_COMPANY = "product_company";
    private static final String PRIORITY_ENGINEERING_CULTURE = "engineering_culture";
    private static final String PRIORITY_WORK_LIFE_BALANCE = "work_life_balance";
    private static final String PRIORITY_STABILITY = "stability";

    private static final String SIGNAL_SALARY_TRANSPARENCY = "salary_transparency";
    private static final String SIGNAL_HYBRID_WORK = "hybrid_work";
    private static final String SIGNAL_REMOTE_FRIENDLY = "remote_friendly";
    private static final String SIGNAL_ENGLISH_ENVIRONMENT = "english_environment";
    private static final String SIGNAL_PRODUCT_COMPANY = "product_company";
    private static final String SIGNAL_ENGINEERING_CULTURE = "engineering_culture";
    private static final String SIGNAL_ENGINEERING_ENVIRONMENT = "engineering_environment";
    private static final String SIGNAL_WORK_LIFE_BALANCE = "work_life_balance";
    private static final String SIGNAL_STABILITY = "stability";
    private static final String SIGNAL_COMPANY_REPUTATION_POSITIVE = "company_reputation_positive";
    private static final String SIGNAL_COMPANY_REPUTATION_POSITIVE_STRONG = "company_reputation_positive_strong";

    private static final String SIGNAL_SALARY_LOW_CONFIDENCE = "salary_low_confidence";
    private static final String SIGNAL_ONSITE_BIAS = "onsite_bias";
    private static final String SIGNAL_LANGUAGE_FRICTION = "language_friction";
    private static final String SIGNAL_CONSULTING_RISK = "consulting_risk";
    private static final String SIGNAL_OVERTIME_RISK = "overtime_risk";
    private static final String SIGNAL_ENGINEERING_ENVIRONMENT_RISK = "engineering_environment_risk";
    private static final String SIGNAL_STARTUP_RISK = "startup_risk";
    private static final String SIGNAL_COMPANY_REPUTATION_RISK = "company_reputation_risk";
    private static final String SIGNAL_COMPANY_REPUTATION_RISK_HIGH = "company_reputation_risk_high";

    private static final String TAG_PROFILE_EXPAT_FRIENDLY = "expat_friendly";
    private static final String TAG_PROFILE_ENGINEERING_BRAND = "engineering_brand";
    private static final String TAG_PROFILE_STRONG_WLB = "strong_wlb";
    private static final String TAG_PROFILE_STABLE_PUBLIC = "stable_public";
    private static final String TAG_PROFILE_PRODUCT_LEADER = "product_leader";
    private static final String TAG_PROFILE_REPUTATION_STRONG = "reputation_strong";

    private static final String TAG_RISK_LANGUAGE_FRICTION_HIGH = "language_friction_high";
    private static final String TAG_RISK_REPUTATION = "reputation_risk";
    private static final String TAG_RISK_LAYOFF = "layoff_risk";
    private static final String TAG_RISK_OVERTIME = "overtime_risk";

    private static final Map<String, String> POSITIVE_SIGNAL_TO_PRIORITY = Map.ofEntries(
            Map.entry(SIGNAL_SALARY_TRANSPARENCY, PRIORITY_SALARY),
            Map.entry(SIGNAL_HYBRID_WORK, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_REMOTE_FRIENDLY, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_ENGLISH_ENVIRONMENT, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_PRODUCT_COMPANY, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_ENGINEERING_CULTURE, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_ENGINEERING_ENVIRONMENT, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_WORK_LIFE_BALANCE, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_STABILITY, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_POSITIVE, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_POSITIVE_STRONG, PRIORITY_STABILITY)
    );
    private static final Map<String, String> RISK_SIGNAL_TO_PRIORITY = Map.ofEntries(
            Map.entry(SIGNAL_SALARY_LOW_CONFIDENCE, PRIORITY_SALARY),
            Map.entry(SIGNAL_ONSITE_BIAS, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_LANGUAGE_FRICTION, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_CONSULTING_RISK, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_OVERTIME_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_ENGINEERING_ENVIRONMENT_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_STARTUP_RISK, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_RISK, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_RISK_HIGH, PRIORITY_STABILITY)
    );

    public EvaluationResult evaluate(JobInput job, PersonaConfig persona, EngineConfig config) {
        LinkedHashSet<String> positiveSignals = new LinkedHashSet<>();
        LinkedHashSet<String> riskSignals = new LinkedHashSet<>();
        List<String> hardRejectReasons = new ArrayList<>();

        String jobText = normalize(job.companyName() + " " + job.title() + " " + job.description());
        Optional<CompanyConfig> trackedCompany = findTrackedCompany(job.companyName(), config.companies());
        String companyContextText = buildCompanyContextText(trackedCompany);
        String combinedText = normalize(jobText + " " + companyContextText);
        String remotePolicy = normalize(job.remotePolicy());
        String salaryRange = normalize(job.salaryRange());
        Set<String> personaHardNo = normalizeSet(persona.hardNo());
        Set<String> personaPriorities = normalizeSet(persona.priorities());

        int languageFrictionIndex = computeLanguageFrictionIndex(combinedText, trackedCompany);
        int companyReputationIndex = computeCompanyReputationIndex(combinedText, trackedCompany);

        detectPositiveSignals(job, combinedText, remotePolicy, salaryRange, trackedCompany, positiveSignals);
        detectRiskSignals(combinedText, remotePolicy, salaryRange, trackedCompany, riskSignals);
        detectReputationSignals(companyReputationIndex, positiveSignals, riskSignals);
        detectHardFilters(
                job,
                combinedText,
                remotePolicy,
                salaryRange,
                personaHardNo,
                config.blacklistedCompanies(),
                hardRejectReasons
        );

        int rawScore = computeScore(positiveSignals, riskSignals, personaPriorities);
        ScoreRange scoreRange = computeScoreRange(personaPriorities);
        int normalizedScore = normalizeScore(rawScore, scoreRange);
        Verdict verdict = decideVerdict(rawScore, hardRejectReasons);

        List<String> reasoning = buildReasoning(
                verdict,
                rawScore,
                normalizedScore,
                scoreRange,
                languageFrictionIndex,
                companyReputationIndex,
                hardRejectReasons,
                positiveSignals,
                riskSignals
        );

        return new EvaluationResult(
                verdict,
                normalizedScore,
                rawScore,
                scoreRange.min(),
                scoreRange.max(),
                languageFrictionIndex,
                companyReputationIndex,
                List.copyOf(hardRejectReasons),
                List.copyOf(positiveSignals),
                List.copyOf(riskSignals),
                reasoning
        );
    }

    private void detectPositiveSignals(
            JobInput job,
            String combinedText,
            String remotePolicy,
            String salaryRange,
            Optional<CompanyConfig> trackedCompany,
            Set<String> positiveSignals
    ) {
        if (isSalaryTransparent(salaryRange)) {
            positiveSignals.add(SIGNAL_SALARY_TRANSPARENCY);
        }
        if (remotePolicy.contains("hybrid")) {
            positiveSignals.add(SIGNAL_HYBRID_WORK);
        }
        if (remotePolicy.contains("remote")) {
            positiveSignals.add(SIGNAL_REMOTE_FRIENDLY);
        }
        if (hasEnglishFriendlySignal(combinedText, trackedCompany)) {
            positiveSignals.add(SIGNAL_ENGLISH_ENVIRONMENT);
        }
        if (isProductCompanySignal(combinedText, trackedCompany)) {
            positiveSignals.add(SIGNAL_PRODUCT_COMPANY);
        }
        if (containsAny(combinedText, ENGINEERING_CULTURE_KEYWORDS)) {
            positiveSignals.add(SIGNAL_ENGINEERING_CULTURE);
        }
        if (hasEngineeringEnvironmentPositiveSignal(combinedText)) {
            positiveSignals.add(SIGNAL_ENGINEERING_ENVIRONMENT);
        }
        if (containsAny(combinedText, WORK_LIFE_BALANCE_KEYWORDS)) {
            positiveSignals.add(SIGNAL_WORK_LIFE_BALANCE);
        }
        if (isStableCompanySignal(combinedText, trackedCompany, job.companyName())) {
            positiveSignals.add(SIGNAL_STABILITY);
        }
        detectCompanyProfilePositiveSignals(trackedCompany, positiveSignals);
    }

    private void detectRiskSignals(
            String combinedText,
            String remotePolicy,
            String salaryRange,
            Optional<CompanyConfig> trackedCompany,
            Set<String> riskSignals
    ) {
        if (containsAny(salaryRange, SALARY_MISSING_KEYWORDS) && !salaryRange.isBlank()) {
            riskSignals.add(SIGNAL_SALARY_LOW_CONFIDENCE);
        }
        if (isOnsiteBias(remotePolicy)) {
            riskSignals.add(SIGNAL_ONSITE_BIAS);
        }
        if (hasLanguageFrictionSignal(combinedText)) {
            riskSignals.add(SIGNAL_LANGUAGE_FRICTION);
        }
        if (containsAny(combinedText, OVERTIME_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_OVERTIME_RISK);
        }
        if (hasEngineeringEnvironmentRiskSignal(combinedText)) {
            riskSignals.add(SIGNAL_ENGINEERING_ENVIRONMENT_RISK);
        }
        if (containsAny(combinedText, STARTUP_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_STARTUP_RISK);
        }
        if (containsAny(combinedText, CONSULTING_HARD_KEYWORDS)) {
            riskSignals.add(SIGNAL_CONSULTING_RISK);
        }
        detectCompanyProfileRiskSignals(trackedCompany, riskSignals);
    }

    private void detectHardFilters(
            JobInput job,
            String combinedText,
            String remotePolicy,
            String salaryRange,
            Set<String> personaHardNo,
            List<BlacklistedCompanyConfig> blacklist,
            List<String> hardRejectReasons
    ) {
        if (personaHardNo.contains("consulting_company")
                && (isBlacklistedCompany(job.companyName(), blacklist)
                || containsAny(combinedText, CONSULTING_HARD_KEYWORDS))) {
            hardRejectReasons.add("consulting / dispatch indicators detected");
        }

        if (personaHardNo.contains("onsite_only") && isOnsiteOnly(remotePolicy)) {
            hardRejectReasons.add("onsite-only work policy detected");
        }

        if (personaHardNo.contains("salary_missing") && isSalaryMissing(salaryRange)) {
            hardRejectReasons.add("salary information is missing or non-transparent");
        }

        if (personaHardNo.contains("early_stage_startup") && containsAny(combinedText, STARTUP_RISK_KEYWORDS)) {
            hardRejectReasons.add("early-stage startup indicators detected");
        }

        if (containsAny(combinedText, ABUSIVE_OVERTIME_KEYWORDS)) {
            hardRejectReasons.add("abusive overtime indicators detected");
        }
    }

    private int computeScore(
            Set<String> positiveSignals,
            Set<String> riskSignals,
            Set<String> personaPriorities
    ) {
        int score = 0;
        for (String signal : positiveSignals) {
            String priority = POSITIVE_SIGNAL_TO_PRIORITY.get(signal);
            score += personaPriorities.contains(priority) ? POSITIVE_PRIORITY_WEIGHT : POSITIVE_DEFAULT_WEIGHT;
        }
        for (String signal : riskSignals) {
            String priority = RISK_SIGNAL_TO_PRIORITY.get(signal);
            score -= personaPriorities.contains(priority) ? RISK_PRIORITY_WEIGHT : RISK_DEFAULT_WEIGHT;
        }
        return score;
    }

    private Verdict decideVerdict(int rawScore, List<String> hardRejectReasons) {
        if (!hardRejectReasons.isEmpty()) {
            return Verdict.NO_GO;
        }
        if (rawScore >= VERDICT_GO_MIN_RAW_SCORE) {
            return Verdict.GO;
        }
        if (rawScore >= VERDICT_GO_WITH_CAUTION_MIN_RAW_SCORE) {
            return Verdict.GO_WITH_CAUTION;
        }
        return Verdict.NO_GO;
    }

    private List<String> buildReasoning(
            Verdict verdict,
            int rawScore,
            int normalizedScore,
            ScoreRange scoreRange,
            int languageFrictionIndex,
            int companyReputationIndex,
            List<String> hardRejectReasons,
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        List<String> reasoning = new ArrayList<>();
        for (String reason : hardRejectReasons) {
            reasoning.add("Hard filter triggered: " + reason + ".");
        }
        if (!positiveSignals.isEmpty()) {
            reasoning.add("Positive signals: " + String.join(", ", positiveSignals) + ".");
        } else {
            reasoning.add("Positive signals: none.");
        }
        if (!riskSignals.isEmpty()) {
            reasoning.add("Risk signals: " + String.join(", ", riskSignals) + ".");
        } else {
            reasoning.add("Risk signals: none.");
        }
        reasoning.add(
                "Raw weighted score: " + rawScore
                        + " (range " + scoreRange.min() + " to " + scoreRange.max() + ")."
        );
        reasoning.add("Normalized score: " + normalizedScore + "/100.");
        reasoning.add("Language friction index: " + languageFrictionIndex + "/100.");
        reasoning.add("Company reputation index: " + companyReputationIndex + "/100.");
        if (!hardRejectReasons.isEmpty()) {
            reasoning.add("Final verdict forced to NO_GO due to hard filters.");
        } else {
            reasoning.add("Final verdict derived from weighted score: " + verdict + ".");
        }
        return reasoning;
    }

    private ScoreRange computeScoreRange(Set<String> personaPriorities) {
        int min = 0;
        int max = 0;
        for (String priority : POSITIVE_SIGNAL_TO_PRIORITY.values()) {
            max += personaPriorities.contains(priority) ? POSITIVE_PRIORITY_WEIGHT : POSITIVE_DEFAULT_WEIGHT;
        }
        for (String priority : RISK_SIGNAL_TO_PRIORITY.values()) {
            min -= personaPriorities.contains(priority) ? RISK_PRIORITY_WEIGHT : RISK_DEFAULT_WEIGHT;
        }
        return new ScoreRange(min, max);
    }

    private int normalizeScore(int rawScore, ScoreRange range) {
        if (range.max() <= range.min()) {
            return NORMALIZED_SCORE_NEUTRAL;
        }
        double ratio = (rawScore - range.min()) / (double) (range.max() - range.min());
        int score = (int) Math.round(ratio * NORMALIZED_SCORE_MAX);
        if (score < NORMALIZED_SCORE_MIN) {
            return NORMALIZED_SCORE_MIN;
        }
        if (score > NORMALIZED_SCORE_MAX) {
            return NORMALIZED_SCORE_MAX;
        }
        return score;
    }

    private Optional<CompanyConfig> findTrackedCompany(String companyName, List<CompanyConfig> companies) {
        String normalizedName = normalize(companyName);
        return companies.stream()
                .filter(company -> {
                    String candidate = normalize(company.name());
                    return candidate.equals(normalizedName)
                            || candidate.contains(normalizedName)
                            || normalizedName.contains(candidate);
                })
                .findFirst();
    }

    private boolean isBlacklistedCompany(String companyName, List<BlacklistedCompanyConfig> blacklist) {
        String normalizedName = normalize(companyName);
        return blacklist.stream().anyMatch(item -> {
            String blocked = normalize(item.name());
            return blocked.equals(normalizedName)
                    || blocked.contains(normalizedName)
                    || normalizedName.contains(blocked);
        });
    }

    private boolean isProductCompanySignal(String combinedText, Optional<CompanyConfig> trackedCompany) {
        if (trackedCompany.isPresent() && normalize(trackedCompany.get().typeHint()).contains("product")) {
            return true;
        }
        return containsAny(combinedText, PRODUCT_COMPANY_KEYWORDS);
    }

    private boolean isStableCompanySignal(
            String combinedText,
            Optional<CompanyConfig> trackedCompany,
            String companyName
    ) {
        if (trackedCompany.isPresent()) {
            return true;
        }
        if (containsAny(combinedText, STABILITY_KEYWORDS)) {
            return true;
        }
        String normalizedName = normalize(companyName);
        return normalizedName.contains("corp") || normalizedName.contains("inc");
    }

    private boolean hasEnglishFriendlySignal(String combinedText, Optional<CompanyConfig> trackedCompany) {
        if (combinedText.contains("english") || containsAny(combinedText, ENGLISH_FRIENDLY_KEYWORDS)) {
            return true;
        }
        return trackedCompany.map(company -> hasCorporateEnglishHint(company.corporateUrl())).orElse(false);
    }

    private boolean hasLanguageFrictionSignal(String combinedText) {
        boolean hasStrongRequiredLanguage = containsAny(combinedText, LANGUAGE_REQUIRED_KEYWORDS)
                || matchesAnyPattern(combinedText, LANGUAGE_REQUIRED_PATTERNS);
        boolean hasOptionalLanguage = containsAny(combinedText, LANGUAGE_OPTIONAL_OR_EXEMPT_KEYWORDS)
                || matchesAnyPattern(combinedText, LANGUAGE_OPTIONAL_PATTERNS);
        if (hasStrongRequiredLanguage) {
            return true;
        }
        if (hasOptionalLanguage) {
            return false;
        }
        return containsAny(combinedText, LANGUAGE_FRICTION_SOFT_KEYWORDS);
    }

    private int computeLanguageFrictionIndex(
            String combinedText,
            Optional<CompanyConfig> trackedCompany
    ) {
        boolean hasStrongRequiredLanguage = containsAny(combinedText, LANGUAGE_REQUIRED_KEYWORDS)
                || matchesAnyPattern(combinedText, LANGUAGE_REQUIRED_PATTERNS);
        boolean hasOptionalLanguage = containsAny(combinedText, LANGUAGE_OPTIONAL_OR_EXEMPT_KEYWORDS)
                || matchesAnyPattern(combinedText, LANGUAGE_OPTIONAL_PATTERNS);
        boolean hasSoftLanguage = containsAny(combinedText, LANGUAGE_FRICTION_SOFT_KEYWORDS);

        int index = 0;
        if (hasStrongRequiredLanguage) {
            index = LANGUAGE_INDEX_REQUIRED_BASE;
        } else if (hasOptionalLanguage) {
            index = LANGUAGE_INDEX_OPTIONAL_BASE;
        } else if (hasSoftLanguage) {
            index = LANGUAGE_INDEX_SOFT_BASE;
        }

        if (containsAny(combinedText, LANGUAGE_MEDIUM_HIGH_FRICTION_KEYWORDS)) {
            index = Math.max(index, LANGUAGE_INDEX_MEDIUM_HIGH);
        }
        if (containsAny(combinedText, LANGUAGE_HIGH_FRICTION_KEYWORDS)) {
            index = Math.max(index, LANGUAGE_INDEX_HIGH);
        }

        Set<String> companyRiskTags = trackedCompany
                .map(CompanyConfig::riskTags)
                .map(this::normalizeSet)
                .orElse(Set.of());
        if (companyRiskTags.contains(TAG_RISK_LANGUAGE_FRICTION_HIGH)) {
            index = Math.max(index, LANGUAGE_INDEX_COMPANY_RISK_FLOOR);
        }

        boolean englishFriendly = hasEnglishFriendlySignal(combinedText, trackedCompany);
        if (!hasStrongRequiredLanguage && englishFriendly) {
            index -= LANGUAGE_INDEX_ENGLISH_FRIENDLY_BONUS;
        }
        if (!hasStrongRequiredLanguage && trackedCompany.isPresent()) {
            String corporateUrl = trackedCompany.get().corporateUrl();
            if (hasCorporateEnglishHint(corporateUrl)) {
                index -= LANGUAGE_INDEX_CORPORATE_ENGLISH_BONUS;
            } else if (hasCorporateJapaneseHint(corporateUrl)) {
                index += LANGUAGE_INDEX_CORPORATE_JAPANESE_PENALTY;
            }
        }
        if (hasOptionalLanguage && !hasStrongRequiredLanguage) {
            index = Math.min(index, LANGUAGE_INDEX_OPTIONAL_MAX);
        }

        if (index < NORMALIZED_SCORE_MIN) {
            return NORMALIZED_SCORE_MIN;
        }
        if (index > NORMALIZED_SCORE_MAX) {
            return NORMALIZED_SCORE_MAX;
        }
        return index;
    }

    private boolean hasEngineeringEnvironmentPositiveSignal(String combinedText) {
        return containsAny(combinedText, ENGINEERING_ENVIRONMENT_POSITIVE_KEYWORDS);
    }

    private boolean hasEngineeringEnvironmentRiskSignal(String combinedText) {
        return containsAny(combinedText, ENGINEERING_ENVIRONMENT_RISK_KEYWORDS);
    }

    private int computeCompanyReputationIndex(
            String combinedText,
            Optional<CompanyConfig> trackedCompany
    ) {
        Set<String> profileTags = trackedCompany
                .map(CompanyConfig::profileTags)
                .map(this::normalizeSet)
                .orElse(Set.of());
        Set<String> riskTags = trackedCompany
                .map(CompanyConfig::riskTags)
                .map(this::normalizeSet)
                .orElse(Set.of());

        int index = REPUTATION_INDEX_NEUTRAL_BASE;
        if (profileTags.contains(TAG_PROFILE_REPUTATION_STRONG)) {
            index += REPUTATION_INDEX_TAG_STRONG_BONUS;
        }
        if (profileTags.contains(TAG_PROFILE_STABLE_PUBLIC)) {
            index += REPUTATION_INDEX_TAG_STABILITY_BONUS;
        }
        if (riskTags.contains(TAG_RISK_REPUTATION)) {
            index -= REPUTATION_INDEX_TAG_RISK_PENALTY;
        }
        if (riskTags.contains(TAG_RISK_LAYOFF)) {
            index -= REPUTATION_INDEX_TAG_LAYOFF_PENALTY;
        }

        int positiveMatches = countKeywordMatches(combinedText, REPUTATION_POSITIVE_KEYWORDS);
        int riskMatches = countKeywordMatches(combinedText, REPUTATION_RISK_KEYWORDS);
        index += Math.min(
                REPUTATION_INDEX_TEXT_POSITIVE_MAX_BONUS,
                positiveMatches * REPUTATION_INDEX_TEXT_POSITIVE_HIT_BONUS
        );
        index -= Math.min(
                REPUTATION_INDEX_TEXT_RISK_MAX_PENALTY,
                riskMatches * REPUTATION_INDEX_TEXT_RISK_HIT_PENALTY
        );

        if (index < NORMALIZED_SCORE_MIN) {
            return NORMALIZED_SCORE_MIN;
        }
        if (index > NORMALIZED_SCORE_MAX) {
            return NORMALIZED_SCORE_MAX;
        }
        return index;
    }

    private void detectReputationSignals(
            int companyReputationIndex,
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        if (companyReputationIndex >= REPUTATION_INDEX_STRONG_POSITIVE_THRESHOLD) {
            positiveSignals.add(SIGNAL_COMPANY_REPUTATION_POSITIVE);
            positiveSignals.add(SIGNAL_COMPANY_REPUTATION_POSITIVE_STRONG);
        } else if (companyReputationIndex >= REPUTATION_INDEX_POSITIVE_THRESHOLD) {
            positiveSignals.add(SIGNAL_COMPANY_REPUTATION_POSITIVE);
        }

        if (companyReputationIndex <= REPUTATION_INDEX_HIGH_RISK_THRESHOLD) {
            riskSignals.add(SIGNAL_COMPANY_REPUTATION_RISK);
            riskSignals.add(SIGNAL_COMPANY_REPUTATION_RISK_HIGH);
        } else if (companyReputationIndex <= REPUTATION_INDEX_RISK_THRESHOLD) {
            riskSignals.add(SIGNAL_COMPANY_REPUTATION_RISK);
        }
    }

    private void detectCompanyProfilePositiveSignals(
            Optional<CompanyConfig> trackedCompany,
            Set<String> positiveSignals
    ) {
        if (trackedCompany.isEmpty()) {
            return;
        }
        Set<String> tags = normalizeSet(trackedCompany.get().profileTags());
        if (tags.contains(TAG_PROFILE_EXPAT_FRIENDLY)) {
            positiveSignals.add(SIGNAL_ENGLISH_ENVIRONMENT);
        }
        if (tags.contains(TAG_PROFILE_ENGINEERING_BRAND)) {
            positiveSignals.add(SIGNAL_ENGINEERING_CULTURE);
        }
        if (tags.contains(TAG_PROFILE_STRONG_WLB)) {
            positiveSignals.add(SIGNAL_WORK_LIFE_BALANCE);
        }
        if (tags.contains(TAG_PROFILE_STABLE_PUBLIC)) {
            positiveSignals.add(SIGNAL_STABILITY);
        }
        if (tags.contains(TAG_PROFILE_PRODUCT_LEADER)) {
            positiveSignals.add(SIGNAL_PRODUCT_COMPANY);
        }
    }

    private void detectCompanyProfileRiskSignals(
            Optional<CompanyConfig> trackedCompany,
            Set<String> riskSignals
    ) {
        if (trackedCompany.isEmpty()) {
            return;
        }
        Set<String> tags = normalizeSet(trackedCompany.get().riskTags());
        if (tags.contains(TAG_RISK_LANGUAGE_FRICTION_HIGH)) {
            riskSignals.add(SIGNAL_LANGUAGE_FRICTION);
        }
        if (tags.contains(TAG_RISK_OVERTIME)) {
            riskSignals.add(SIGNAL_OVERTIME_RISK);
        }
    }

    private boolean isOnsiteOnly(String remotePolicy) {
        if (containsAny(remotePolicy, REMOTE_OR_HYBRID_KEYWORDS)) {
            return false;
        }
        return containsAny(remotePolicy, ONSITE_ONLY_KEYWORDS);
    }

    private boolean isOnsiteBias(String remotePolicy) {
        if (isOnsiteOnly(remotePolicy)) {
            return false;
        }
        return remotePolicy.contains("onsite") || remotePolicy.contains("office");
    }

    private boolean isSalaryMissing(String salaryRange) {
        return salaryRange.isBlank() || containsAny(salaryRange, SALARY_MISSING_KEYWORDS);
    }

    private boolean isSalaryTransparent(String salaryRange) {
        if (isSalaryMissing(salaryRange)) {
            return false;
        }
        return salaryRange.chars().anyMatch(Character::isDigit);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private int countKeywordMatches(String text, List<String> keywords) {
        int matches = 0;
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                matches++;
            }
        }
        return matches;
    }

    private boolean matchesAnyPattern(String text, List<Pattern> patterns) {
        if (text.isBlank()) {
            return false;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(String text, String keyword) {
        if (text.isBlank() || keyword.isBlank()) {
            return false;
        }
        if (shouldUseLiteralContains(keyword)) {
            return text.contains(keyword);
        }
        String pattern = "\\b" + Pattern.quote(keyword) + "\\b";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private boolean shouldUseLiteralContains(String keyword) {
        if (keyword.contains(" ") || keyword.contains("-") || keyword.contains("/")) {
            return true;
        }
        return keyword.chars().anyMatch(c -> c > 0x7f);
    }

    private Set<String> normalizeSet(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String entry = normalize(value);
            if (!entry.isBlank()) {
                normalized.add(entry);
            }
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildCompanyContextText(Optional<CompanyConfig> trackedCompany) {
        if (trackedCompany.isEmpty()) {
            return "";
        }
        CompanyConfig company = trackedCompany.get();
        StringBuilder sb = new StringBuilder();
        sb.append(company.notes()).append(" ");
        sb.append(company.typeHint()).append(" ");
        for (String tag : company.profileTags()) {
            sb.append(tag).append(" ");
        }
        for (String tag : company.riskTags()) {
            sb.append(tag).append(" ");
        }
        sb.append(buildCorporateUrlContext(company.corporateUrl()));
        return sb.toString();
    }

    private String buildCorporateUrlContext(String corporateUrl) {
        if (corporateUrl == null || corporateUrl.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(corporateUrl).append(" ");
        if (hasCorporateEnglishHint(corporateUrl)) {
            sb.append("english site international team ");
        }
        if (hasCorporateJapaneseHint(corporateUrl)) {
            sb.append("japanese language site ");
        }
        try {
            URI uri = new URI(corporateUrl);
            if (uri.getHost() != null && uri.getHost().toLowerCase(Locale.ROOT).endsWith(".co.jp")) {
                sb.append("japan company context ");
            }
        } catch (URISyntaxException ignored) {
        }
        return sb.toString();
    }

    private boolean hasCorporateEnglishHint(String corporateUrl) {
        String normalized = normalize(corporateUrl);
        return normalized.contains("/en/")
                || normalized.endsWith("/en")
                || normalized.contains("locale=en")
                || normalized.contains("/global");
    }

    private boolean hasCorporateJapaneseHint(String corporateUrl) {
        String normalized = normalize(corporateUrl);
        return normalized.contains("/jp/")
                || normalized.endsWith("/jp")
                || normalized.contains("locale=jp")
                || normalized.contains(".co.jp");
    }

    private record ScoreRange(int min, int max) {
    }
}
