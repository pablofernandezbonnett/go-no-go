package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.JobInputLoadException;
import com.pmfb.gonogo.engine.exception.UnsafeInputException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

final class DirectInputSecurity {
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String LOCALHOST_HOSTNAME = "localhost";
    private static final String LOCAL_SUFFIX = ".local";
    private static final String LOCALHOST_SUFFIX = ".localhost";
    private static final String INTERNAL_SUFFIX = ".internal";
    private static final int MAX_RAW_TEXT_LENGTH = 100_000;
    private static final Pattern UNSAFE_CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final Pattern IPV4_LITERAL_PATTERN = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    private static final Pattern HTML_INPUT_PATTERN = Pattern.compile(
            "(?is)<(?:!doctype|html|body|head|script|style|div|p|span|section|article|main|a|ul|ol|li|table|form|h[1-6])\\b"
    );

    String validateUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isBlank()) {
            throw new JobInputLoadException(List.of("URL cannot be blank."));
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new JobInputLoadException(List.of("URL is invalid."));
        }

        try {
            ensureSafeHttpUri(uri);
        } catch (UnsafeInputException e) {
            throw new JobInputLoadException(List.of(e.getMessage()));
        }
        return uri.toString();
    }

    void ensureSafeHttpUri(URI uri) throws UnsafeInputException {
        if (uri == null) {
            throw new UnsafeInputException("URL is invalid.");
        }

        String scheme = normalize(uri.getScheme());
        if (!HTTP_SCHEME.equals(scheme) && !HTTPS_SCHEME.equals(scheme)) {
            throw new UnsafeInputException("Only public http/https URLs are allowed.");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new UnsafeInputException("URLs with embedded credentials are not allowed.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeInputException("URL host is missing or invalid.");
        }

        String normalizedHost;
        try {
            normalizedHost = normalizeHost(host);
        } catch (IllegalArgumentException e) {
            throw new UnsafeInputException("URL host is missing or invalid.");
        }
        if (normalizedHost.equals(LOCALHOST_HOSTNAME)
                || normalizedHost.endsWith(LOCALHOST_SUFFIX)
                || normalizedHost.endsWith(LOCAL_SUFFIX)
                || normalizedHost.endsWith(INTERNAL_SUFFIX)) {
            throw new UnsafeInputException("Local or internal hosts are not allowed.");
        }
        if (isIpLiteral(normalizedHost) && isPrivateOrLocalAddress(normalizedHost)) {
            throw new UnsafeInputException("Private or local network addresses are not allowed.");
        }
    }

    String sanitizeRawText(String rawText) {
        String cleaned = rawText == null ? "" : UNSAFE_CONTROL_CHARS.matcher(rawText).replaceAll("");
        cleaned = cleaned.trim();
        if (cleaned.isBlank()) {
            throw new JobInputLoadException(List.of("Raw text is empty."));
        }
        if (cleaned.length() > MAX_RAW_TEXT_LENGTH) {
            throw new JobInputLoadException(List.of(
                    "Raw text is too large for ad-hoc evaluation (max " + MAX_RAW_TEXT_LENGTH + " characters)."
            ));
        }

        if (looksLikeHtml(cleaned)) {
            cleaned = sanitizeHtmlToText(cleaned);
        }
        if (cleaned.isBlank()) {
            throw new JobInputLoadException(List.of("Raw text is empty after sanitization."));
        }
        return cleaned;
    }

    private boolean looksLikeHtml(String value) {
        return HTML_INPUT_PATTERN.matcher(value).find();
    }

    private String sanitizeHtmlToText(String html) {
        Document document = Jsoup.parse(html);
        document.select("script,style,noscript,iframe,object,embed,svg,canvas").remove();
        String text = document.body() != null ? document.body().wholeText() : document.wholeText();
        text = UNSAFE_CONTROL_CHARS.matcher(text).replaceAll("");
        return text.trim();
    }

    private boolean isIpLiteral(String host) {
        return IPV4_LITERAL_PATTERN.matcher(host).matches() || host.contains(":");
    }

    private boolean isPrivateOrLocalAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return true;
            }
            if (address instanceof Inet4Address ipv4Address) {
                return isPrivateOrLocalIpv4(ipv4Address.getAddress());
            }
            if (address instanceof Inet6Address ipv6Address) {
                return isPrivateOrLocalIpv6(ipv6Address.getAddress());
            }
            return false;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private boolean isPrivateOrLocalIpv4(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);
        return first == 0
                || first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19));
    }

    private boolean isPrivateOrLocalIpv6(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        return (first & 0xfe) == 0xfc;
    }

    private String normalizeHost(String host) {
        return IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
