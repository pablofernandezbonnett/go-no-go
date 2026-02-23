package com.pmfb.gonogo.engine.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.job.JobInput;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

final class DecisionEngineV1RegressionFixturesTest {
    private static final String FIXTURE_RESOURCE = "fixtures/decision-regression/cases.yaml";
    private static final DecisionEngineV1 ENGINE = new DecisionEngineV1();

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void matchesExpectedVerdictAndSignals(String caseId, RegressionCase fixture) {
        EvaluationResult result = ENGINE.evaluate(
                fixture.job(),
                DecisionTestFixtureSupport.defaultPersona(),
                DecisionTestFixtureSupport.defaultConfig()
        );

        assertEquals(
                fixture.expectedVerdict(),
                result.verdict(),
                "verdict mismatch for fixture " + caseId
        );

        for (String signal : fixture.expectedPositiveSignalsContains()) {
            assertTrue(
                    result.positiveSignals().contains(signal),
                    "expected positive signal '" + signal + "' in fixture " + caseId
            );
        }
        for (String signal : fixture.expectedRiskSignalsContains()) {
            assertTrue(
                    result.riskSignals().contains(signal),
                    "expected risk signal '" + signal + "' in fixture " + caseId
            );
        }
        for (String reason : fixture.expectedHardRejectReasonsContains()) {
            assertTrue(
                    result.hardRejectReasons().contains(reason),
                    "expected hard reject reason '" + reason + "' in fixture " + caseId
            );
        }
    }

    static Stream<Arguments> cases() {
        return loadFixtures().stream().map(item -> Arguments.of(item.id(), item));
    }

    @SuppressWarnings("unchecked")
    private static List<RegressionCase> loadFixtures() {
        InputStream input = DecisionEngineV1RegressionFixturesTest.class.getClassLoader()
                .getResourceAsStream(FIXTURE_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Fixture resource not found: " + FIXTURE_RESOURCE);
        }

        Object rootObj = new Yaml().load(input);
        if (!(rootObj instanceof Map<?, ?> root)) {
            throw new IllegalStateException("Fixture root must be a map: " + FIXTURE_RESOURCE);
        }

        Object casesObj = root.get("cases");
        if (!(casesObj instanceof List<?> rawCases)) {
            throw new IllegalStateException("Fixture 'cases' must be a list: " + FIXTURE_RESOURCE);
        }

        List<RegressionCase> fixtures = new ArrayList<>();
        for (Object entry : rawCases) {
            if (!(entry instanceof Map<?, ?> caseMap)) {
                throw new IllegalStateException("Each fixture case must be an object.");
            }

            String id = asString(caseMap.get("id"));
            Map<String, Object> jobMap = asMap(caseMap.get("job"), "job");
            Map<String, Object> expectedMap = asMap(caseMap.get("expected"), "expected");

            JobInput job = new JobInput(
                    asString(jobMap.get("company_name")),
                    asString(jobMap.get("title")),
                    asString(jobMap.get("location")),
                    asString(jobMap.get("salary_range")),
                    asString(jobMap.get("remote_policy")),
                    asString(jobMap.get("description"))
            );

            Verdict expectedVerdict = Verdict.valueOf(asString(expectedMap.get("verdict")).toUpperCase());
            List<String> positiveContains = asStringList(expectedMap.get("positive_signals_contains"));
            List<String> riskContains = asStringList(expectedMap.get("risk_signals_contains"));
            List<String> hardRejectContains = asStringList(expectedMap.get("hard_reject_reasons_contains"));

            fixtures.add(new RegressionCase(
                    id,
                    job,
                    expectedVerdict,
                    positiveContains,
                    riskContains,
                    hardRejectContains
            ));
        }
        return fixtures;
    }

    private static String asString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Field '" + field + "' must be an object.");
        }
        return (Map<String, Object>) map;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String text = asString(item);
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private record RegressionCase(
            String id,
            JobInput job,
            Verdict expectedVerdict,
            List<String> expectedPositiveSignalsContains,
            List<String> expectedRiskSignalsContains,
            List<String> expectedHardRejectReasonsContains
    ) {
    }
}
