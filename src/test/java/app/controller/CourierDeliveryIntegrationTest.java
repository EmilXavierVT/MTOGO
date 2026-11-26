package app.controller;

import app.App;
import app.config.CourierTestDatabase;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Restaurant;
import app.entities.users.Courier;
import app.entities.users.User;
import app.support.SecurityTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierDeliveryIntegrationTest {
    private static final String AVAILABLE = "/api/courier/orders/available";
    private static final String ACTIVE = "/api/courier/delivery/active";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private EntityManagerFactory emf;
    private Javalin server;
    private SecurityTestData.Fixtures fixtures;

    @BeforeEach
    void start() {
        emf = CourierTestDatabase.create();
        fixtures = SecurityTestData.populate(emf);
        emf.runInTransaction(em -> {
            for (String name : List.of("first", "second")) {
                Courier courier = new Courier();
                courier.setName(name);
                courier.setEmail(name + "-courier@example.test");
                courier.setPasswordHash(em.find(User.class, fixtures.accounts().get(Role.CUSTOMER)).getPasswordHash());
                courier.setRole(Role.COURIER);
                em.persist(courier);
            }
        });
        server = App.createApp(emf, "courier-test-only-secret-at-least-32-bytes").start("127.0.0.1", 0);
    }

    @AfterEach
    void stop() {
        try {
            if (server != null) server.stop();
        } finally {
            if (emf != null) emf.close();
        }
    }

    @Test
    void couriersSeeOnlyUnassignedReadyOrdersOldestFirstWithoutCustomerDetails() throws Exception {
        UUID newer = order(Status.READY_FOR_DELIVERY, LocalDateTime.of(2026, 11, 26, 12, 30));
        UUID older = order(Status.READY_FOR_DELIVERY, LocalDateTime.of(2026, 11, 26, 12, 0));
        UUID assigned = order(Status.READY_FOR_DELIVERY, LocalDateTime.of(2026, 11, 26, 11, 0));
        for (Status status : Status.values()) {
            if (status != Status.READY_FOR_DELIVERY) order(status, LocalDateTime.of(2026, 11, 26, 10, 0));
        }
        json(201, request("POST", action(assigned, "accept"), login("second-courier@example.test")));

        HttpResponse<String> response = request("GET", AVAILABLE, login("first-courier@example.test"));
        JsonNode available = json(200, response);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(2, available.size());
        assertEquals(older.toString(), available.at("/0/orderId").asText());
        assertEquals(newer.toString(), available.at("/1/orderId").asText());
        assertEquals("First restaurant", available.at("/0/restaurant/name").asText());
        assertEquals("First address", available.at("/0/restaurant/address").asText());
        assertFalse(response.body().contains("Saved customer"));
        assertFalse(response.body().contains("12345678"));
        assertFalse(response.body().contains("Saved delivery address"));
        assertFalse(response.body().contains("password"));
        assertFalse(response.body().contains("commission"));
    }

    @Test
    void assignedCourierCanReloadTheirDeliveryAndCompleteOnlyTheNextValidStep() throws Exception {
        String first = login("first-courier@example.test");
        String second = login("second-courier@example.test");
        assertEquals(204, request("GET", ACTIVE, first).statusCode());
        UUID orderId = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
        JsonNode claimed = json(201, request("POST", action(orderId, "accept"), first));

        HttpResponse<String> response = request("GET", ACTIVE, first);
        JsonNode active = json(200, response);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(claimed.get("id"), active.get("id"));
        assertEquals(orderId.toString(), active.get("orderId").asText());
        assertEquals("CLAIMED", active.get("status").asText());
        assertEquals("First restaurant", active.at("/restaurant/name").asText());
        assertEquals("First address", active.at("/restaurant/address").asText());
        assertEquals("Saved customer", active.get("customerName").asText());
        assertEquals("12345678", active.get("customerPhone").asText());
        assertEquals("Saved delivery address", active.get("deliveryAddress").asText());
        assertSameTimeAtDatabasePrecision(claimed.get("claimedTime"), active.get("claimedTime"));
        assertEquals(active, json(200, request("GET", ACTIVE, first)));
        assertEquals(204, request("GET", ACTIVE, second).statusCode());
        assertFalse(response.body().contains("password"));
        assertFalse(response.body().contains("commission"));

        json(409, request("POST", action(orderId, "accept"), second));
        json(403, request("POST", action(orderId, "pickup"), second));
        json(409, request("POST", action(orderId, "delivered"), first));
        JsonNode pickedUp = json(200, request("POST", action(orderId, "pickup"), first));
        JsonNode reloaded = json(200, request("GET", ACTIVE, first));
        assertEquals("UNDER_DELIVERY", reloaded.get("status").asText());
        assertSameTimeAtDatabasePrecision(pickedUp.get("pickupTime"), reloaded.get("pickupTime"));
        json(200, request("POST", action(orderId, "delivered"), first));
        assertEquals(204, request("GET", ACTIVE, first).statusCode());
        assertEquals(0, json(200, request("GET", AVAILABLE, second)).size());
    }

    @Test
    void completingADeliveryEarnsTwentyPercentOfItsSavedCommission() throws Exception {
        String courier = login("first-courier@example.test");
        Map<String, String> examples = Map.of("0.00", "0.00", "0.02", "0.00", "0.03", "0.01",
                "6.00", "1.20", "13.75", "2.75", "23.33", "4.67", "30.08", "6.02", "30.00", "6.00");
        for (var example : examples.entrySet()) {
            UUID orderId = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
            emf.runInTransaction(em -> em.find(Order.class, orderId).setCommission(new BigDecimal(example.getKey())));
            JsonNode claimed = json(201, request("POST", action(orderId, "accept"), courier));
            json(200, request("POST", action(orderId, "pickup"), courier));

            JsonNode delivered = json(200, request("POST", action(orderId, "delivered"), courier));

            assertEquals("DELIVERED", delivered.path("status").asText());
            assertEquals(claimed.get("courierId"), delivered.get("courierId"));
            assertEquals(new BigDecimal(example.getValue()), delivered.path("earnedBonus").decimalValue().setScale(2),
                    "Bonus for saved commission " + example.getKey());
        }
    }

    @Test
    void bonusRequiresCompletionByTheAssignedCourierAndCannotBeEarnedAgain() throws Exception {
        String courier = login("first-courier@example.test");
        String otherCourier = login("second-courier@example.test");
        UUID orderId = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
        JsonNode claimed = json(201, request("POST", action(orderId, "accept"), courier));
        assertTrue(claimed.path("earnedBonus").isNull());
        json(409, request("POST", action(orderId, "delivered"), courier));
        JsonNode pickedUp = json(200, request("POST", action(orderId, "pickup"), courier));
        assertTrue(pickedUp.path("earnedBonus").isNull());
        json(403, request("POST", action(orderId, "delivered"), otherCourier));

        JsonNode delivered = json(200, request("POST", action(orderId, "delivered"), courier));
        assertEquals(claimed.get("courierId"), delivered.get("courierId"));
        assertEquals(new BigDecimal("0.96"), delivered.path("earnedBonus").decimalValue().setScale(2));
        json(409, request("POST", action(orderId, "delivered"), courier));
        json(403, request("POST", action(orderId, "delivered"), otherCourier));
        assertEquals(204, request("GET", ACTIVE, courier).statusCode());
    }

    @Test
    void rejectedOrderCannotEarnBonusEvenIfItsDeliveryStillLooksPickedUp() throws Exception {
        String courier = login("first-courier@example.test");
        UUID orderId = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
        json(201, request("POST", action(orderId, "accept"), courier));
        json(200, request("POST", action(orderId, "pickup"), courier));
        emf.runInTransaction(em -> em.find(Order.class, orderId).setStatus(Status.REJECTED));

        json(409, request("POST", action(orderId, "delivered"), courier));
    }

    @Test
    void rejectedOrderCannotBePickedUpToMakeItEligibleForBonus() throws Exception {
        String courier = login("first-courier@example.test");
        UUID orderId = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
        json(201, request("POST", action(orderId, "accept"), courier));
        emf.runInTransaction(em -> em.find(Order.class, orderId).setStatus(Status.REJECTED));

        json(409, request("POST", action(orderId, "pickup"), courier));
        json(409, request("POST", action(orderId, "delivered"), courier));
    }

    @Test
    void eachCourierReadsOnlyTheirOwnActiveDelivery() throws Exception {
        String first = login("first-courier@example.test");
        String second = login("second-courier@example.test");
        UUID firstOrder = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
        UUID secondOrder = order(Status.READY_FOR_DELIVERY, LocalDateTime.now());
        json(201, request("POST", action(firstOrder, "accept"), first));
        json(201, request("POST", action(secondOrder, "accept"), second));

        assertEquals(firstOrder.toString(), json(200, request("GET", ACTIVE, first)).get("orderId").asText());
        assertEquals(secondOrder.toString(), json(200, request("GET", ACTIVE, second)).get("orderId").asText());
        json(409, request("POST", action(secondOrder, "accept"), first));
        assertEquals(0, json(200, request("GET", AVAILABLE, first)).size());
    }

    @Test
    void courierReadsRequireAuthenticationAndCourierRole() throws Exception {
        for (String path : List.of(AVAILABLE, ACTIVE)) {
            json(401, request("GET", path, null));
            json(401, request("GET", path, "invalid-token"));
            for (String role : List.of("customer", "admin", "restaurant_owner")) {
                json(403, request("GET", path, login(role + "@example.test")));
            }
        }
        assertEquals(0, json(200, request("GET", AVAILABLE, login("first-courier@example.test"))).size());
    }

    private UUID order(Status status, LocalDateTime orderTime) {
        return emf.callInTransaction(em -> {
            Order order = new Order();
            order.setCustomer(em.find(User.class, fixtures.accounts().get(Role.CUSTOMER)));
            order.setRestaurant(em.find(Restaurant.class, fixtures.restaurant()));
            order.setCustomerName("Saved customer");
            order.setCustomerPhone("12345678");
            order.setDeliveryAddress("Saved delivery address");
            order.setOrderTime(orderTime);
            order.setStatus(status);
            order.setTotalPrice(new BigDecimal("50.00"));
            order.setNetAmount(new BigDecimal("40.000"));
            order.setCommission(new BigDecimal("4.80"));
            em.persist(order);
            return order.getId();
        });
    }

    private void assertSameTimeAtDatabasePrecision(JsonNode immediate, JsonNode saved) {
        // Java on Linux can return nanoseconds; PostgreSQL timestamps retain microseconds.
        Duration difference = Duration.between(LocalDateTime.parse(immediate.asText()), LocalDateTime.parse(saved.asText())).abs();
        assertTrue(difference.compareTo(Duration.ofNanos(1_000)) < 0,
                () -> "Timestamp changed beyond database precision: " + immediate + " -> " + saved);
    }

    private String action(UUID orderId, String action) {
        return "/api/courier/orders/" + orderId + "/delivery/" + action;
    }

    private String login(String email) throws Exception {
        String credentials = mapper.writeValueAsString(Map.of("email", email, "password", SecurityTestData.PASSWORD));
        HttpRequest request = HttpRequest.newBuilder(base("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(credentials)).build();
        return json(200, client.send(request, HttpResponse.BodyHandlers.ofString())).get("token").asText();
    }

    private JsonNode json(int status, HttpResponse<String> response) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private URI base(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private HttpResponse<String> request(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base(path)).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
