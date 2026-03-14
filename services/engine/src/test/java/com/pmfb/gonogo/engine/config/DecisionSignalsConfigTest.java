package com.pmfb.gonogo.engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DecisionSignalsConfigTest {
    @Test
    void loaderReadsDecisionSignalsFromYaml() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-decision-signals-test");
        writeBaseConfig(tempDir);
        Files.writeString(
                tempDir.resolve("decision-signals.yaml"),
                """
                        language:
                          required_keywords:
                            - japanese required
                            - business-level japanese
                          friction_soft_keywords:
                            - japanese communication
                          medium_high_friction_keywords:
                            - business-level japanese
                          high_friction_keywords:
                            - native japanese
                          optional_or_exempt_keywords:
                            - no japanese required
                          english_friendly_keywords:
                            - english-first
                          english_support_environment_keywords:
                            - english is used on a daily basis
                          english_support_max_index: 17
                        work_life_balance:
                          overtime_risk_keywords:
                            - fixed overtime
                            - overtime work may occur
                          holiday_policy_risk_keywords:
                            - national holidays are workdays
                        mobility:
                          location_mobility_risk_keywords:
                            - including overseas
                        job_post_quality:
                          generic_marketing_risk_keywords:
                            - industry leading company
                            - cutting-edge solutions
                          generic_marketing_risk_hit_min: 3
                          conditions_section_keywords:
                            - benefits
                          vague_conditions_risk_keywords:
                            - opportunity to work
                          vague_conditions_risk_hit_min: 2
                          concrete_conditions_keywords:
                            - annual leave
                        """
        );

        EngineConfig config = new YamlConfigLoader(tempDir).load();

        assertEquals(2, config.decisionSignals().language().requiredKeywords().size());
        assertEquals(17, config.decisionSignals().language().englishSupportMaxIndex());
        assertEquals(
                List.of("fixed overtime", "overtime work may occur"),
                config.decisionSignals().workLifeBalance().overtimeRiskKeywords()
        );
        assertEquals(
                List.of("including overseas"),
                config.decisionSignals().mobility().locationMobilityRiskKeywords()
        );
        assertEquals(3, config.decisionSignals().jobPostQuality().genericMarketingRiskHitMin());
        assertEquals(
                List.of("annual leave"),
                config.decisionSignals().jobPostQuality().concreteConditionsKeywords()
        );
    }

    @Test
    void validatorRejectsInvalidDecisionSignals() {
        EngineConfig config = new EngineConfig(
                List.of(new CompanyConfig(
                        "moneyforward",
                        "Money Forward",
                        "https://example.com/careers",
                        "product",
                        "japan",
                        "test company"
                )),
                List.of(new PersonaConfig(
                        "product_expat_engineer",
                        "Test persona",
                        List.of("english_environment"),
                        List.of("onsite_only", "salary_missing"),
                        List.of("hybrid_partial")
                )),
                List.of(),
                List.of(),
                RuntimeSettingsConfig.defaults(),
                new DecisionSignalsConfig(
                        new DecisionSignalsConfig.LanguageConfig(
                                List.of(),
                                List.of("japanese communication", "japanese communication"),
                                List.of("business-level japanese"),
                                List.of("native japanese"),
                                List.of("no japanese required"),
                                List.of("english-first"),
                                List.of("english is used on a daily basis"),
                                101
                        ),
                        new DecisionSignalsConfig.WorkLifeBalanceConfig(
                                List.of(),
                                List.of("national holidays are workdays", "national holidays are workdays")
                        ),
                        new DecisionSignalsConfig.MobilityConfig(List.of()),
                        new DecisionSignalsConfig.JobPostQualityConfig(
                                List.of(),
                                0,
                                List.of(),
                                List.of("opportunity to work", "opportunity to work"),
                                0,
                                List.of()
                        )
                )
        );

        List<String> errors = new ConfigValidator().validate(config);

        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.language.required_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.language.friction_soft_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.language.english_support_max_index")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.work_life_balance.overtime_risk_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.work_life_balance.holiday_policy_risk_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.mobility.location_mobility_risk_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.job_post_quality.generic_marketing_risk_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.job_post_quality.generic_marketing_risk_hit_min")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.job_post_quality.conditions_section_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.job_post_quality.vague_conditions_risk_keywords")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.job_post_quality.vague_conditions_risk_hit_min")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("decision-signals.job_post_quality.concrete_conditions_keywords")));
    }

    private void writeBaseConfig(Path configDir) throws IOException {
        Files.writeString(
                configDir.resolve("companies.yaml"),
                """
                        companies:
                          - id: moneyforward
                            name: Money Forward
                            career_url: https://example.com/careers
                            corporate_url: https://example.com
                            type_hint: product
                            region: japan
                            notes: Test company
                        """
        );
        Files.writeString(
                configDir.resolve("personas.yaml"),
                """
                        personas:
                          - id: product_expat_engineer
                            description: Test persona
                            priorities:
                              - english_environment
                            hard_no:
                              - onsite_only
                              - salary_missing
                            acceptable_if:
                              - hybrid_partial
                        """
        );
        Files.writeString(
                configDir.resolve("blacklist.yaml"),
                """
                        blacklisted_companies: []
                        """
        );
    }
}
