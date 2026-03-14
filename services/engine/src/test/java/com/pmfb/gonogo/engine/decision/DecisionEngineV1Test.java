package com.pmfb.gonogo.engine.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.config.BlacklistedCompanyConfig;
import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.RuntimeSettingsConfig;
import com.pmfb.gonogo.engine.job.JobInput;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DecisionEngineV1Test {
    private final DecisionEngineV1 engine = new DecisionEngineV1();

    @Test
    void returnsNoGoWhenOnsiteOnlyIsDetected() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Mercari",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 8,000,000 - 12,000,000",
                        "Onsite-only, Tokyo office",
                        "English first team with product ownership."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.NO_GO, result.verdict());
        assertTrue(result.hardRejectReasons().contains("onsite-only work policy detected"));
    }

    @Test
    void returnsGoForStrongProductSignals() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Money Forward",
                        "Senior Backend Engineer",
                        "Tokyo",
                        "JPY 10,000,000 - 14,000,000",
                        "Hybrid (2 days onsite, 3 days remote)",
                        "English-first international team with product ownership, code review, " +
                                "automated testing and flexible hours."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.GO, result.verdict());
        assertTrue(result.hardRejectReasons().isEmpty());
        assertTrue(result.positiveSignals().contains("salary_transparency"));
        assertTrue(result.positiveSignals().contains("hybrid_work"));
        assertTrue(result.positiveSignals().contains("english_environment"));
    }

    @Test
    void returnsGoWithCautionWhenLanguageFrictionExists() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Mercari",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "English first team, but business Japanese and JLPT N2 are required."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.GO_WITH_CAUTION, result.verdict());
        assertTrue(result.riskSignals().contains("language_friction"));
        assertTrue(result.hardRejectReasons().isEmpty());
        assertEquals(85, result.languageFrictionIndex());
    }

    @Test
    void returnsNoGoWhenSalaryIsMissing() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Mercari",
                        "Backend Engineer",
                        "Tokyo",
                        "TBD",
                        "Hybrid",
                        "English-first team with product ownership and code review."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.NO_GO, result.verdict());
        assertTrue(result.hardRejectReasons().contains("salary information is missing or non-transparent"));
    }

    @Test
    void returnsNoGoWhenSalaryHasNoExplicitRange() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Mercari",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 10,000,000",
                        "Hybrid",
                        "English-first team with product ownership and code review."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.NO_GO, result.verdict());
        assertTrue(result.hardRejectReasons().contains("salary information is missing or non-transparent"));
        assertTrue(result.riskSignals().contains("salary_low_confidence"));
        assertFalse(result.positiveSignals().contains("salary_transparency"));
    }

    @Test
    void doesNotFlagLanguageFrictionWhenJapaneseIsOptional() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Money Forward",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,500,000 - 12,000,000",
                        "Hybrid",
                        "English-first team. Japanese is a plus, but not required."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.GO, result.verdict());
        assertFalse(result.riskSignals().contains("language_friction"));
        assertEquals(0, result.languageFrictionIndex());
    }

    @Test
    void flagsLanguageFrictionWhenJapaneseIsRequiredInJapaneseText() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Mercari",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "グローバルチームですが、ビジネスレベルの日本語（日本語必須）です。"
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("language_friction"));
        assertEquals(85, result.languageFrictionIndex());
    }

    @Test
    void flagsLanguageFrictionWhenRoleRequiresCommunicationInJapanese() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Unknown Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "Communicate requirements, progress, and technical decisions effectively in Japanese. "
                                + "Communicates confidently in Japanese and collaborates effectively."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("language_friction"));
        assertEquals(75, result.languageFrictionIndex());
    }

    @Test
    void flagsAnonymousEmployerRiskForOpaqueRecruiterStylePost() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Unknown Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "Work in an industry leading company. "
                                + "The employer is a large organization operating within the Technology & Telecoms industry."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("anonymous_employer_risk"));
    }

    @Test
    void flagsGenericMarketingPostRiskForBuzzSentenceHeavyAnonymousPost() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Unknown Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "Work on large-scale, high-impact systems. Work in an industry leading company. "
                                + "They are committed to delivering cutting-edge solutions and fostering technical excellence."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("generic_marketing_post_risk"));
    }

    @Test
    void flagsVagueConditionsRiskWhenConditionsSectionIsPureMarketing() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Unknown Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "Conditions and benefits. Opportunity to work in the vibrant city of Tokyo. "
                                + "A chance to contribute to innovative technology solutions."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("vague_conditions_risk"));
    }

    @Test
    void doesNotFlagVagueConditionsRiskWhenConcreteBenefitsExist() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Unknown Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "Benefits. Annual leave, social insurance, bonus, hybrid work, and flextime are provided. "
                                + "A chance to contribute to innovative technology solutions."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertFalse(result.riskSignals().contains("vague_conditions_risk"));
    }

    @Test
    void appliesCompanyProfileTagsAsPositiveSignals() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Profiled Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 10,000,000 - 12,000,000",
                        "Hybrid",
                        "Role description without explicit culture keywords."
                ),
                defaultPersona(),
                configWithCompany(
                        new CompanyConfig(
                                "profiled_company",
                                "Profiled Company",
                                "https://example.com/careers",
                                "product",
                                "japan",
                                "Profiled company",
                                List.of("expat_friendly", "engineering_brand", "reputation_strong"),
                                List.of()
                        )
                )
        );

        assertTrue(result.positiveSignals().contains("english_environment"));
        assertTrue(result.positiveSignals().contains("engineering_culture"));
        assertTrue(result.positiveSignals().contains("company_reputation_positive"));
        assertEquals(70, result.companyReputationIndex());
    }

    @Test
    void appliesCompanyRiskTagsAsRiskSignals() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Risky Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        "Role description without language/overtime hints."
                ),
                defaultPersona(),
                configWithCompany(
                        new CompanyConfig(
                                "risky_company",
                                "Risky Company",
                                "https://example.com/careers",
                                "product",
                                "japan",
                                "Risky company",
                                List.of(),
                                List.of("language_friction_high", "reputation_risk")
                        )
                )
        );

        assertTrue(result.riskSignals().contains("language_friction"));
        assertTrue(result.riskSignals().contains("company_reputation_risk"));
        assertEquals(70, result.languageFrictionIndex());
        assertEquals(28, result.companyReputationIndex());
    }

    @Test
    void aggregatesStrongPositiveReputationFromTagsAndText() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Reputable Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 10,000,000 - 12,000,000",
                        "Hybrid",
                        "English-first team with profitable growth, high retention, transparent leadership, " +
                                "and a long-term roadmap."
                ),
                defaultPersona(),
                configWithCompany(
                        new CompanyConfig(
                                "reputable_company",
                                "Reputable Company",
                                "https://example.com/careers",
                                "product",
                                "japan",
                                "Reputable company",
                                List.of("reputation_strong", "stable_public"),
                                List.of()
                        )
                )
        );

        assertTrue(result.positiveSignals().contains("company_reputation_positive"));
        assertTrue(result.positiveSignals().contains("company_reputation_positive_strong"));
        assertTrue(result.companyReputationIndex() >= 85);
    }

    @Test
    void aggregatesHighReputationRiskFromTagsAndText() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Turbulent Company",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 10,500,000",
                        "Hybrid",
                        "Recent layoff and restructuring with hiring freeze and salary cuts."
                ),
                defaultPersona(),
                configWithCompany(
                        new CompanyConfig(
                                "turbulent_company",
                                "Turbulent Company",
                                "https://example.com/careers",
                                "product",
                                "japan",
                                "Turbulent company",
                                List.of(),
                                List.of("reputation_risk", "layoff_risk")
                        )
                )
        );

        assertTrue(result.riskSignals().contains("company_reputation_risk"));
        assertTrue(result.riskSignals().contains("company_reputation_risk_high"));
        assertTrue(result.companyReputationIndex() <= 20);
    }

    @Test
    void detectsStrongEngineeringEnvironmentSignal() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Atlas Labs",
                        "Platform Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 12,000,000",
                        "Hybrid",
                        "English-first product team with end-to-end ownership, blameless postmortems, " +
                                "documented runbooks, and sustainable on-call with compensation."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.GO, result.verdict());
        assertTrue(result.positiveSignals().contains("engineering_environment"));
    }

    @Test
    void detectsEngineeringEnvironmentRiskSignal() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Atlas Labs",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 8,500,000 - 10,500,000",
                        "Hybrid",
                        "English-first role with 24/7 on-call, frequent incidents, legacy monolith, " +
                                "and constant firefighting."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.GO_WITH_CAUTION, result.verdict());
        assertTrue(result.riskSignals().contains("engineering_environment_risk"));
    }

    @Test
    void returnsGoWithCautionForConsultingCompanyWhenPersonaIsPragmatic() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Randstad Japan",
                        "Backend Engineer",
                        "Japan",
                        "JPY 7,000,000 - 11,000,000",
                        "Hybrid",
                        "Design and develop backend systems, code reviews, Agile teamwork, and architecture contributions."
                ),
                pragmaticPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.GO_WITH_CAUTION, result.verdict());
        assertTrue(result.hardRejectReasons().isEmpty());
        assertTrue(result.riskSignals().contains("consulting_risk"));
    }

    @Test
    void flagsSalaryBelowPersonaFloorWhenConfigured() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Atlas Labs",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 4,000,000 - 7,000,000",
                        "Unspecified",
                        "General backend engineering role."
                ),
                pragmaticPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.NO_GO, result.verdict());
        assertTrue(result.riskSignals().contains("salary_below_persona_floor"));
    }

    @Test
    void usesConservativeSalaryBenchmarkForIntermediaryRoles() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Randstad Japan",
                        "Senior Java Developer",
                        "Japan",
                        "JPY 6,000,000 - 8,500,000",
                        "Hybrid",
                        "Java and Spring Boot backend development with code reviews and production support."
                ),
                pragmaticPersona(),
                defaultConfig()
        );

        assertEquals(Verdict.NO_GO, result.verdict());
        assertTrue(result.riskSignals().contains("consulting_risk"));
        assertTrue(result.riskSignals().contains("salary_below_persona_floor"));
    }

    @Test
    void addsCandidateFitSignalsWhenProfileMatchesRole() {
        CandidateProfileConfig candidateProfile = new CandidateProfileConfig(
                "pmfb",
                "PMFB",
                "Senior Backend Engineer",
                "Tokyo",
                8,
                List.of("Java", "Spring Boot", "AWS", "JPA"),
                List.of("Kubernetes"),
                List.of(),
                List.of("enterprise_java", "system_design"),
                List.of("cloud_basics"),
                List.of("mobile_cross_platform")
        );

        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "avatarin",
                        "Senior Backend Engineer",
                        "Tokyo",
                        "JPY 8,000,000 - 10,000,000",
                        "Hybrid",
                        """
                                Requirements:
                                - 5+ years of experience in Java and Spring Boot backend development
                                - Experience with AWS and system design
                                Responsibilities:
                                - Build scalable services in a global team
                                """
                ),
                pragmaticPersona(),
                candidateProfile,
                defaultConfig()
        );

        assertTrue(result.positiveSignals().contains("candidate_stack_fit"));
        assertTrue(result.positiveSignals().contains("candidate_domain_fit"));
        assertTrue(result.positiveSignals().contains("candidate_seniority_fit"));
        assertFalse(result.riskSignals().contains("candidate_stack_gap"));
    }

    @Test
    void addsCandidateGapSignalsWhenProfileDoesNotMatchRole() {
        CandidateProfileConfig candidateProfile = new CandidateProfileConfig(
                "pmfb",
                "PMFB",
                "Senior Backend Engineer",
                "Tokyo",
                12,
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of("Flutter", "Dart"),
                List.of("enterprise_java"),
                List.of("system_design"),
                List.of("mobile_cross_platform")
        );

        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Mobile Shop",
                        "Jr.-Mid Mobile Engineer",
                        "Tokyo",
                        "JPY 6,000,000 - 8,000,000",
                        "Hybrid",
                        """
                                Requirements:
                                - 3+ years of experience with Flutter and Dart
                                - Experience building mobile products
                                Responsibilities:
                                - Ship mobile features
                                """
                ),
                pragmaticPersona(),
                candidateProfile,
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("candidate_stack_gap"));
        assertTrue(result.riskSignals().contains("candidate_domain_gap"));
        assertTrue(result.riskSignals().contains("candidate_seniority_mismatch"));
    }

    @Test
    void detectsAlgorithmicInterviewRiskFromCombinedSignals() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Screening Heavy Co",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,000,000 - 11,000,000",
                        "Hybrid",
                        """
                                Strong problem-solving ability required.
                                Interview process includes a coding test covering data structures and algorithms.
                                """
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("algorithmic_interview_risk"));
    }

    @Test
    void detectsProductEngineeringAndInterviewPositives() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Productive Labs",
                        "Senior Backend Engineer",
                        "Tokyo",
                        "JPY 10,000,000 - 12,000,000",
                        "Hybrid",
                        """
                                Work closely with Product Managers on customer-facing product reliability.
                                We publish an engineering blog, invest in developer productivity,
                                and maintain internal developer tools.
                                Process: casual interview, technical interview, final interview.
                                """
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.positiveSignals().contains("product_pm_collaboration"));
        assertTrue(result.positiveSignals().contains("engineering_maturity"));
        assertTrue(result.positiveSignals().contains("casual_interview"));
    }

    @Test
    void detectsJapanSpecificWorkLifeBalancePositives() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Balanced Systems",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 9,500,000 - 11,500,000",
                        "Hybrid",
                        """
                                The team uses asynchronous communication and written-first updates.
                                Full flextime with no core hours.
                                Average overtime: 10 hours / month.
                                """
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.positiveSignals().contains("async_communication"));
        assertTrue(result.positiveSignals().contains("real_flextime"));
        assertTrue(result.positiveSignals().contains("low_overtime_disclosed"));
        assertFalse(result.riskSignals().contains("fake_flextime_risk"));
    }

    @Test
    void detectsTraditionalPressureAndCustomerSiteRisks() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Traditional SIer",
                        "Backend Engineer",
                        "Tokyo",
                        "JPY 8,500,000 - 10,500,000",
                        "Hybrid",
                        """
                                Business level Japanese required and TOEIC score submission preferred.
                                Flexible working hours.
                                We need a self-motivated person who can work under pressure.
                                Some projects are performed onsite at customer offices.
                                """
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("pressure_culture_risk"));
        assertTrue(result.riskSignals().contains("fake_flextime_risk"));
        assertTrue(result.riskSignals().contains("traditional_corporate_process_risk"));
        assertTrue(result.riskSignals().contains("customer_site_risk"));
    }

    @Test
    void doesNotFlagOvertimeRiskForNeutralOvertimeAllowanceMention() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Fast Retailing",
                        "Application Engineer",
                        "Tokyo",
                        "JPY 6,560,000 - JPY 21,240,000",
                        "Unspecified",
                        """
                                Annual salary range with commuting allowance and overtime allowance.
                                English is used on a daily basis and current fluency is not a requirement.
                                """
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertFalse(result.riskSignals().contains("overtime_risk"));
    }

    @Test
    void doesNotFlagSalaryRangeAnomalyForAnnualAndMonthlyRanges() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Fast Retailing",
                        "Application Engineer",
                        "Tokyo",
                        "¥6.56 million - ¥21.24 million (Monthly Salary: ¥410,000 - ¥1,180,000)",
                        "Unspecified",
                        "English is used on a daily basis and fluency in English is not required."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertFalse(result.riskSignals().contains("salary_range_anomaly"));
    }

    @Test
    void suppressesCompanyLanguageRiskWhenRoleExplicitlySupportsEnglish() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Fast Retailing",
                        "Application Engineer",
                        "Tokyo",
                        "JPY 6,560,000 - JPY 21,240,000",
                        "Unspecified",
                        """
                                Individuals who can proactively engage in a work environment requiring daily English usage.
                                Current fluency is not a requirement. We provide support to become comfortable with English.
                                """
                ),
                defaultPersona(),
                configWithCompany(
                        new CompanyConfig(
                                "fast_retailing",
                                "Fast Retailing",
                                "https://www.fastretailing.com/employment/en/",
                                "retail_product",
                                "japan",
                                "Retail tech transformation focus.",
                                List.of("stable_public", "product_leader"),
                                List.of("language_friction_high")
                        )
                )
        );

        assertFalse(result.riskSignals().contains("language_friction"));
        assertEquals(20, result.languageFrictionIndex());
    }

    @Test
    void flagsHolidayPolicyRiskForNationalHolidaysAsWorkdays() {
        EvaluationResult result = engine.evaluate(
                new JobInput(
                        "Fast Retailing",
                        "Application Engineer",
                        "Tokyo",
                        "JPY 6,560,000 - JPY 21,240,000",
                        "Unspecified",
                        "Two days off per week. National holidays are workdays."
                ),
                defaultPersona(),
                defaultConfig()
        );

        assertTrue(result.riskSignals().contains("holiday_policy_risk"));
    }

    private PersonaConfig defaultPersona() {
        return new PersonaConfig(
                "product_expat_engineer",
                "Product-oriented expat engineer living in Japan",
                List.of(
                        "english_environment",
                        "product_company",
                        "engineering_culture",
                        "hybrid_work",
                        "work_life_balance",
                        "salary",
                        "stability"
                ),
                List.of("consulting_company", "onsite_only", "salary_missing", "early_stage_startup"),
                List.of("hybrid_partial", "japanese_not_blocking", "stable_scaleup")
        );
    }

    private PersonaConfig pragmaticPersona() {
        return new PersonaConfig(
                "product_expat_engineer_pragmatic",
                "Product-oriented expat engineer optimizing for employability first",
                List.of(
                        "english_environment",
                        "product_company",
                        "engineering_culture",
                        "hybrid_work",
                        "work_life_balance",
                        "salary",
                        "stability"
                ),
                List.of("onsite_only", "salary_missing", "early_stage_startup"),
                List.of("hybrid_partial", "japanese_not_blocking", "stable_scaleup"),
                java.util.Map.of(),
                RankingStrategy.BY_SCORE,
                8_000_000
        );
    }

    private EngineConfig defaultConfig() {
        return configWithCompany(
                new CompanyConfig(
                        "mercari",
                        "Mercari",
                        "https://careers.mercari.com/",
                        "product",
                        "japan",
                        "Marketplace product company"
                ),
                new CompanyConfig(
                        "moneyforward",
                        "Money Forward",
                        "https://corp.moneyforward.com/recruit/",
                        "fintech_product",
                        "japan",
                        "Strong engineering culture"
                )
        );
    }

    private EngineConfig configWithCompany(CompanyConfig... companies) {
        return new EngineConfig(
                List.of(companies),
                List.of(defaultPersona()),
                List.of(
                        new BlacklistedCompanyConfig(
                                "Randstad",
                                "Recruitment / dispatch company"
                        )
                ),
                List.of(),
                RuntimeSettingsConfig.defaults()
        );
    }
}
