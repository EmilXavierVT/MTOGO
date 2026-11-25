package app.server;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMemoryMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class BackendMetrics {
    private final PrometheusRegistry registry = new PrometheusRegistry();
    private final Histogram requestDuration = Histogram.builder()
            .name("mtogo_http_request_duration_seconds")
            .help("Duration of completed MTOGO HTTP requests in seconds.")
            .labelNames("method", "route", "status")
            .classicOnly()
            .register(registry);

    private HTTPServer exporter;

    private BackendMetrics() {
        JvmMemoryMetrics.builder().register(registry);
    }

    public static void configure(JavalinConfig config, int port) {
        BackendMetrics metrics = new BackendMetrics();
        config.requestLogger.http(metrics::recordRequest);
        config.events.serverStarting(() -> metrics.start(port));
        config.events.serverStartFailed(metrics::stop);
        config.events.serverStopping(metrics::stop);
    }

    private void recordRequest(Context ctx, float durationMs) {
        // Use the registered route pattern to keep label values bounded.
        String path = ctx.handlerType() == HandlerType.BEFORE ? "" : ctx.endpointHandlerPath();
        String route = path.startsWith("/") ? path : "unmatched";
        requestDuration.labelValues(ctx.method().name(), route, Integer.toString(ctx.statusCode()))
                .observe(durationMs / 1000.0);
    }

    private void start(int port) {
        try {
            exporter = HTTPServer.builder().port(port).registry(registry).buildAndStart();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the Prometheus exporter on port " + port, e);
        }
    }

    private void stop() {
        if (exporter != null) {
            exporter.close();
        }
    }
}
