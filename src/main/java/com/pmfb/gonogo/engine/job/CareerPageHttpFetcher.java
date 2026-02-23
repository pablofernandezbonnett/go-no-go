package com.pmfb.gonogo.engine.job;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class CareerPageHttpFetcher implements CareerPageFetcher {
    private final HttpClient client;

    public CareerPageHttpFetcher() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public FetchResult fetch(String url, Duration timeout, String userAgent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        URI responseUri = response.uri() != null ? response.uri() : URI.create(url);
        return new FetchResult(response.statusCode(), responseUri.toString(), response.body());
    }

    public record FetchResult(
            int statusCode,
            String finalUrl,
            String body
    ) {
    }
}
