package com.pmfb.gonogo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.exception.JobInputLoadException;
import java.net.InetAddress;
import java.util.List;
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

    @Test
    void rejectsHostnameThatResolvesToPrivateNetworkAddress() throws Exception {
        DirectInputSecurity security = new DirectInputSecurity(host -> new InetAddress[] {
                InetAddress.getByAddress(host, new byte[] {10, 0, 0, 8})
        });

        JobInputLoadException error = assertThrows(
                JobInputLoadException.class,
                () -> security.validateUrl("https://jobs.example.com/backend-engineer")
        );

        assertEquals(List.of("Private or local network addresses are not allowed."), error.errors());
    }

    @Test
    void rejectsHostnameWhenAnyResolvedAddressIsPrivate() throws Exception {
        DirectInputSecurity security = new DirectInputSecurity(host -> new InetAddress[] {
                InetAddress.getByAddress(host, new byte[] {93, (byte) 184, (byte) 216, 34}),
                InetAddress.getByAddress(host, new byte[] {(byte) 192, (byte) 168, 1, 9})
        });

        JobInputLoadException error = assertThrows(
                JobInputLoadException.class,
                () -> security.validateUrl("https://jobs.example.com/backend-engineer")
        );

        assertEquals(List.of("Private or local network addresses are not allowed."), error.errors());
    }

    @Test
    void allowsHostnameThatResolvesOnlyToPublicAddress() throws Exception {
        DirectInputSecurity security = new DirectInputSecurity(host -> new InetAddress[] {
                InetAddress.getByAddress(host, new byte[] {93, (byte) 184, (byte) 216, 34})
        });

        assertEquals(
                "https://jobs.example.com/backend-engineer",
                security.validateUrl("https://jobs.example.com/backend-engineer")
        );
    }
}
