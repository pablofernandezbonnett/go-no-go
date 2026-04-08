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

    @Test
    void infersCompanyAndTitleFromLeadingBilingualLines() {
        String rawText = """
                Preferred Networks
                Product and Service Development Engineer / プロダクト・サービス開発エンジニア
                勤務地: Otemachi, Tokyo
                勤務形態: Remote work system available (limited to work in Japan)
                給与: Experience, performance, skills, contribution are taken into consideration.
                """;

        RawJobExtractionResult result = parser.parse(rawText, null, null);

        assertEquals("Preferred Networks", result.jobInput().companyName());
        assertEquals(
                "Product and Service Development Engineer / プロダクト・サービス開発エンジニア",
                result.jobInput().title()
        );
        assertEquals("Otemachi, Tokyo", result.jobInput().location());
        assertEquals(
                "Experience, performance, skills, contribution are taken into consideration.",
                result.jobInput().salaryRange()
        );
        assertEquals("Remote", result.jobInput().remotePolicy());
    }

    @Test
    void treatsNoRemoteAsOnsiteOnlyEvenWhenInterviewMentionsRemote() {
        String rawText = """
                Title: Software Engineer
                Location: Tokyo
                No remote
                Hiring Process:
                In-person or remote interview with the team
                """;

        RawJobExtractionResult result = parser.parse(rawText, "Lunaris", null);

        assertEquals("Onsite-only", result.jobInput().remotePolicy());
    }

    @Test
    void treatsPartiallyRemoteAsHybrid() {
        String rawText = """
                Title: Backend Engineer
                Location: Tokyo
                Partially remote
                English-first team.
                """;

        RawJobExtractionResult result = parser.parse(rawText, "Example Co", null);

        assertEquals("Hybrid", result.jobInput().remotePolicy());
    }

    @Test
    void infersCompanyFromAboutHeadingAndTitleFromNarrativeSentence() {
        String rawText = """
                About the job
                About Woven By Toyota

                TEAM
                We are looking for an experienced Software Engineer to help build and evolve our Experimentation Platform.
                Work Hours - Flexible working time
                """;

        RawJobExtractionResult result = parser.parse(rawText, null, null);

        assertEquals("Woven By Toyota", result.jobInput().companyName());
        assertEquals("Software Engineer", result.jobInput().title());
    }
}
