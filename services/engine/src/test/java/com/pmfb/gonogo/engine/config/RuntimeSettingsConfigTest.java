package com.pmfb.gonogo.engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.decision.RankingStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RuntimeSettingsConfigTest {
    @Test
    void loaderReadsRuntimeFetchDefaultsFromYaml() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-runtime-config-test");
        writeBaseConfig(tempDir);
        Files.writeString(
                tempDir.resolve("runtime.yaml"),
                """
                        fetch_web:
                          timeout_seconds: 45
                          user_agent: "custom-agent/1.0"
                          retries: 5
                          backoff_millis: 900
                          request_delay_millis: 1800
                          max_concurrency: 7
                          max_concurrency_per_host: 2
                          robots_mode: warn
                          cache_ttl_minutes: 30
                        evaluation:
                          max_concurrency: 6
                        """
        );

        EngineConfig config = new YamlConfigLoader(tempDir).load();
        FetchWebRuntimeConfig runtime = config.runtimeSettings().fetchWeb();
        EvaluationRuntimeConfig evaluation = config.runtimeSettings().evaluation();

        assertEquals(45, runtime.timeoutSeconds());
        assertEquals("custom-agent/1.0", runtime.userAgent());
        assertEquals(5, runtime.retries());
        assertEquals(900, runtime.backoffMillis());
        assertEquals(1800, runtime.requestDelayMillis());
        assertEquals(7, runtime.maxConcurrency());
        assertEquals(2, runtime.maxConcurrencyPerHost());
        assertEquals("warn", runtime.robotsMode());
        assertEquals(30, runtime.cacheTtlMinutes());
        assertEquals(6, evaluation.maxConcurrency());
    }

    @Test
    void validatorRejectsInvalidRuntimeFetchDefaults() {
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
                        List.of("hybrid_partial"),
                        java.util.Map.of(),
                        RankingStrategy.BY_SCORE,
                        0
                )),
                List.of(),
                List.of(),
                new RuntimeSettingsConfig(
                        new FetchWebRuntimeConfig(
                                4,
                                " ",
                                -1,
                                -1,
                                -1,
                                0,
                                0,
                                "broken",
                                0
                        ),
                        new EvaluationRuntimeConfig(0)
                )
        );

        List<String> errors = new ConfigValidator().validate(config);

        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.timeout_seconds")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.user_agent")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.retries")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.backoff_millis")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.request_delay_millis")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.max_concurrency")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.max_concurrency_per_host")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.robots_mode")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.fetch_web.cache_ttl_minutes")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("runtime.evaluation.max_concurrency")));
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
