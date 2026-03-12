package com.pmfb.gonogo.engine.decision;

import com.pmfb.gonogo.engine.config.BlacklistedCompanyConfig;
import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class DecisionEngineV1 {
    private final MarketSignalDetector marketSignalDetector;
    private static final int POSITIVE_PRIORITY_WEIGHT = 2;
    private static final int POSITIVE_DEFAULT_WEIGHT = 1;
    private static final int RISK_PRIORITY_WEIGHT = 3;
    private static final int RISK_DEFAULT_WEIGHT = 2;
    private static final int VERDICT_GO_MIN_RAW_SCORE = 8;
    private static final int VERDICT_GO_WITH_CAUTION_MIN_RAW_SCORE = 1;
    private static final int NORMALIZED_SCORE_MIN = 0;
    private static final int NORMALIZED_SCORE_MAX = 100;
    private static final int NORMALIZED_SCORE_NEUTRAL = 50;
    private static final int HARD_FILTER_SCORE_CAP = 20;

    private static final int LANGUAGE_INDEX_REQUIRED_BASE = 75;
    private static final int LANGUAGE_INDEX_OPTIONAL_BASE = 5;
    private static final int LANGUAGE_INDEX_SOFT_BASE = 45;
    private static final int LANGUAGE_INDEX_MEDIUM_HIGH = 85;
    private static final int LANGUAGE_INDEX_HIGH = 95;
    private static final int LANGUAGE_INDEX_CRITICAL = 100;
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
    private static final int MANAGER_SCOPE_SALARY_MISALIGNED_MAX_YEN = 9_000_000;
    private static final int WORKLOAD_OVERLOAD_SIGNAL_THRESHOLD = 2;

    private static final List<String> SALARY_MISSING_KEYWORDS = List.of(
            "tbd",
            "to be discussed",
            "not disclosed",
            "not specified",
            "not provided",
            "no salary range given",
            "salary range not given",
            "salary not disclosed",
            "n/a",
            "na",
            "negotiable",
            "competitive"
    );

    public DecisionEngineV1() {
        this(new MarketSignalDetector());
    }

    DecisionEngineV1(MarketSignalDetector marketSignalDetector) {
        this.marketSignalDetector = Objects.requireNonNull(marketSignalDetector, "marketSignalDetector");
    }

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
            "with overtime",
            "overtime",
            "fixed overtime",
            "fast-paced",
            "high-pressure",
            "tight deadlines",
            "deadlines are tight",
            "occasional weekend work"
    );
    private static final List<String> WORKLOAD_NO_FIXED_HOURS_KEYWORDS = List.of(
            "no set working hours",
            "no fixed working hours",
            "no fixed hours",
            "results over time clocks",
            "flexible working hours"
    );
    private static final List<String> WORKLOAD_MIN_WEEKLY_COMMITMENT_KEYWORDS = List.of(
            "minimum commitment of 40 hours per week",
            "minimum 40-hour workweek",
            "minimum 40 hour workweek",
            "minimum 40 hours per week",
            "40-hour workweek",
            "40 hour workweek"
    );
    private static final List<String> WORKLOAD_PRESSURE_KEYWORDS = List.of(
            "work hard and play hard",
            "work hard, play hard",
            "work hard play hard",
            "with overtime",
            "overtime"
    );
    private static final List<String> HOLIDAY_POLICY_RISK_KEYWORDS = List.of(
            "national holidays correspond to working days",
            "national holidays are working days",
            "public holidays are working days",
            "holidays correspond to working days"
    );
    private static final List<String> LOCATION_MOBILITY_RISK_KEYWORDS = List.of(
            "location may be changed",
            "including overseas",
            "transfer may be required",
            "relocation may be required",
            "as determined by the company"
    );
    private static final List<String> LOCATION_CHANGE_BY_COMPANY_KEYWORDS = List.of(
            "location may be changed",
            "transfer may be required",
            "relocation may be required",
            "as determined by the company"
    );
    private static final List<String> LOCATION_OVERSEAS_TRANSFER_KEYWORDS = List.of(
            "including overseas",
            "overseas transfer",
            "overseas assignment"
    );
    private static final List<String> DEBT_FIRST_CULTURE_RISK_KEYWORDS = List.of(
            "strategically accumulate technical debt",
            "technical debt if it means faster growth",
            "only after reaching product market fit will we seek to optimize",
            "fail proof our codebase",
            "moving fast should be a startup's primary focus"
    );
    private static final List<String> HYPERGROWTH_EXECUTION_RISK_KEYWORDS = List.of(
            "increased revenue 10x over the past year",
            "plan to do the same again over the coming 12 months",
            "10x over the past year",
            "10x growth"
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
    private static final List<String> JAPANESE_INTERNAL_ONLY_KEYWORDS = List.of(
            "all internal communications",
            "all internal communication",
            "internal communications",
            "internal communication",
            "all documents",
            "customer support will be in japanese",
            "社内コミュニケーション",
            "社内文書",
            "社内資料",
            "日本語での対応"
    );
    private static final List<String> FOREIGN_WELCOME_KEYWORDS = List.of(
            "welcome foreign workers",
            "foreign workers welcome",
            "welcome international applicants",
            "international applicants welcome",
            "foreign nationals welcome",
            "visa sponsorship",
            "外国人歓迎",
            "海外人材歓迎"
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
            "n2 level or above",
            "n2 or above",
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
    private static final List<String> INHOUSE_PRODUCT_ENGINEERING_KEYWORDS = List.of(
            "in-house development",
            "in house development",
            "end-to-end in-house development",
            "end to end in-house development",
            "client-server-3rd party system design"
    );
    private static final List<String> GLOBAL_TEAM_COLLABORATION_KEYWORDS = List.of(
            "multinational members",
            "diverse backgrounds",
            "full-remote team",
            "full remote team",
            "global team",
            "international team"
    );
    private static final List<String> ENGLISH_SUPPORT_ENVIRONMENT_KEYWORDS = List.of(
            "english is used on a daily basis",
            "english used on a daily basis",
            "fluency in english is not required",
            "support is provided"
    );
    private static final List<String> VISA_SPONSORSHIP_SUPPORT_KEYWORDS = List.of(
            "visa sponsorship",
            "japan visa sponsorship available",
            "sponsorship available",
            "work visa support"
    );
    private static final List<Pattern> LANGUAGE_REQUIRED_PATTERNS = List.of(
            Pattern.compile("(?i)\\bjapanese\\b.{0,24}\\b(must|required for this role|mandatory)\\b"),
            Pattern.compile("(?i)\\b(must|mandatory)\\b.{0,24}\\bjapanese\\b"),
            Pattern.compile("(?i)\\bjlpt\\s*n[12]\\b"),
            Pattern.compile("(?i)\\bn[12]\\s*(level\\s*or\\s*above|or\\s*above|\\+)\\b"),
            Pattern.compile("(?i)\\bn[12]\\b.{0,24}\\bjapanese\\b"),
            Pattern.compile("日本語.*必須"),
            Pattern.compile("必須.*日本語"),
            Pattern.compile("日本語能力試験\\s*n[12N１２]")
    );
    private static final List<Pattern> LANGUAGE_OPTIONAL_PATTERNS = List.of(
            Pattern.compile("(?i)\\bjapanese\\b.{0,24}\\b(optional|preferred|plus|nice to have|welcome)\\b"),
            Pattern.compile("(?i)\\b(no japanese required|japanese not required|english only)\\b"),
            Pattern.compile("日本語.*(不問|歓迎|必須ではありません)")
    );
    private static final List<Pattern> JAPANESE_INTERNAL_ONLY_PATTERNS = List.of(
            Pattern.compile("(?i)all\\s+internal\\s+communications?.{0,60}\\bjapanese\\b"),
            Pattern.compile("(?i)all\\s+internal\\s+documents?.{0,60}\\bjapanese\\b"),
            Pattern.compile("(?i)customer\\s+support.{0,60}\\bjapanese\\b"),
            Pattern.compile("(?i)all\\s+internal\\s+communications?,\\s+documents?,\\s+and\\s+customer\\s+support\\s+will\\s+be\\s+in\\s+japanese"),
            Pattern.compile("日本語.*(社内|社外).*必須")
    );
    private static final List<String> ROLE_MANAGER_SCOPE_KEYWORDS = List.of(
            "engineering manager",
            "people management",
            "management of each team member",
            "mentoring and coaching",
            "1-on-1",
            "goal setting and evaluation",
            "engineering organization",
            "organization performance",
            "tech lead",
            "team management",
            "line management"
    );
    private static final List<String> ROLE_MANAGER_TITLE_KEYWORDS = List.of(
            "manager",
            "lead",
            "head",
            "director",
            "vp"
    );
    private static final List<String> ROLE_IC_TITLE_KEYWORDS = List.of(
            "engineer",
            "developer",
            "programmer"
    );
    private static final List<String> ROLE_SOFTWARE_KEYWORDS = List.of(
            "software",
            "backend",
            "front",
            "fullstack",
            "engineer",
            "developer",
            "programmer",
            "java",
            "kotlin",
            "python",
            "typescript",
            "go"
    );
    private static final List<String> ROLE_NON_SOFTWARE_MISMATCH_KEYWORDS = List.of(
            "physicist",
            "physics",
            "chemist",
            "biologist",
            "attorney",
            "lawyer",
            "nurse",
            "doctor",
            "talent network",
            "talent pool"
    );
    private static final Pattern POSITION_LABEL_PATTERN =
            Pattern.compile("(?is)\\bposition\\s*:\\s*([^\\n\\r|.;]{2,120})");
    private static final List<String> INTERMEDIARY_CONTRACT_KEYWORDS = List.of(
            "one of our clients",
            "project basis",
            "independent contractor",
            "services rendered",
            "talent network",
            "talent pool",
            "contract and payment terms"
    );
    private static final List<String> APPLICATION_PROCESS_TEMPLATE_KEYWORDS = List.of(
            "application process",
            "upload resume",
            "profile verification",
            "screening process",
            "skills questionnaire",
            "time-boxed exercise",
            "selected candidates will be contacted"
    );
    private static final int INTERMEDIARY_KEYWORD_HIT_MIN = 2;
    private static final int APPLICATION_TEMPLATE_HIT_MIN = 3;
    private static final Pattern GENERIC_DESIRED_SKILLS_ENGINEER_PATTERN =
            Pattern.compile("(?is)desired\\s+skills\\s+and\\s+experience\\s*[:\\-\\n\\r]*\\s*engineer\\b");
    private static final List<String> PRE_IPO_RISK_KEYWORDS = List.of(
            "pre-ipo",
            "pre ipo",
            "preparing for ipo",
            "ipo preparation",
            "aiming for ipo",
            "phase of preparing for ipo",
            "listing on the stock exchange"
    );
    private static final Pattern SALARY_MILLION_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(m|million)\\b");
    private static final Pattern SALARY_NUMBER_PATTERN = Pattern.compile("(\\d{1,3}(?:[,.]\\d{3})+|\\d{6,9})");
    private static final Pattern SALARY_ORDERED_TOKEN_PATTERN =
            Pattern.compile("(?i)(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(m|million|k)?");
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
    private static final String HARD_NO_JAPANESE_ONLY_ENVIRONMENT = "japanese_only_environment";
    private static final String HARD_NO_WORKLOAD_OVERLOAD = "workload_overload";
    private static final String HARD_NO_FORCED_RELOCATION = "forced_relocation";

    private static final String SIGNAL_SALARY_TRANSPARENCY = "salary_transparency";
    private static final String SIGNAL_HYBRID_WORK = "hybrid_work";
    private static final String SIGNAL_REMOTE_FRIENDLY = "remote_friendly";
    private static final String SIGNAL_ENGLISH_ENVIRONMENT = "english_environment";
    private static final String SIGNAL_PRODUCT_COMPANY = "product_company";
    private static final String SIGNAL_ENGINEERING_CULTURE = "engineering_culture";
    private static final String SIGNAL_ENGINEERING_ENVIRONMENT = "engineering_environment";
    private static final String SIGNAL_INHOUSE_PRODUCT_ENGINEERING = "inhouse_product_engineering";
    private static final String SIGNAL_GLOBAL_TEAM_COLLABORATION = "global_team_collaboration";
    private static final String SIGNAL_ENGLISH_SUPPORT_ENVIRONMENT = "english_support_environment";
    private static final String SIGNAL_VISA_SPONSORSHIP_SUPPORT = "visa_sponsorship_support";
    private static final String SIGNAL_WORK_LIFE_BALANCE = "work_life_balance";
    private static final String SIGNAL_STABILITY = "stability";
    private static final String SIGNAL_COMPANY_REPUTATION_POSITIVE = "company_reputation_positive";
    private static final String SIGNAL_COMPANY_REPUTATION_POSITIVE_STRONG = "company_reputation_positive_strong";
    private static final String SIGNAL_CANDIDATE_STACK_FIT = "candidate_stack_fit";
    private static final String SIGNAL_CANDIDATE_DOMAIN_FIT = "candidate_domain_fit";
    private static final String SIGNAL_CANDIDATE_SENIORITY_FIT = "candidate_seniority_fit";
    private static final String SIGNAL_PRODUCT_PM_COLLABORATION = "product_pm_collaboration";
    private static final String SIGNAL_ENGINEERING_MATURITY = "engineering_maturity";
    private static final String SIGNAL_CASUAL_INTERVIEW = "casual_interview";
    private static final String SIGNAL_ASYNC_COMMUNICATION = "async_communication";
    private static final String SIGNAL_REAL_FLEXTIME = "real_flextime";
    private static final String SIGNAL_LOW_OVERTIME_DISCLOSED = "low_overtime_disclosed";

    private static final String SIGNAL_SALARY_LOW_CONFIDENCE = "salary_low_confidence";
    private static final String SIGNAL_SALARY_BELOW_PERSONA_FLOOR = "salary_below_persona_floor";
    private static final String SIGNAL_ONSITE_BIAS = "onsite_bias";
    private static final String SIGNAL_LANGUAGE_FRICTION = "language_friction";
    private static final String SIGNAL_LANGUAGE_FRICTION_CRITICAL = "language_friction_critical";
    private static final String SIGNAL_CONSULTING_RISK = "consulting_risk";
    private static final String SIGNAL_OVERTIME_RISK = "overtime_risk";
    private static final String SIGNAL_ENGINEERING_ENVIRONMENT_RISK = "engineering_environment_risk";
    private static final String SIGNAL_STARTUP_RISK = "startup_risk";
    private static final String SIGNAL_ROLE_MISMATCH_MANAGER_VS_IC_TITLE = "role_mismatch_manager_vs_ic_title";
    private static final String SIGNAL_ROLE_IDENTITY_MISMATCH = "role_identity_mismatch";
    private static final String SIGNAL_INTERMEDIARY_CONTRACT_RISK = "intermediary_contract_risk";
    private static final String SIGNAL_INCLUSION_CONTRADICTION = "inclusion_contradiction";
    private static final String SIGNAL_PRE_IPO_RISK = "pre_ipo_risk";
    private static final String SIGNAL_MANAGER_SCOPE_SALARY_MISALIGNED = "manager_scope_salary_misaligned";
    private static final String SIGNAL_WORKLOAD_POLICY_RISK = "workload_policy_risk";
    private static final String SIGNAL_HOLIDAY_POLICY_RISK = "holiday_policy_risk";
    private static final String SIGNAL_LOCATION_MOBILITY_RISK = "location_mobility_risk";
    private static final String SIGNAL_SALARY_RANGE_ANOMALY = "salary_range_anomaly";
    private static final String SIGNAL_DEBT_FIRST_CULTURE_RISK = "debt_first_culture_risk";
    private static final String SIGNAL_HYPERGROWTH_EXECUTION_RISK = "hypergrowth_execution_risk";
    private static final String SIGNAL_COMPANY_REPUTATION_RISK = "company_reputation_risk";
    private static final String SIGNAL_COMPANY_REPUTATION_RISK_HIGH = "company_reputation_risk_high";
    private static final String SIGNAL_CANDIDATE_STACK_GAP = "candidate_stack_gap";
    private static final String SIGNAL_CANDIDATE_DOMAIN_GAP = "candidate_domain_gap";
    private static final String SIGNAL_CANDIDATE_SENIORITY_MISMATCH = "candidate_seniority_mismatch";
    private static final String SIGNAL_ALGORITHMIC_INTERVIEW_RISK = "algorithmic_interview_risk";
    private static final String SIGNAL_PRESSURE_CULTURE_RISK = "pressure_culture_risk";
    private static final String SIGNAL_FAKE_FLEXTIME_RISK = "fake_flextime_risk";
    private static final String SIGNAL_TRADITIONAL_CORPORATE_PROCESS_RISK = "traditional_corporate_process_risk";
    private static final String SIGNAL_CUSTOMER_SITE_RISK = "customer_site_risk";
    private static final int EXTRA_RISK_PENALTY_CRITICAL_LANGUAGE = 4;
    private static final int EXTRA_RISK_PENALTY_ROLE_MISMATCH = 2;
    private static final int EXTRA_RISK_PENALTY_ROLE_IDENTITY_MISMATCH = 3;
    private static final int EXTRA_RISK_PENALTY_INTERMEDIARY_CONTRACT = 2;
    private static final int EXTRA_RISK_PENALTY_INCLUSION_CONTRADICTION = 2;
    private static final int EXTRA_RISK_PENALTY_PRE_IPO = 1;
    private static final int EXTRA_RISK_PENALTY_MANAGER_SCOPE_SALARY = 2;
    private static final int EXTRA_RISK_PENALTY_WORKLOAD_POLICY = 3;
    private static final int EXTRA_RISK_PENALTY_HOLIDAY_POLICY = 2;
    private static final int EXTRA_RISK_PENALTY_LOCATION_MOBILITY = 1;
    private static final int EXTRA_RISK_PENALTY_SALARY_RANGE_ANOMALY = 3;
    private static final int EXTRA_RISK_PENALTY_DEBT_FIRST_CULTURE = 3;
    private static final int EXTRA_RISK_PENALTY_HYPERGROWTH_EXECUTION = 2;
    private static final int EXTRA_RISK_PENALTY_ONSITE_BIAS = 2;
    private static final int EXTRA_RISK_PENALTY_OVERTIME_RISK = 2;
    private static final int EXTRA_RISK_PENALTY_CANDIDATE_STACK_GAP = 2;
    private static final int EXTRA_RISK_PENALTY_CANDIDATE_SENIORITY_MISMATCH = 2;

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
    private static final List<String> CANDIDATE_REQUIREMENT_SECTION_KEYWORDS = List.of(
            "requirements",
            "required skills",
            "must-have",
            "must have",
            "mandatory",
            "must-have skills",
            "must have skills"
    );
    private static final List<String> CANDIDATE_NON_REQUIREMENT_SECTION_KEYWORDS = List.of(
            "responsibilities",
            "job contents",
            "team culture",
            "working conditions",
            "selection process",
            "benefits",
            "nice to have",
            "preferred",
            "bonus"
    );
    private static final List<String> CANDIDATE_REQUIREMENT_LINE_HINTS = List.of(
            "experience with",
            "hands-on experience with",
            "proficiency in",
            "strong background in",
            "knowledge of",
            "expertise in"
    );
    private static final List<String> CANDIDATE_ALTERNATIVE_LINE_HINTS = List.of(
            "at least one",
            "e.g.",
            "e.g,",
            "for example",
            "such as"
    );
    private static final Pattern YEARS_EXPERIENCE_PATTERN =
            Pattern.compile("(?i)(\\d+)\\+?\\s+years?(?: of)?\\s+(?:hands-on )?(?:experience|exp)");
    private static final List<Pattern> JUNIOR_MID_TITLE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(junior|jr\\.?|entry[- ]level|graduate|new grad)\\b"),
            Pattern.compile("(?i)\\bmid(?:[- ]level)?\\b")
    );
    private static final List<String> SENIOR_ROLE_KEYWORDS = List.of(
            "senior",
            "staff",
            "lead",
            "principal",
            "architect"
    );
    private static final Map<String, List<String>> CANDIDATE_SKILL_KEYWORDS = Map.ofEntries(
            Map.entry("java", List.of("java")),
            Map.entry("spring boot", List.of("spring boot")),
            Map.entry("spring framework", List.of("spring framework", "spring")),
            Map.entry("sap hybris", List.of("sap hybris", "hybris")),
            Map.entry("sap commerce cloud", List.of("sap commerce cloud", "sap commerce")),
            Map.entry("rest api", List.of("rest api", "rest apis", "restful api", "restful apis")),
            Map.entry("flutter", List.of("flutter")),
            Map.entry("dart", List.of("dart")),
            Map.entry("typescript", List.of("typescript")),
            Map.entry("react", List.of("react")),
            Map.entry("mongodb", List.of("mongodb")),
            Map.entry("sql", List.of("sql", "relational", "mysql", "postgresql")),
            Map.entry("h2", List.of("h2")),
            Map.entry("stripe", List.of("stripe")),
            Map.entry("shopify", List.of("shopify")),
            Map.entry("aws", List.of("aws")),
            Map.entry("kotlin", List.of("kotlin")),
            Map.entry("kafka", List.of("kafka")),
            Map.entry("redis", List.of("redis")),
            Map.entry("android", List.of("android", "jetpack")),
            Map.entry("kmp", List.of("kmp", "kotlin multiplatform")),
            Map.entry("riverpod", List.of("riverpod")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s")),
            Map.entry("go", List.of("go", "golang")),
            Map.entry("scala", List.of("scala")),
            Map.entry("rust", List.of("rust")),
            Map.entry("c#", List.of("c#")),
            Map.entry("php", List.of("php")),
            Map.entry("laravel", List.of("laravel")),
            Map.entry("python", List.of("python")),
            Map.entry("docker", List.of("docker")),
            Map.entry("jpa", List.of("jpa")),
            Map.entry("nosql", List.of("nosql")),
            Map.entry("gcp", List.of("gcp")),
            Map.entry("azure", List.of("azure"))
    );
    private static final Map<String, List<String>> CANDIDATE_DOMAIN_KEYWORDS = Map.ofEntries(
            Map.entry("ecommerce_platforms", List.of("ecommerce", "e-commerce", "commerce", "checkout", "cart", "retail")),
            Map.entry("commerce_performance", List.of("performance", "scalability", "reliability", "high availability", "load")),
            Map.entry("payment_integrations", List.of("payment", "payments", "stripe")),
            Map.entry("omnichannel_retail", List.of("omnichannel", "inventory", "order management", "oms", "retail")),
            Map.entry("mobile_cross_platform", List.of("flutter", "dart", "mobile")),
            Map.entry("enterprise_java", List.of("java", "spring", "jpa", "hibernate")),
            Map.entry("distributed_teams", List.of("international", "global team", "multicultural", "distributed")),
            Map.entry("event_driven_architecture", List.of("event-driven", "event driven", "kafka", "messaging", "pub/sub")),
            Map.entry("system_design", List.of("system design", "architecture", "scalability", "reliability")),
            Map.entry("frontend_fullstack", List.of("react", "typescript", "frontend", "full stack", "full-stack")),
            Map.entry("cloud_basics", List.of("aws", "gcp", "azure", "cloud")),
            Map.entry("data_pipelines", List.of("data pipeline", "streaming", "cdc", "kafka streams", "etl")),
            Map.entry("infrastructure_as_code", List.of("terraform", "cdk", "cloudformation", "infrastructure as code", "iac")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s"))
    );

    private static final Map<String, String> POSITIVE_SIGNAL_TO_PRIORITY = Map.ofEntries(
            Map.entry(SIGNAL_SALARY_TRANSPARENCY, PRIORITY_SALARY),
            Map.entry(SIGNAL_HYBRID_WORK, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_REMOTE_FRIENDLY, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_ENGLISH_ENVIRONMENT, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_PRODUCT_COMPANY, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_ENGINEERING_CULTURE, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_ENGINEERING_ENVIRONMENT, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_INHOUSE_PRODUCT_ENGINEERING, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_GLOBAL_TEAM_COLLABORATION, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_ENGLISH_SUPPORT_ENVIRONMENT, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_VISA_SPONSORSHIP_SUPPORT, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_WORK_LIFE_BALANCE, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_STABILITY, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_POSITIVE, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_POSITIVE_STRONG, PRIORITY_STABILITY),
            Map.entry(SIGNAL_CANDIDATE_STACK_FIT, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_CANDIDATE_DOMAIN_FIT, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_CANDIDATE_SENIORITY_FIT, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_PRODUCT_PM_COLLABORATION, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_ENGINEERING_MATURITY, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_CASUAL_INTERVIEW, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_ASYNC_COMMUNICATION, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_REAL_FLEXTIME, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_LOW_OVERTIME_DISCLOSED, PRIORITY_WORK_LIFE_BALANCE)
    );
    private static final Map<String, String> RISK_SIGNAL_TO_PRIORITY = Map.ofEntries(
            Map.entry(SIGNAL_SALARY_LOW_CONFIDENCE, PRIORITY_SALARY),
            Map.entry(SIGNAL_SALARY_BELOW_PERSONA_FLOOR, PRIORITY_SALARY),
            Map.entry(SIGNAL_ONSITE_BIAS, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_LANGUAGE_FRICTION, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_LANGUAGE_FRICTION_CRITICAL, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_CONSULTING_RISK, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_OVERTIME_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_ENGINEERING_ENVIRONMENT_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_ROLE_MISMATCH_MANAGER_VS_IC_TITLE, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_ROLE_IDENTITY_MISMATCH, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_INTERMEDIARY_CONTRACT_RISK, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_INCLUSION_CONTRADICTION, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_PRE_IPO_RISK, PRIORITY_STABILITY),
            Map.entry(SIGNAL_MANAGER_SCOPE_SALARY_MISALIGNED, PRIORITY_SALARY),
            Map.entry(SIGNAL_WORKLOAD_POLICY_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_HOLIDAY_POLICY_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_LOCATION_MOBILITY_RISK, PRIORITY_HYBRID_WORK),
            Map.entry(SIGNAL_SALARY_RANGE_ANOMALY, PRIORITY_SALARY),
            Map.entry(SIGNAL_DEBT_FIRST_CULTURE_RISK, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_HYPERGROWTH_EXECUTION_RISK, PRIORITY_STABILITY),
            Map.entry(SIGNAL_STARTUP_RISK, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_RISK, PRIORITY_STABILITY),
            Map.entry(SIGNAL_COMPANY_REPUTATION_RISK_HIGH, PRIORITY_STABILITY),
            Map.entry(SIGNAL_CANDIDATE_STACK_GAP, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_CANDIDATE_DOMAIN_GAP, PRIORITY_PRODUCT_COMPANY),
            Map.entry(SIGNAL_CANDIDATE_SENIORITY_MISMATCH, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_ALGORITHMIC_INTERVIEW_RISK, PRIORITY_ENGINEERING_CULTURE),
            Map.entry(SIGNAL_PRESSURE_CULTURE_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_FAKE_FLEXTIME_RISK, PRIORITY_WORK_LIFE_BALANCE),
            Map.entry(SIGNAL_TRADITIONAL_CORPORATE_PROCESS_RISK, PRIORITY_ENGLISH_ENVIRONMENT),
            Map.entry(SIGNAL_CUSTOMER_SITE_RISK, PRIORITY_PRODUCT_COMPANY)
    );
    private static final Map<String, Integer> RISK_SIGNAL_EXTRA_PENALTY = Map.ofEntries(
            Map.entry(SIGNAL_LANGUAGE_FRICTION_CRITICAL, EXTRA_RISK_PENALTY_CRITICAL_LANGUAGE),
            Map.entry(SIGNAL_ROLE_MISMATCH_MANAGER_VS_IC_TITLE, EXTRA_RISK_PENALTY_ROLE_MISMATCH),
            Map.entry(SIGNAL_ROLE_IDENTITY_MISMATCH, EXTRA_RISK_PENALTY_ROLE_IDENTITY_MISMATCH),
            Map.entry(SIGNAL_INTERMEDIARY_CONTRACT_RISK, EXTRA_RISK_PENALTY_INTERMEDIARY_CONTRACT),
            Map.entry(SIGNAL_INCLUSION_CONTRADICTION, EXTRA_RISK_PENALTY_INCLUSION_CONTRADICTION),
            Map.entry(SIGNAL_PRE_IPO_RISK, EXTRA_RISK_PENALTY_PRE_IPO),
            Map.entry(SIGNAL_MANAGER_SCOPE_SALARY_MISALIGNED, EXTRA_RISK_PENALTY_MANAGER_SCOPE_SALARY),
            Map.entry(SIGNAL_WORKLOAD_POLICY_RISK, EXTRA_RISK_PENALTY_WORKLOAD_POLICY),
            Map.entry(SIGNAL_HOLIDAY_POLICY_RISK, EXTRA_RISK_PENALTY_HOLIDAY_POLICY),
            Map.entry(SIGNAL_LOCATION_MOBILITY_RISK, EXTRA_RISK_PENALTY_LOCATION_MOBILITY),
            Map.entry(SIGNAL_SALARY_RANGE_ANOMALY, EXTRA_RISK_PENALTY_SALARY_RANGE_ANOMALY),
            Map.entry(SIGNAL_DEBT_FIRST_CULTURE_RISK, EXTRA_RISK_PENALTY_DEBT_FIRST_CULTURE),
            Map.entry(SIGNAL_HYPERGROWTH_EXECUTION_RISK, EXTRA_RISK_PENALTY_HYPERGROWTH_EXECUTION),
            Map.entry(SIGNAL_ONSITE_BIAS, EXTRA_RISK_PENALTY_ONSITE_BIAS),
            Map.entry(SIGNAL_OVERTIME_RISK, EXTRA_RISK_PENALTY_OVERTIME_RISK),
            Map.entry(SIGNAL_CANDIDATE_STACK_GAP, EXTRA_RISK_PENALTY_CANDIDATE_STACK_GAP),
            Map.entry(SIGNAL_CANDIDATE_SENIORITY_MISMATCH, EXTRA_RISK_PENALTY_CANDIDATE_SENIORITY_MISMATCH)
    );

    public EvaluationResult evaluate(JobInput job, PersonaConfig persona, EngineConfig config) {
        return evaluate(job, persona, null, config, "");
    }

    public EvaluationResult evaluate(
            JobInput job,
            PersonaConfig persona,
            CandidateProfileConfig candidateProfile,
            EngineConfig config
    ) {
        return evaluate(job, persona, candidateProfile, config, "");
    }

    public EvaluationResult evaluate(
            JobInput job,
            PersonaConfig persona,
            EngineConfig config,
            String externalContext
    ) {
        return evaluate(job, persona, null, config, externalContext);
    }

    public EvaluationResult evaluate(
            JobInput job,
            PersonaConfig persona,
            CandidateProfileConfig candidateProfile,
            EngineConfig config,
            String externalContext
    ) {
        LinkedHashSet<String> positiveSignals = new LinkedHashSet<>();
        LinkedHashSet<String> riskSignals = new LinkedHashSet<>();
        List<String> hardRejectReasons = new ArrayList<>();

        String jobText = normalize(job.companyName() + " " + job.title() + " " + job.description());
        Optional<CompanyConfig> trackedCompany = findTrackedCompany(job.companyName(), config.companies());
        String companyContextText = buildCompanyContextText(trackedCompany);
        String combinedText = normalize(jobText + " " + companyContextText + " " + normalize(externalContext));
        String remotePolicy = normalize(job.remotePolicy());
        String salaryRange = normalize(job.salaryRange());
        Set<String> personaHardNo = normalizeSet(persona.hardNo());
        Set<String> personaPriorities = normalizeSet(persona.priorities());

        int languageFrictionIndex = computeLanguageFrictionIndex(combinedText, trackedCompany);
        int companyReputationIndex = computeCompanyReputationIndex(combinedText, trackedCompany);

        detectPositiveSignals(job, combinedText, remotePolicy, salaryRange, trackedCompany, positiveSignals);
        detectRiskSignals(
                job,
                combinedText,
                remotePolicy,
                salaryRange,
                trackedCompany,
                config.blacklistedCompanies(),
                persona,
                riskSignals
        );
        detectMarketSignals(combinedText, positiveSignals, riskSignals);
        detectCandidateProfileSignals(job, combinedText, candidateProfile, positiveSignals, riskSignals);
        detectReputationSignals(companyReputationIndex, positiveSignals, riskSignals);
        detectHardFilters(
                job,
                combinedText,
                remotePolicy,
                salaryRange,
                personaPriorities,
                personaHardNo,
                config.blacklistedCompanies(),
                hardRejectReasons
        );

        Map<String, Integer> signalWeights = persona.signalWeights();
        int rawScore = computeScore(positiveSignals, riskSignals, personaPriorities, signalWeights);
        ScoreRange scoreRange = computeScoreRange(personaPriorities, signalWeights);
        int normalizedScore = normalizeScore(rawScore, scoreRange);
        Verdict verdict = decideVerdict(rawScore, hardRejectReasons);
        if (!hardRejectReasons.isEmpty()) {
            normalizedScore = Math.min(normalizedScore, HARD_FILTER_SCORE_CAP);
        }

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
        if (containsAny(combinedText, INHOUSE_PRODUCT_ENGINEERING_KEYWORDS)) {
            positiveSignals.add(SIGNAL_INHOUSE_PRODUCT_ENGINEERING);
        }
        if (containsAny(combinedText, GLOBAL_TEAM_COLLABORATION_KEYWORDS)) {
            positiveSignals.add(SIGNAL_GLOBAL_TEAM_COLLABORATION);
        }
        if (containsAny(combinedText, ENGLISH_SUPPORT_ENVIRONMENT_KEYWORDS)) {
            positiveSignals.add(SIGNAL_ENGLISH_SUPPORT_ENVIRONMENT);
        }
        if (containsAny(combinedText, VISA_SPONSORSHIP_SUPPORT_KEYWORDS)) {
            positiveSignals.add(SIGNAL_VISA_SPONSORSHIP_SUPPORT);
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
            JobInput job,
            String combinedText,
            String remotePolicy,
            String salaryRange,
            Optional<CompanyConfig> trackedCompany,
            List<BlacklistedCompanyConfig> blacklist,
            PersonaConfig persona,
            Set<String> riskSignals
    ) {
        if (isSalaryMissing(salaryRange)) {
            riskSignals.add(SIGNAL_SALARY_LOW_CONFIDENCE);
        }
        if (hasSalaryBelowPersonaFloor(
                job,
                combinedText,
                salaryRange,
                blacklist,
                persona.minimumSalaryYen()
        )) {
            riskSignals.add(SIGNAL_SALARY_BELOW_PERSONA_FLOOR);
        }
        if (isOnsiteBias(remotePolicy)) {
            riskSignals.add(SIGNAL_ONSITE_BIAS);
        }
        if (hasLanguageFrictionSignal(combinedText)) {
            riskSignals.add(SIGNAL_LANGUAGE_FRICTION);
        }
        if (hasCriticalLanguageFrictionSignal(combinedText)) {
            riskSignals.add(SIGNAL_LANGUAGE_FRICTION_CRITICAL);
        }
        if (containsAny(combinedText, OVERTIME_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_OVERTIME_RISK);
        }
        if (hasEngineeringEnvironmentRiskSignal(combinedText)) {
            riskSignals.add(SIGNAL_ENGINEERING_ENVIRONMENT_RISK);
        }
        if (hasRoleMismatchManagerVsIcTitle(job.title(), combinedText)) {
            riskSignals.add(SIGNAL_ROLE_MISMATCH_MANAGER_VS_IC_TITLE);
        }
        if (hasRoleIdentityMismatch(job.title(), job.description())) {
            riskSignals.add(SIGNAL_ROLE_IDENTITY_MISMATCH);
        }
        if (hasIntermediaryContractRisk(combinedText)) {
            riskSignals.add(SIGNAL_INTERMEDIARY_CONTRACT_RISK);
        }
        if (hasInclusionContradiction(combinedText)) {
            riskSignals.add(SIGNAL_INCLUSION_CONTRADICTION);
        }
        if (containsAny(combinedText, PRE_IPO_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_PRE_IPO_RISK);
        }
        if (hasManagerScopeSalaryMisaligned(job.title(), combinedText, salaryRange)) {
            riskSignals.add(SIGNAL_MANAGER_SCOPE_SALARY_MISALIGNED);
        }
        if (hasWorkloadPolicyRisk(combinedText)) {
            riskSignals.add(SIGNAL_WORKLOAD_POLICY_RISK);
        }
        if (containsAny(combinedText, HOLIDAY_POLICY_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_HOLIDAY_POLICY_RISK);
        }
        if (containsAny(combinedText, LOCATION_MOBILITY_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_LOCATION_MOBILITY_RISK);
        }
        if (hasSalaryRangeAnomaly(salaryRange)) {
            riskSignals.add(SIGNAL_SALARY_RANGE_ANOMALY);
        }
        if (containsAny(combinedText, DEBT_FIRST_CULTURE_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_DEBT_FIRST_CULTURE_RISK);
        }
        if (containsAny(combinedText, HYPERGROWTH_EXECUTION_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_HYPERGROWTH_EXECUTION_RISK);
        }
        if (containsAny(combinedText, STARTUP_RISK_KEYWORDS)) {
            riskSignals.add(SIGNAL_STARTUP_RISK);
        }
        if (isBlacklistedCompany(job.companyName(), blacklist)
                || containsAny(combinedText, CONSULTING_HARD_KEYWORDS)) {
            riskSignals.add(SIGNAL_CONSULTING_RISK);
        }
        detectCompanyProfileRiskSignals(trackedCompany, riskSignals);
    }

    private void detectMarketSignals(
            String combinedText,
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        MarketSignalDetector.SignalMatches matches = marketSignalDetector.detect(combinedText);
        if (matches.productPmCollaborationPositive()) {
            positiveSignals.add(SIGNAL_PRODUCT_PM_COLLABORATION);
        }
        if (matches.engineeringMaturityPositive()) {
            positiveSignals.add(SIGNAL_ENGINEERING_MATURITY);
        }
        if (matches.casualInterviewPositive()) {
            positiveSignals.add(SIGNAL_CASUAL_INTERVIEW);
        }
        if (matches.asyncCommunicationPositive()) {
            positiveSignals.add(SIGNAL_ASYNC_COMMUNICATION);
        }
        if (matches.realFlextimePositive()) {
            positiveSignals.add(SIGNAL_REAL_FLEXTIME);
        }
        if (matches.lowOvertimeDisclosedPositive()) {
            positiveSignals.add(SIGNAL_LOW_OVERTIME_DISCLOSED);
        }
        if (matches.algorithmicInterviewRisk()) {
            riskSignals.add(SIGNAL_ALGORITHMIC_INTERVIEW_RISK);
        }
        if (matches.pressureCultureRisk()) {
            riskSignals.add(SIGNAL_PRESSURE_CULTURE_RISK);
        }
        if (matches.fakeFlextimeRisk()) {
            riskSignals.add(SIGNAL_FAKE_FLEXTIME_RISK);
        }
        if (matches.traditionalCorporateProcessRisk()) {
            riskSignals.add(SIGNAL_TRADITIONAL_CORPORATE_PROCESS_RISK);
        }
        if (matches.customerSiteRisk()) {
            riskSignals.add(SIGNAL_CUSTOMER_SITE_RISK);
        }
    }

    private void detectCandidateProfileSignals(
            JobInput job,
            String combinedText,
            CandidateProfileConfig candidateProfile,
            Set<String> positiveSignals,
            Set<String> riskSignals
    ) {
        if (candidateProfile == null) {
            return;
        }

        if (hasCandidateStackFit(job, combinedText, candidateProfile)) {
            positiveSignals.add(SIGNAL_CANDIDATE_STACK_FIT);
        }
        if (hasCandidateDomainFit(combinedText, candidateProfile)) {
            positiveSignals.add(SIGNAL_CANDIDATE_DOMAIN_FIT);
        }
        if (hasCandidateSeniorityFit(job, combinedText, candidateProfile)) {
            positiveSignals.add(SIGNAL_CANDIDATE_SENIORITY_FIT);
        }
        if (hasCandidateStackGap(job, candidateProfile)) {
            riskSignals.add(SIGNAL_CANDIDATE_STACK_GAP);
        }
        if (hasCandidateDomainGap(combinedText, candidateProfile)) {
            riskSignals.add(SIGNAL_CANDIDATE_DOMAIN_GAP);
        }
        if (hasCandidateSeniorityMismatch(job, combinedText, candidateProfile)) {
            riskSignals.add(SIGNAL_CANDIDATE_SENIORITY_MISMATCH);
        }
    }

    private boolean hasCandidateStackFit(
            JobInput job,
            String combinedText,
            CandidateProfileConfig candidateProfile
    ) {
        Set<String> productionSkills = canonicalizeSkills(candidateProfile.productionSkills());
        int explicitRequiredMatches = countExplicitRequiredSkillMatches(job, productionSkills);
        int broadMatches = countCanonicalSkillMatches(combinedText, productionSkills);
        return explicitRequiredMatches >= 1 || broadMatches >= 2;
    }

    private boolean hasCandidateStackGap(JobInput job, CandidateProfileConfig candidateProfile) {
        Set<String> productionSkills = canonicalizeSkills(candidateProfile.productionSkills());
        Set<String> learningSkills = canonicalizeSkills(candidateProfile.learningSkills());
        for (String line : extractRequirementLines(job.description())) {
            Set<String> requiredSkills = extractMentionedTechs(line);
            if (requiredSkills.isEmpty()) {
                continue;
            }

            boolean productionMatch = containsAnyCanonicalSkill(requiredSkills, productionSkills);
            boolean learningMatch = containsAnyCanonicalSkill(requiredSkills, learningSkills);
            boolean alternativeLine = isAlternativeSkillLine(line);

            if (alternativeLine) {
                if (!productionMatch && learningMatch) {
                    return true;
                }
                if (!productionMatch && !learningMatch) {
                    return true;
                }
                continue;
            }

            if (!productionMatch && !requiredSkills.isEmpty()) {
                return true;
            }
            if (!productionMatch && learningMatch) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCandidateDomainFit(String combinedText, CandidateProfileConfig candidateProfile) {
        int strongMatches = countDomainMatches(combinedText, candidateProfile.strongDomains());
        int moderateMatches = countDomainMatches(combinedText, candidateProfile.moderateDomains());
        return strongMatches >= 1 || moderateMatches >= 2;
    }

    private boolean hasCandidateDomainGap(String combinedText, CandidateProfileConfig candidateProfile) {
        int limitedMatches = countDomainMatches(combinedText, candidateProfile.limitedDomains());
        if (limitedMatches == 0) {
            return false;
        }
        return !hasCandidateDomainFit(combinedText, candidateProfile);
    }

    private boolean hasCandidateSeniorityFit(
            JobInput job,
            String combinedText,
            CandidateProfileConfig candidateProfile
    ) {
        int requiredYears = extractRequiredYears(combinedText);
        String normalizedTitle = normalize(job.title());
        if (requiredYears > 0 && requiredYears <= candidateProfile.totalExperienceYears()) {
            return !hasJuniorMidRoleIndicator(normalizedTitle);
        }
        return containsAny(normalizedTitle, SENIOR_ROLE_KEYWORDS) && candidateProfile.totalExperienceYears() >= 5;
    }

    private boolean hasCandidateSeniorityMismatch(
            JobInput job,
            String combinedText,
            CandidateProfileConfig candidateProfile
    ) {
        int requiredYears = extractRequiredYears(combinedText);
        String normalizedTitle = normalize(job.title());
        if (requiredYears > candidateProfile.totalExperienceYears()) {
            return true;
        }
        return candidateProfile.totalExperienceYears() >= 10 && hasJuniorMidRoleIndicator(normalizedTitle);
    }

    private int countExplicitRequiredSkillMatches(JobInput job, Set<String> candidateSkills) {
        int matches = 0;
        for (String line : extractRequirementLines(job.description())) {
            Set<String> requiredSkills = extractMentionedTechs(line);
            if (requiredSkills.isEmpty()) {
                continue;
            }
            if (containsAnyCanonicalSkill(requiredSkills, candidateSkills)) {
                matches++;
            }
        }
        return matches;
    }

    private List<String> extractRequirementLines(String description) {
        List<String> lines = splitDescriptionLines(description);
        List<String> requirementLines = new ArrayList<>();
        boolean withinRequirementsSection = false;
        for (String line : lines) {
            String normalizedLine = normalize(line);
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (containsAny(normalizedLine, CANDIDATE_NON_REQUIREMENT_SECTION_KEYWORDS)) {
                withinRequirementsSection = false;
            }
            if (containsAny(normalizedLine, CANDIDATE_REQUIREMENT_SECTION_KEYWORDS)) {
                withinRequirementsSection = true;
            }
            if (withinRequirementsSection || containsAny(normalizedLine, CANDIDATE_REQUIREMENT_LINE_HINTS)) {
                requirementLines.add(normalizedLine);
            }
        }
        return requirementLines;
    }

    private List<String> splitDescriptionLines(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        return List.of(description.split("\\R"));
    }

    private Set<String> extractMentionedTechs(String text) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        String normalizedText = normalize(text);
        for (Map.Entry<String, List<String>> entry : CANDIDATE_SKILL_KEYWORDS.entrySet()) {
            if (matchesAnySkillAlias(normalizedText, entry.getValue())) {
                matches.add(entry.getKey());
            }
        }
        return matches;
    }

    private Set<String> canonicalizeSkills(List<String> rawSkills) {
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String rawSkill : rawSkills) {
            String normalizedSkill = normalize(rawSkill);
            for (Map.Entry<String, List<String>> entry : CANDIDATE_SKILL_KEYWORDS.entrySet()) {
                if (matchesAnySkillAlias(normalizedSkill, entry.getValue())) {
                    canonical.add(entry.getKey());
                }
            }
        }
        return canonical;
    }

    private int countCanonicalSkillMatches(String text, Set<String> canonicalSkills) {
        int matches = 0;
        for (String skill : canonicalSkills) {
            List<String> aliases = CANDIDATE_SKILL_KEYWORDS.getOrDefault(skill, List.of(skill));
            if (matchesAnySkillAlias(text, aliases)) {
                matches++;
            }
        }
        return matches;
    }

    private boolean containsAnyCanonicalSkill(Set<String> requiredSkills, Set<String> candidateSkills) {
        for (String skill : requiredSkills) {
            if (candidateSkills.contains(skill)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAlternativeSkillLine(String normalizedLine) {
        if (containsAny(normalizedLine, CANDIDATE_ALTERNATIVE_LINE_HINTS)) {
            return true;
        }
        return normalizedLine.contains(" or ");
    }

    private int countDomainMatches(String combinedText, List<String> domainKeys) {
        int matches = 0;
        for (String domainKey : domainKeys) {
            List<String> aliases = CANDIDATE_DOMAIN_KEYWORDS.get(normalize(domainKey));
            if (aliases != null && containsAny(combinedText, aliases)) {
                matches++;
            }
        }
        return matches;
    }

    private int extractRequiredYears(String combinedText) {
        Matcher matcher = YEARS_EXPERIENCE_PATTERN.matcher(combinedText);
        int maxYears = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > maxYears) {
                maxYears = value;
            }
        }
        return maxYears;
    }

    private boolean hasJuniorMidRoleIndicator(String normalizedTitle) {
        return matchesAnyPattern(normalizedTitle, JUNIOR_MID_TITLE_PATTERNS);
    }

    private boolean matchesAnySkillAlias(String text, List<String> aliases) {
        for (String alias : aliases) {
            if (containsTechAlias(text, alias)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTechAlias(String text, String alias) {
        String normalizedText = normalize(text);
        String normalizedAlias = normalize(alias);
        if (normalizedText.isBlank() || normalizedAlias.isBlank()) {
            return false;
        }
        String pattern = "(^|[^a-z0-9])" + Pattern.quote(normalizedAlias) + "([^a-z0-9]|$)";
        return Pattern.compile(pattern).matcher(normalizedText).find();
    }

    private void detectHardFilters(
            JobInput job,
            String combinedText,
            String remotePolicy,
            String salaryRange,
            Set<String> personaPriorities,
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

        if (personaHardNo.contains(HARD_NO_JAPANESE_ONLY_ENVIRONMENT)
                && hasCriticalLanguageFrictionSignal(combinedText)) {
            hardRejectReasons.add("critical Japanese-only communication environment detected");
        }

        if (personaHardNo.contains(HARD_NO_WORKLOAD_OVERLOAD)
                && hasWorkloadOverloadHardCondition(combinedText)) {
            hardRejectReasons.add("overload workload policy detected (no fixed hours + minimum weekly commitment + overtime pressure)");
        }

        if (personaHardNo.contains(HARD_NO_FORCED_RELOCATION)
                && hasCompanyControlledOverseasRelocationRisk(combinedText)) {
            hardRejectReasons.add("company-controlled overseas relocation risk detected");
        }
    }

    private int resolvePositiveWeight(String signal, Set<String> priorities, Map<String, Integer> weights) {
        if (weights.containsKey(signal)) {
            return weights.get(signal);
        }
        String priority = POSITIVE_SIGNAL_TO_PRIORITY.get(signal);
        return priorities.contains(priority) ? POSITIVE_PRIORITY_WEIGHT : POSITIVE_DEFAULT_WEIGHT;
    }

    private int resolveRiskWeight(String signal, Set<String> priorities, Map<String, Integer> weights) {
        if (weights.containsKey(signal)) {
            return weights.get(signal);
        }
        String priority = RISK_SIGNAL_TO_PRIORITY.get(signal);
        int base = priorities.contains(priority) ? RISK_PRIORITY_WEIGHT : RISK_DEFAULT_WEIGHT;
        return base + RISK_SIGNAL_EXTRA_PENALTY.getOrDefault(signal, 0);
    }

    private int computeScore(
            Set<String> positiveSignals,
            Set<String> riskSignals,
            Set<String> personaPriorities,
            Map<String, Integer> signalWeights
    ) {
        int score = 0;
        for (String signal : positiveSignals) {
            score += resolvePositiveWeight(signal, personaPriorities, signalWeights);
        }
        for (String signal : riskSignals) {
            score -= resolveRiskWeight(signal, personaPriorities, signalWeights);
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

    private ScoreRange computeScoreRange(Set<String> personaPriorities, Map<String, Integer> signalWeights) {
        int min = 0;
        int max = 0;
        for (String signal : POSITIVE_SIGNAL_TO_PRIORITY.keySet()) {
            max += resolvePositiveWeight(signal, personaPriorities, signalWeights);
        }
        for (String signal : RISK_SIGNAL_TO_PRIORITY.keySet()) {
            min -= resolveRiskWeight(signal, personaPriorities, signalWeights);
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
        hasStrongRequiredLanguage = resolveRequiredLanguageConflict(
                combinedText,
                hasStrongRequiredLanguage,
                hasOptionalLanguage
        );
        if (hasStrongRequiredLanguage) {
            return true;
        }
        if (hasOptionalLanguage) {
            return false;
        }
        return containsAny(combinedText, LANGUAGE_FRICTION_SOFT_KEYWORDS);
    }

    private boolean hasCriticalLanguageFrictionSignal(String combinedText) {
        boolean hasMediumOrHighLanguageRequirement = containsAny(combinedText, LANGUAGE_MEDIUM_HIGH_FRICTION_KEYWORDS)
                || containsAny(combinedText, LANGUAGE_HIGH_FRICTION_KEYWORDS)
                || containsAny(combinedText, LANGUAGE_REQUIRED_KEYWORDS)
                || matchesAnyPattern(combinedText, LANGUAGE_REQUIRED_PATTERNS);
        boolean hasJapaneseInternalOnly = containsAny(combinedText, JAPANESE_INTERNAL_ONLY_KEYWORDS)
                || matchesAnyPattern(combinedText, JAPANESE_INTERNAL_ONLY_PATTERNS);
        return hasMediumOrHighLanguageRequirement && hasJapaneseInternalOnly;
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
        hasStrongRequiredLanguage = resolveRequiredLanguageConflict(
                combinedText,
                hasStrongRequiredLanguage,
                hasOptionalLanguage
        );

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
        if (hasCriticalLanguageFrictionSignal(combinedText)) {
            index = Math.max(index, LANGUAGE_INDEX_CRITICAL);
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

    private boolean resolveRequiredLanguageConflict(
            String combinedText,
            boolean hasStrongRequiredLanguage,
            boolean hasOptionalLanguage
    ) {
        if (!hasStrongRequiredLanguage || !hasOptionalLanguage) {
            return hasStrongRequiredLanguage;
        }

        boolean hasClearRequiredIndicator = containsAny(combinedText, LANGUAGE_HIGH_FRICTION_KEYWORDS)
                || containsAny(combinedText, LANGUAGE_MEDIUM_HIGH_FRICTION_KEYWORDS)
                || matchesAnyPattern(combinedText, LANGUAGE_REQUIRED_PATTERNS);

        if (hasClearRequiredIndicator) {
            return true;
        }
        return false;
    }

    private boolean hasEngineeringEnvironmentPositiveSignal(String combinedText) {
        return containsAny(combinedText, ENGINEERING_ENVIRONMENT_POSITIVE_KEYWORDS);
    }

    private boolean hasEngineeringEnvironmentRiskSignal(String combinedText) {
        return containsAny(combinedText, ENGINEERING_ENVIRONMENT_RISK_KEYWORDS);
    }

    private boolean hasRoleMismatchManagerVsIcTitle(String title, String combinedText) {
        String normalizedTitle = normalize(title);
        if (normalizedTitle.isBlank()) {
            return false;
        }
        boolean hasManagerScope = containsAny(combinedText, ROLE_MANAGER_SCOPE_KEYWORDS);
        boolean hasIcTitle = containsAny(normalizedTitle, ROLE_IC_TITLE_KEYWORDS);
        boolean hasManagerTitle = containsAny(normalizedTitle, ROLE_MANAGER_TITLE_KEYWORDS);
        return hasManagerScope && hasIcTitle && !hasManagerTitle;
    }

    private boolean hasRoleIdentityMismatch(String title, String description) {
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);
        if (normalizedTitle.isBlank() || normalizedDescription.isBlank()) {
            return false;
        }

        String explicitPosition = extractExplicitPositionLabel(normalizedDescription);
        if (explicitPosition.isBlank()) {
            return false;
        }

        boolean titleSoftware = containsAny(normalizedTitle, ROLE_SOFTWARE_KEYWORDS);
        boolean titleNonSoftware = containsAny(normalizedTitle, ROLE_NON_SOFTWARE_MISMATCH_KEYWORDS);
        boolean positionSoftware = containsAny(explicitPosition, ROLE_SOFTWARE_KEYWORDS);
        boolean positionNonSoftware = containsAny(explicitPosition, ROLE_NON_SOFTWARE_MISMATCH_KEYWORDS);

        if (titleSoftware && positionNonSoftware && !positionSoftware) {
            return true;
        }
        if (titleNonSoftware && positionSoftware && !titleSoftware) {
            return true;
        }
        return false;
    }

    private String extractExplicitPositionLabel(String normalizedDescription) {
        Matcher matcher = POSITION_LABEL_PATTERN.matcher(normalizedDescription);
        if (!matcher.find()) {
            return "";
        }
        return normalize(matcher.group(1));
    }

    private boolean hasIntermediaryContractRisk(String combinedText) {
        int intermediaryHits = countKeywordMatches(combinedText, INTERMEDIARY_CONTRACT_KEYWORDS);
        int processHits = countKeywordMatches(combinedText, APPLICATION_PROCESS_TEMPLATE_KEYWORDS);
        boolean genericDesiredSkills = GENERIC_DESIRED_SKILLS_ENGINEER_PATTERN.matcher(combinedText).find();

        if (intermediaryHits >= INTERMEDIARY_KEYWORD_HIT_MIN) {
            return true;
        }
        return intermediaryHits >= 1
                && processHits >= APPLICATION_TEMPLATE_HIT_MIN
                && genericDesiredSkills;
    }

    private boolean hasInclusionContradiction(String combinedText) {
        boolean hasForeignWelcome = containsAny(combinedText, FOREIGN_WELCOME_KEYWORDS);
        boolean hasJapaneseInternalOnly = containsAny(combinedText, JAPANESE_INTERNAL_ONLY_KEYWORDS)
                || matchesAnyPattern(combinedText, JAPANESE_INTERNAL_ONLY_PATTERNS);
        return hasForeignWelcome && hasJapaneseInternalOnly;
    }

    private boolean hasManagerScopeSalaryMisaligned(String title, String combinedText, String salaryRange) {
        if (!isSalaryTransparent(salaryRange)) {
            return false;
        }
        boolean hasManagerScope = containsAny(combinedText, ROLE_MANAGER_SCOPE_KEYWORDS)
                || containsAny(normalize(title), ROLE_MANAGER_TITLE_KEYWORDS);
        if (!hasManagerScope) {
            return false;
        }
        int upperBoundYen = parseUpperBoundYen(salaryRange);
        return upperBoundYen > NORMALIZED_SCORE_MIN && upperBoundYen <= MANAGER_SCOPE_SALARY_MISALIGNED_MAX_YEN;
    }

    private boolean hasWorkloadPolicyRisk(String combinedText) {
        int matched = 0;
        if (containsAny(combinedText, WORKLOAD_NO_FIXED_HOURS_KEYWORDS)) {
            matched++;
        }
        if (containsAny(combinedText, WORKLOAD_MIN_WEEKLY_COMMITMENT_KEYWORDS)) {
            matched++;
        }
        if (containsAny(combinedText, WORKLOAD_PRESSURE_KEYWORDS)
                || containsAny(combinedText, OVERTIME_RISK_KEYWORDS)) {
            matched++;
        }
        return matched >= WORKLOAD_OVERLOAD_SIGNAL_THRESHOLD;
    }

    private boolean hasWorkloadOverloadHardCondition(String combinedText) {
        boolean noFixedHours = containsAny(combinedText, WORKLOAD_NO_FIXED_HOURS_KEYWORDS);
        boolean minWeeklyCommitment = containsAny(combinedText, WORKLOAD_MIN_WEEKLY_COMMITMENT_KEYWORDS);
        boolean overtimePressure = containsAny(combinedText, WORKLOAD_PRESSURE_KEYWORDS)
                || containsAny(combinedText, OVERTIME_RISK_KEYWORDS);
        return noFixedHours && minWeeklyCommitment && overtimePressure;
    }

    private boolean hasSalaryRangeAnomaly(String salaryRange) {
        if (salaryRange.isBlank()) {
            return false;
        }
        List<Integer> orderedValues = extractOrderedSalaryValues(salaryRange);
        if (orderedValues.size() < 2) {
            return false;
        }
        return orderedValues.get(0) > orderedValues.get(orderedValues.size() - 1);
    }

    private boolean hasSalaryBelowPersonaFloor(
            JobInput job,
            String combinedText,
            String salaryRange,
            List<BlacklistedCompanyConfig> blacklist,
            int minimumSalaryYen
    ) {
        if (minimumSalaryYen <= 0 || !isSalaryTransparent(salaryRange)) {
            return false;
        }
        int benchmarkYen = estimateSalaryBenchmarkYen(
                salaryRange,
                shouldUseConservativeSalaryBenchmark(job, combinedText, blacklist)
        );
        return benchmarkYen > 0 && benchmarkYen < minimumSalaryYen;
    }

    private boolean shouldUseConservativeSalaryBenchmark(
            JobInput job,
            String combinedText,
            List<BlacklistedCompanyConfig> blacklist
    ) {
        return isBlacklistedCompany(job.companyName(), blacklist)
                || hasIntermediaryContractRisk(combinedText)
                || containsAny(combinedText, CONSULTING_HARD_KEYWORDS);
    }

    private List<Integer> extractOrderedSalaryValues(String salaryRange) {
        List<Integer> values = new ArrayList<>();
        Matcher matcher = SALARY_ORDERED_TOKEN_PATTERN.matcher(salaryRange);
        while (matcher.find()) {
            String numberRaw = matcher.group(1);
            String suffixRaw = matcher.group(2);
            if (numberRaw == null || numberRaw.isBlank()) {
                continue;
            }

            String normalized = numberRaw.replace(",", "");
            double baseValue;
            try {
                baseValue = Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                continue;
            }

            int value = (int) Math.round(baseValue);
            String suffix = normalize(suffixRaw);
            if ("m".equals(suffix) || "million".equals(suffix)) {
                value = (int) Math.round(baseValue * 1_000_000d);
            } else if ("k".equals(suffix)) {
                value = (int) Math.round(baseValue * 1_000d);
            }
            values.add(value);
        }
        return values;
    }

    private int estimateSalaryBenchmarkYen(String salaryRange, boolean conservativeRange) {
        List<Integer> orderedValues = extractOrderedSalaryValues(salaryRange);
        if (orderedValues.size() >= 2) {
            int lowest = orderedValues.get(0);
            int highest = orderedValues.get(orderedValues.size() - 1);
            if (lowest <= highest) {
                int ratioDivisor = conservativeRange ? 4 : 2;
                return lowest + ((highest - lowest) / ratioDivisor);
            }
        }
        return parseUpperBoundYen(salaryRange);
    }

    private int parseUpperBoundYen(String salaryRange) {
        if (salaryRange.isBlank()) {
            return NORMALIZED_SCORE_MIN;
        }

        int maxMillionBasedYen = NORMALIZED_SCORE_MIN;
        Matcher millionMatcher = SALARY_MILLION_PATTERN.matcher(salaryRange);
        while (millionMatcher.find()) {
            double value = Double.parseDouble(millionMatcher.group(1));
            int yen = (int) Math.round(value * 1_000_000d);
            if (yen > maxMillionBasedYen) {
                maxMillionBasedYen = yen;
            }
        }
        if (maxMillionBasedYen > NORMALIZED_SCORE_MIN) {
            return maxMillionBasedYen;
        }

        int maxNumber = NORMALIZED_SCORE_MIN;
        Matcher numberMatcher = SALARY_NUMBER_PATTERN.matcher(salaryRange);
        while (numberMatcher.find()) {
            String raw = numberMatcher.group(1).replace(",", "").replace(".", "");
            int value = Integer.parseInt(raw);
            if (value > maxNumber) {
                maxNumber = value;
            }
        }
        return maxNumber;
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
        return salaryRange.isBlank()
                || containsAny(salaryRange, SALARY_MISSING_KEYWORDS)
                || !hasExplicitSalaryRange(salaryRange);
    }

    private boolean isSalaryTransparent(String salaryRange) {
        return hasExplicitSalaryRange(salaryRange) && !hasSalaryRangeAnomaly(salaryRange);
    }

    private boolean hasExplicitSalaryRange(String salaryRange) {
        if (salaryRange.isBlank() || containsAny(salaryRange, SALARY_MISSING_KEYWORDS)) {
            return false;
        }
        List<Integer> orderedValues = extractOrderedSalaryValues(salaryRange);
        if (orderedValues.size() < 2) {
            return false;
        }
        String normalizedSalaryRange = normalize(salaryRange);
        return normalizedSalaryRange.contains("-")
                || normalizedSalaryRange.contains("–")
                || normalizedSalaryRange.contains("—")
                || normalizedSalaryRange.contains("~")
                || normalizedSalaryRange.contains("〜")
                || normalizedSalaryRange.contains("～")
                || normalizedSalaryRange.contains(" to ")
                || (normalizedSalaryRange.contains("between")
                && normalizedSalaryRange.contains(" and "));
    }

    private boolean hasCompanyControlledOverseasRelocationRisk(String combinedText) {
        boolean hasLocationControl = containsAny(combinedText, LOCATION_CHANGE_BY_COMPANY_KEYWORDS);
        boolean hasOverseasTransfer = containsAny(combinedText, LOCATION_OVERSEAS_TRANSFER_KEYWORDS);
        return hasLocationControl && hasOverseasTransfer;
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
