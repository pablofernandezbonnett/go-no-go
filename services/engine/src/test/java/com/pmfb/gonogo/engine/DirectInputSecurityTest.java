package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DirectInputSecurityTest {
    private final DirectInputSecurity security = new DirectInputSecurity();

    @Test
    void sanitizesHtmlAndScriptContentIntoPlainText() {
        String sanitized = security.sanitizeRawText("""
                <html>
                  <body>
                    <h1>Senior Backend Engineer</h1>
                    <p>Salary: JPY 9,000,000 - 12,000,000</p>
                    <script>alert('xss')</script>
                  </body>
                </html>
                """);

        assertTrue(sanitized.contains("Senior Backend Engineer"));
        assertTrue(sanitized.contains("Salary: JPY 9,000,000 - 12,000,000"));
        assertFalse(sanitized.contains("<script"));
        assertFalse(sanitized.contains("alert('xss')"));
    }
}
