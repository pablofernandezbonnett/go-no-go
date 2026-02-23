package com.pmfb.gonogo.engine.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.config.BlacklistedCompanyConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
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
                )
        );
    }
}
