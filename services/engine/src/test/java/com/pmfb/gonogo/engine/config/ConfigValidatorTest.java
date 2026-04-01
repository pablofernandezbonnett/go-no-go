package com.pmfb.gonogo.engine.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ConfigValidatorTest {
    @Test
    void validatorAllowsPersonaSpecificHardNoWithoutOnsiteOnly() {
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
                        List.of("consulting_company"),
                        List.of("hybrid_partial")
                )),
                List.of(),
                List.of(),
                RuntimeSettingsConfig.defaults()
        );

        List<String> errors = new ConfigValidator().validate(config);

        assertTrue(errors.isEmpty(), () -> "Expected no validation errors but got: " + errors);
    }

    @Test
    void validatorRejectsLocalOrPrivateCompanyUrls() {
        EngineConfig config = new EngineConfig(
                List.of(new CompanyConfig(
                        "moneyforward",
                        "Money Forward",
                        "https://127.0.0.1/careers",
                        "product",
                        "japan",
                        "test company"
                )),
                List.of(new PersonaConfig(
                        "product_expat_engineer",
                        "Test persona",
                        List.of("english_environment"),
                        List.of("consulting_company"),
                        List.of("hybrid_partial")
                )),
                List.of(),
                List.of(),
                RuntimeSettingsConfig.defaults()
        );

        List<String> errors = new ConfigValidator().validate(config);

        assertTrue(
                errors.contains("companies[0].career_url must be a valid public http/https URL"),
                () -> "Expected private URL validation error but got: " + errors
        );
    }
}
