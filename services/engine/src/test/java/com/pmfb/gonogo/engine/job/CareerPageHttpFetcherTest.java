package com.pmfb.gonogo.engine.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pmfb.gonogo.engine.exception.UnsafeInputException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CareerPageHttpFetcherTest {
    @Test
    void defaultFetcherRejectsLoopbackTargets() {
        CareerPageHttpFetcher fetcher = new CareerPageHttpFetcher();

        assertThrows(
                UnsafeInputException.class,
                () -> fetcher.fetch("http://127.0.0.1:8080/jobs/backend", Duration.ofSeconds(2), "test-agent")
        );
    }

    @Test
    void validatesManualRedirectTargetsBeforeFollowingThem() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "<html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            URI startUri = new URI("http://127.0.0.1:" + server.getAddress().getPort() + "/start");
            URI finalUri = startUri.resolve("/final");
            List<URI> validatedUris = new ArrayList<>();
            CareerPageHttpFetcher fetcher = new CareerPageHttpFetcher(validatedUris::add);

            CareerPageHttpFetcher.FetchResult result = fetcher.fetch(
                    startUri.toString(),
                    Duration.ofSeconds(2),
                    "test-agent"
            );

            assertEquals(List.of(startUri, finalUri), validatedUris);
            assertEquals(finalUri.toString(), result.finalUrl());
            assertEquals("<html><body>ok</body></html>", result.body());
        } finally {
            server.stop(0);
        }
    }
}
