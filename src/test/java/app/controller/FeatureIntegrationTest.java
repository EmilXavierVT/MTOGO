package app.controller;

import app.App;
import app.config.H2TestDatabase;
import app.entities.enums.Role;
import app.features.EnvironmentFeatureFlags;
import app.features.FeatureCatalog;
import app.features.FeatureFlags;
import app.features.FeatureKey;
import app.security.Ownership;
import app.support.SecurityTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeatureIntegrationTest {
    private static final String SECRET = "feature-integration-test-secret-at-least-32-bytes";
    private static final FeatureKey TEST = new FeatureKey("MTOGO_FEATURE_TEST");
    private static final FeatureKey INTERNAL = new FeatureKey("MTOGO_FEATURE_INTERNAL");
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger writes = new AtomicInteger();
    private EntityManagerFactory emf;
    private SecurityTestData.Fixtures fixtures;
    private Javalin app;

    @BeforeEach
    void setup() {
        emf = H2TestDatabase.create();
        fixtures = SecurityTestData.populate(emf);
    }

    @AfterEach
    void stop() {
        try {
            if (app != null) app.stop();
        } finally {
            emf.close();
        }
    }

    private void start(boolean enabled) {
        FeatureCatalog catalog = new FeatureCatalog(Set.of(TEST, INTERNAL), Map.of("testCapability", TEST));
        FeatureFlags flags = new EnvironmentFeatureFlags(catalog.registered(),
                name -> name.equals(TEST.environmentVariable()) ? Boolean.toString(enabled) : "true",
                name -> fail("Invalid fixture configuration"));
        app = App.createApp(emf, SECRET, catalog, flags);
        // A future capability's registration pattern, using the real application's security middleware.
        app.post("/api/test/features/{customerId}", ctx -> {
            Ownership.requireCustomer(ctx.attribute("user"), UUID.fromString(ctx.pathParam("customerId")));
            flags.requireEnabled(TEST);
            ctx.json(Map.of("writes", writes.incrementAndGet()));
        }, Role.CUSTOMER);
        app.start("127.0.0.1", 0);
    }

    @Test
    void everyRoleReceivesOnlyAllowlistedBooleansWithoutCaching() throws Exception {
        start(true);
        for (Role role : Role.values()) {
            HttpResponse<String> response = request("GET", "/api/features", login(role.name() + "@example.test"), null);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            JsonNode body = mapper.readTree(response.body());
            assertEquals(1, body.size());
            assertTrue(body.get("testCapability").isBoolean());
            assertTrue(body.get("testCapability").asBoolean());
            assertFalse(response.body().contains("MTOGO_FEATURE_"));
            assertFalse(response.body().contains("SECRET"));
        }
    }

    @Test
    void disabledFlagPreservesAuthenticationRoleAndOwnershipChecksWithoutWrites() throws Exception {
        start(false);
        String customer = login("customer@example.test");
        String ownPath = "/api/test/features/" + fixtures.accounts().get(Role.CUSTOMER);
        assertEquals(401, request("POST", ownPath, null, null).statusCode());
        assertEquals(403, request("POST", ownPath, login("courier@example.test"), null).statusCode());
        assertEquals(403, request("POST", ownPath, login("other-customer@example.test"), null).statusCode());
        HttpResponse<String> disabled = request("POST", ownPath, customer, null);
        assertEquals(404, disabled.statusCode(), disabled.body());
        assertEquals("Feature unavailable", mapper.readTree(disabled.body()).get("error").asText());
        assertFalse(mapper.readTree(request("GET", "/api/features", customer, null).body()).get("testCapability").asBoolean());
        assertEquals(0, writes.get());
        assertEquals(200, request("GET", "/api/auth/me", customer, null).statusCode());
    }

    @Test
    void enabledFlagAllowsOnlyTheAuthorizedOwnersOperation() throws Exception {
        start(true);
        String path = "/api/test/features/" + fixtures.accounts().get(Role.CUSTOMER);
        assertEquals(200, request("POST", path, login("customer@example.test"), null).statusCode());
        assertEquals(403, request("POST", path, login("other-customer@example.test"), null).statusCode());
        assertEquals(403, request("POST", path, login("admin@example.test"), null).statusCode());
        assertEquals(1, writes.get());
    }

    @Test
    void defaultApplicationExposesAnEmptyCatalogAndRequiresValidAuthentication() throws Exception {
        app = App.createApp(emf, SECRET).start("127.0.0.1", 0);
        assertEquals(401, request("GET", "/api/features", null, null).statusCode());
        assertEquals(401, request("GET", "/api/features", "invalid", null).statusCode());
        HttpResponse<String> response = request("GET", "/api/features", login("customer@example.test"), null);
        assertEquals(200, response.statusCode());
        assertEquals(mapper.createObjectNode(), mapper.readTree(response.body()));
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = request("POST", "/api/auth/login", null,
                mapper.writeValueAsString(Map.of("email", email, "password", SecurityTestData.PASSWORD)));
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).get("token").asText();
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
