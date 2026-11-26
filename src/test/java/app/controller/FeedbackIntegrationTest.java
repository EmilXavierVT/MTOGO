package app.controller;

import app.App;
import app.config.CourierTestDatabase;
import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Order;
import app.features.EnvironmentFeatureFlags;
import app.features.FeatureCatalog;
import app.support.FeedbackTestData;
import app.support.SecurityTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackIntegrationTest {
    private static final String SECRET = "feedback-integration-test-secret-at-least-32-bytes";
    private static final String RATINGS = "{\"foodRating\":1,\"overallExperienceRating\":5,\"courierRating\":3}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private EntityManagerFactory emf;
    private FeedbackTestData data;
    private Javalin app;

    @BeforeEach
    void setup() {
        emf = CourierTestDatabase.create();
        data = FeedbackTestData.create(emf);
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
        app = App.createApp(emf, SECRET, FeatureCatalog.APPLICATION,
                new EnvironmentFeatureFlags(FeatureCatalog.APPLICATION.registered(),
                        name -> Boolean.toString(enabled), name -> fail())).start("127.0.0.1", 0);
    }

    @Test
    void savesAllThreeRatingsOnceForThePersistedCourierWithoutChangingBonus() throws Exception {
        start(true);
        String customer = login("customer@example.test");
        JsonNode before = json(200, request("GET", path(), customer, null));
        assertTrue(before.get("feedback").isNull());
        assertEquals(data.courier().id().toString(), before.get("courierId").asText());
        assertEquals("Assigned courier", before.get("courierName").asText());
        String suppliedIdentities = RATINGS.replace("}", ",\"courierId\":\"" + UUID.randomUUID()
                + "\",\"customerId\":\"" + data.accounts().otherCustomer() + "\"}");
        HttpResponse<String> response = request("POST", path(), customer, suppliedIdentities);
        JsonNode saved = json(201, response);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(data.deliveryId().toString(), saved.get("deliveryId").asText());
        assertEquals(data.courier().id().toString(), saved.get("courierId").asText());
        assertEquals(1, saved.at("/feedback/foodRating").asInt());
        assertEquals(5, saved.at("/feedback/overallExperienceRating").asInt());
        assertEquals(3, saved.at("/feedback/courierRating").asInt());
        assertTrue(saved.at("/feedback/id").isTextual());
        assertEquals(saved, json(200, request("GET", path(), customer, null)));
        json(409, request("POST", path(), customer, RATINGS));
        assertEquals(1, feedbackCount());
        assertBonusUnchanged();
        assertFalse(response.body().contains("password"));
        assertFalse(response.body().contains("earnedBonus"));
        assertTrue(json(200, request("GET", "/api/features", customer, null)).get("customerFeedback").asBoolean());
    }

    @Test
    void authenticationRoleAndStoredOwnershipAreIndependentOfTheFlag() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            start(enabled);
            for (String method : List.of("GET", "POST")) {
                String body = method.equals("POST") ? RATINGS : null;
                json(401, request(method, path(), null, body));
                json(401, request(method, path(), "invalid-token", body));
                for (String role : List.of("courier", "admin", "restaurant_owner")) {
                    json(403, request(method, path(), login(role + "@example.test"), body));
                }
                json(403, request(method, path(), login("other-customer@example.test"), body));
                String customer = login("customer@example.test");
                json(404, request(method, "/api/customer/orders/" + UUID.randomUUID() + "/feedback", customer, body));
                json(400, request(method, "/api/customer/orders/invalid/feedback", customer, body));
                if (!enabled) {
                    assertEquals("Feature unavailable", json(404, request(method, path(), customer, body)).get("error").asText());
                }
            }
            app.stop();
            app = null;
        }
        assertEquals(0, feedbackCount());
        assertBonusUnchanged();
    }

    @Test
    void disabledFeatureHidesExistingFeedbackAndPreservesOrderHistoryAndCompletion() throws Exception {
        start(true);
        String customer = login("customer@example.test");
        JsonNode saved = json(201, request("POST", path(), customer, RATINGS));
        app.stop();
        start(false);
        assertFalse(json(200, request("GET", "/api/features", customer, null)).get("customerFeedback").asBoolean());
        json(404, request("GET", path(), customer, null));
        json(404, request("POST", path(), customer, RATINGS));
        json(200, request("GET", "/api/customer/orders/" + data.orderId(), customer, null));
        assertEquals(1, json(200, request("GET", "/api/customer/orders", customer, null)).size());
        assertEquals(1, feedbackCount());
        app.stop();
        start(true);
        assertEquals(saved, json(200, request("GET", path(), customer, null)));
        assertBonusUnchanged();
    }

    @Test
    void refusesEveryPrematureOrderStateAndIncompleteOrMissingDelivery() throws Exception {
        start(true);
        String customer = login("customer@example.test");
        for (Status status : Status.values()) {
            if (status == Status.DELIVERED) continue;
            emf.runInTransaction(em -> em.find(Order.class, data.orderId()).setStatus(status));
            json(409, request("POST", path(), customer, RATINGS));
            json(409, request("GET", path(), customer, null));
        }
        emf.runInTransaction(em -> {
            em.find(Order.class, data.orderId()).setStatus(Status.DELIVERED);
            em.find(Delivery.class, data.deliveryId()).setStatus(Status.UNDER_DELIVERY);
        });
        json(409, request("POST", path(), customer, RATINGS));
        emf.runInTransaction(em -> {
            Delivery delivery = em.find(Delivery.class, data.deliveryId());
            delivery.setStatus(Status.DELIVERED);
            delivery.setDeliveryTime(null);
        });
        json(409, request("POST", path(), customer, RATINGS));
        emf.runInTransaction(em -> em.remove(em.find(Delivery.class, data.deliveryId())));
        json(409, request("POST", path(), customer, RATINGS));
        assertEquals(0, feedbackCount());
    }

    @Test
    void requiresThreeWholeJsonNumbersInRangeWithoutCoercionOrPartialWrites() throws Exception {
        start(true);
        String customer = login("customer@example.test");
        for (String field : List.of("foodRating", "overallExperienceRating", "courierRating")) {
            for (String invalid : List.of("0", "6", "-1", "1.5", "\"3\"", "true", "null", "[]", "999999999999")) {
                var payload = mapper.readTree(RATINGS).deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set(field, mapper.readTree(invalid));
                json(400, request("POST", path(), customer, payload.toString()));
            }
            var missing = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(RATINGS);
            missing.remove(field);
            json(400, request("POST", path(), customer, missing.toString()));
        }
        for (String invalid : List.of("null", "[]", "{", "", RATINGS + " {}")) {
            json(400, request("POST", path(), customer, invalid));
        }
        assertEquals(0, feedbackCount());
        assertBonusUnchanged();
    }

    private long feedbackCount() {
        return emf.callInTransaction(em -> em.createQuery("select count(f) from Feedback f", Long.class).getSingleResult());
    }

    private void assertBonusUnchanged() {
        emf.runInTransaction(em -> {
            Delivery delivery = em.find(Delivery.class, data.deliveryId());
            assertEquals(new BigDecimal("2.75"), delivery.getEarnedBonus());
            assertEquals(Status.DELIVERED, delivery.getStatus());
            assertEquals(Status.DELIVERED, delivery.getOrder().getStatus());
            assertEquals(new BigDecimal("13.75"), delivery.getOrder().getCommission());
        });
    }

    private String path() {
        return "/api/customer/orders/" + data.orderId() + "/feedback";
    }

    private JsonNode json(int status, HttpResponse<String> response) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private String login(String email) throws Exception {
        return json(200, request("POST", "/api/auth/login", null,
                mapper.writeValueAsString(Map.of("email", email, "password", SecurityTestData.PASSWORD))))
                .get("token").asText();
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
