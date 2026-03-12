package com.pmfb.gonogo.engine.job;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class CareerPageHttpFetcher implements CareerPageFetcher {
    private static final int MAX_REDIRECTS = 6;
    @FunctionalInterface
    public interface UriSafetyValidator {
        void validate(URI uri) throws IOException;
    }

    private static final UriSafetyValidator ALLOW_ALL_URIS = uri -> {
    };
    private final HttpClient client;
    private final UriSafetyValidator uriSafetyValidator;

    public CareerPageHttpFetcher() {
        this(ALLOW_ALL_URIS);
    }

    public CareerPageHttpFetcher(UriSafetyValidator uriSafetyValidator) {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.uriSafetyValidator = uriSafetyValidator == null ? ALLOW_ALL_URIS : uriSafetyValidator;
    }

    @Override
    public FetchResult fetch(String url, Duration timeout, String userAgent) throws IOException, InterruptedException {
        URI currentUri = URI.create(url);
        HttpResponse<String> response = sendRequest(currentUri, timeout, userAgent);
        int redirects = 0;

        while (isRedirectStatus(response.statusCode()) && redirects < MAX_REDIRECTS) {
            Optional<String> locationHeader = response.headers().firstValue("location");
            if (locationHeader.isEmpty()) {
                break;
            }
            URI redirected = currentUri.resolve(locationHeader.get());
            redirected = normalizeRedirectUri(currentUri, redirected);
            if (redirected.equals(currentUri)) {
                break;
            }
            currentUri = redirected;
            response = sendRequest(currentUri, timeout, userAgent);
            redirects++;
        }

        URI responseUri = response.uri() != null ? response.uri() : currentUri;
        return new FetchResult(response.statusCode(), responseUri.toString(), response.body());
    }

    private HttpResponse<String> sendRequest(
            URI uri,
            Duration timeout,
            String userAgent
    ) throws IOException, InterruptedException {
        uriSafetyValidator.validate(uri);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private URI normalizeRedirectUri(URI source, URI target) {
        if (source == null || target == null) {
            return target;
        }
        String sourceScheme = source.getScheme();
        String targetScheme = target.getScheme();
        String sourceHost = source.getHost();
        String targetHost = target.getHost();
        boolean downgradeToHttp = "https".equalsIgnoreCase(sourceScheme)
                && "http".equalsIgnoreCase(targetScheme)
                && sourceHost != null
                && sourceHost.equalsIgnoreCase(targetHost);
        if (!downgradeToHttp) {
            return target;
        }
        try {
            return new URI(
                    "https",
                    target.getUserInfo(),
                    target.getHost(),
                    target.getPort(),
                    target.getPath(),
                    target.getQuery(),
                    target.getFragment()
            );
        } catch (java.net.URISyntaxException e) {
            return target;
        }
    }

    public record FetchResult(
            int statusCode,
            String finalUrl,
            String body
    ) {
    }
}
