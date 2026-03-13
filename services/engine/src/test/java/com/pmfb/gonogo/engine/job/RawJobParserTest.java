package com.pmfb.gonogo.engine.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RawJobParserTest {
    private final RawJobParser parser = new RawJobParser();

    @Test
    void extractsLabeledFields() {
        String rawText = """
                Company: Money Forward
                Title: Senior Backend Engineer
                Location: Tokyo
                Salary: JPY 10,000,000 - 14,000,000
                Work style: Hybrid (2 days onsite, 3 days remote)
                English-first team with strong engineering culture.
                """;

        RawJobExtractionResult result = parser.parse(rawText, null, null);

        assertEquals("Money Forward", result.jobInput().companyName());
        assertEquals("Senior Backend Engineer", result.jobInput().title());
        assertEquals("Tokyo", result.jobInput().location());
        assertEquals("JPY 10,000,000 - 14,000,000", result.jobInput().salaryRange());
        assertEquals("Hybrid", result.jobInput().remotePolicy());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void appliesOverridesAndFallbacks() {
        String rawText = """
                # Platform Engineer
                We are an international team based in Tokyo.
                Compensation is competitive.
                Onsite collaboration is expected in office.
                """;

        RawJobExtractionResult result = parser.parse(rawText, "Mercari", "Backend Engineer");

        assertEquals("Mercari", result.jobInput().companyName());
        assertEquals("Backend Engineer", result.jobInput().title());
        assertEquals("Tokyo", result.jobInput().location());
        assertEquals("TBD", result.jobInput().salaryRange());
        assertEquals("Onsite-only", result.jobInput().remotePolicy());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Salary range not found")));
    }

    @Test
    void detectsSalaryFromInlineCurrencyPattern() {
        String rawText = """
                Role: Backend Engineer
                You will work in a global team in Japan.
                Annual package around JPY 8,500,000 to JPY 12,000,000 depending on experience.
                Remote setup available.
                """;

        RawJobExtractionResult result = parser.parse(rawText, "SmartNews", null);

        assertEquals("SmartNews", result.jobInput().companyName());
        assertEquals("Backend Engineer", result.jobInput().title());
        assertEquals("Japan", result.jobInput().location());
        assertEquals("JPY 8,500,000 to JPY 12,000,000", result.jobInput().salaryRange());
        assertEquals("Remote", result.jobInput().remotePolicy());
    }

    @Test
    void extractsBulletedAnnualSalaryRangeLabels() {
        String rawText = """
                Title: Application Engineer
                Location: Ariake Headquarters, Tokyo
                ・Annual Salary Range: ¥6.56 million - ¥21.24 million (Monthly Salary: ¥410,000 - ¥1,180,000)
                ・Work Style: Hybrid
                English is used on a daily basis.
                """;

        RawJobExtractionResult result = parser.parse(rawText, "Fast Retailing", null);

        assertEquals("Fast Retailing", result.jobInput().companyName());
        assertEquals("Application Engineer", result.jobInput().title());
        assertEquals("Ariake Headquarters, Tokyo", result.jobInput().location());
        assertEquals(
                "¥6.56 million - ¥21.24 million (Monthly Salary: ¥410,000 - ¥1,180,000)",
                result.jobInput().salaryRange()
        );
        assertEquals("Hybrid", result.jobInput().remotePolicy());
    }

    @Test
    void doesNotInferOnsiteOnlyFromGenericOfficeMentionsInNarrativeText() {
        String rawText = """
                Title: Application Engineer
                Location: Tokyo
                English is used on a daily basis in our headquarters environment.
                We provide support to become comfortable with English.
                """;

        RawJobExtractionResult result = parser.parse(rawText, "Fast Retailing", null);

        assertEquals("Unspecified", result.jobInput().remotePolicy());
    }
}
