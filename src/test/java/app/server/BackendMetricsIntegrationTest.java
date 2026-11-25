package app.server;

import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendMetricsIntegrationTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin server;
    private int metricsPort;

    @BeforeEach
    void start() throws IOException {
        try (ServerSocket available = new ServerSocket(0)) {
            metricsPort = available.getLocalPort();
        }
        server = createServer().start("127.0.0.1", 0);
    }

    private Javalin createServer() {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            BackendMetrics.configure(config, metricsPort);
        });
        app.beforeMatched(ctx -> {
            if (ctx.path().startsWith("/api/private/")) {
                throw new UnauthorizedResponse();
            }
        });
        app.get("/api/orders/{orderId}", ctx -> ctx.result("Order"));
        app.get("/api/private/{accountId}", ctx -> ctx.result("Private"));
        app.get("/fail", ctx -> { throw new IllegalStateException("Test failure"); });
        app.exception(IllegalStateException.class, (error, ctx) -> ctx.status(500));
        return app;
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop();
    }

    @Test
    void recordsFinalStatusesAndRoutePatternsWithoutUserData() throws Exception {
        assertEquals(200, request(server.port(), "/api/orders/alice-order?token=secret-token").statusCode());
        assertEquals(200, request(server.port(), "/api/orders/bob-order").statusCode());
        assertEquals(401, request(server.port(), "/api/private/private-account").statusCode());
        assertEquals(500, request(server.port(), "/fail").statusCode());
        assertEquals(404, request(server.port(), "/missing/private-email@example.test").statusCode());
        assertEquals(404, request(server.port(), "/metrics").statusCode());

        String metrics = scrape();
        assertCount(metrics, "/api/orders/{orderId}", 200, 2);
        assertCount(metrics, "/api/private/{accountId}", 401, 1);
        assertCount(metrics, "/fail", 500, 1);
        assertCount(metrics, "unmatched", 404, 2);
        assertTrue(metrics.contains("mtogo_http_request_duration_seconds_bucket{"));
        assertTrue(metrics.contains("jvm_memory_used_bytes{area=\"heap\"}"));
        for (String privateValue : new String[]{"alice-order", "bob-order", "secret-token", "private-account", "private-email"}) {
            assertFalse(metrics.contains(privateValue), privateValue);
        }
    }

    @Test
    void closesExporterAndUsesAFreshRegistryAfterRestart() throws Exception {
        request(server.port(), "/api/orders/previous-order");
        assertCount(scrape(), "/api/orders/{orderId}", 200, 1);

        server.stop();
        assertThrows(IOException.class, this::scrape);
        server = createServer().start("127.0.0.1", 0);
        assertFalse(scrape().contains("mtogo_http_request_duration_seconds_count{"));
        request(server.port(), "/api/orders/new-order");
        assertCount(scrape(), "/api/orders/{orderId}", 200, 1);
    }

    private void assertCount(String metrics, String route, int status, double expected) {
        String sample = metrics.lines()
                .filter(line -> line.startsWith("mtogo_http_request_duration_seconds_count{"))
                .filter(line -> line.contains("method=\"GET\""))
                .filter(line -> line.contains("route=\"" + route + "\""))
                .filter(line -> line.contains("status=\"" + status + "\""))
                .findFirst().orElseThrow(() -> new AssertionError("Missing request count for " + route + " / " + status));
        assertEquals(expected, Double.parseDouble(sample.substring(sample.lastIndexOf(' ') + 1)));
    }

    private String scrape() throws IOException, InterruptedException {
        HttpResponse<String> response = request(metricsPort, "/metrics");
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private HttpResponse<String> request(int port, String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
