package com.pmfb.gonogo.engine.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class JobPostingExtractorTest {
    private final JobPostingExtractor extractor = new JobPostingExtractor();

    @Test
    void extractsJobCandidatesFromAnchorLinks() {
        String html = """
                <html>
                  <body>
                    <div>
                      <a href="https://example.com/jobs/backend-engineer">Backend Engineer</a>
                      <p>Senior backend engineer role with Go and distributed systems.</p>
                    </div>
                    <div>
                      <a href="https://example.com/jobs/mobile-engineer">Mobile Engineer</a>
                      <p>Mobile engineer building iOS and Android applications.</p>
                    </div>
                    <a href="https://example.com/privacy">Privacy Policy</a>
                  </body>
                </html>
                """;

        List<JobPostingCandidate> results = extractor.extract(html, "https://example.com/careers", 10);

        assertEquals(2, results.size());
        assertEquals("Backend Engineer", results.get(0).title());
        assertEquals("https://example.com/jobs/backend-engineer", results.get(0).url());
        assertEquals("Mobile Engineer", results.get(1).title());
        assertEquals("https://example.com/jobs/mobile-engineer", results.get(1).url());
    }

    @Test
    void limitsToMaxItemsAndDeduplicates() {
        String html = """
                <html>
                  <body>
                    <a href="/jobs/backend-engineer">Backend Engineer</a>
                    <a href="/jobs/backend-engineer">Backend Engineer</a>
                    <a href="/jobs/frontend-engineer">Frontend Engineer</a>
                    <a href="/jobs/platform-engineer">Platform Engineer</a>
                  </body>
                </html>
                """;

        List<JobPostingCandidate> results = extractor.extract(html, "https://example.com/careers", 2);

        assertEquals(2, results.size());
        assertEquals("Backend Engineer", results.get(0).title());
        assertEquals("Frontend Engineer", results.get(1).title());
    }

    @Test
    void extractsSnippetFromContainerContext() {
        String html = """
                <html>
                  <body>
                    <article>
                      <a href="/jobs/platform-engineer">Platform Engineer</a>
                      <p>English-first team building platform product with hybrid work and code review.</p>
                    </article>
                  </body>
                </html>
                """;

        List<JobPostingCandidate> results = extractor.extract(html, "https://example.com/careers", 10);

        assertEquals(1, results.size());
        assertFalse(results.get(0).snippet().isBlank());
        assertTrue(results.get(0).snippet().toLowerCase().contains("english-first"));
    }

    @Test
    void keepsNearbyCardMetadataInSnippet() {
        String html = """
                <html>
                  <body>
                    <section>
                      <div class="job-card">
                        <h4><a href="/companies/lunaris/jobs/software-engineer">Software Engineer</a></h4>
                        <p>Lunaris</p>
                        <p>No Japanese required Apply from abroad No remote Backend</p>
                      </div>
                    </section>
                  </body>
                </html>
                """;

        List<JobPostingCandidate> results = extractor.extract(html, "https://www.tokyodev.com/companies/lunaris", 10);

        assertEquals(1, results.size());
        assertTrue(results.get(0).snippet().contains("No remote"));
        assertTrue(results.get(0).snippet().contains("Apply from abroad"));
    }
}
