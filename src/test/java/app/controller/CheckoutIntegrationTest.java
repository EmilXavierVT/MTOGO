package app.controller;

import app.App;
import app.config.H2TestDatabase;
import app.entities.enums.Role;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CheckoutIntegrationTest {
    private static final String ORDERS = "/api/customer/orders";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private EntityManagerFactory emf;
    private Javalin server;
    private SecurityTestData.Fixtures fixtures;
    private UUID productId;

    @BeforeEach
    void start() {
        emf = H2TestDatabase.create();
        fixtures = SecurityTestData.populate(emf);
        emf.runInTransaction(em -> {
            Restaurant restaurant = em.find(Restaurant.class, fixtures.restaurant());
            restaurant.setName("Checkout restaurant");
            Menu menu = new Menu();
            menu.setName("Lunch");
            menu.setRestaurant(restaurant);
            em.persist(menu);
            Product product = new Product();
            product.setMenu(menu);
            product.setName("Burger");
            product.setPrice(new BigDecimal("50.00"));
            product.setAvailable(true);
            em.persist(product);
            productId = product.getId();
        });
        server = App.createApp(emf, "checkout-test-only-secret-at-least-32-bytes").start("127.0.0.1", 0);
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
    void confirmationReloadUsesSavedOrderAndNeverCreatesAnotherOrder() throws Exception {
        String token = login("customer@example.test");
        JsonNode created = json(201, request("POST", ORDERS, token, checkout("100.00", "APPROVED")));
        assertEquals("PENDING", created.get("status").asText());
        assertEquals("Checkout restaurant", created.at("/restaurant/name").asText());
        assertEquals("Customer at checkout", created.get("customerName").asText());
        assertEquals("12345678", created.get("customerPhone").asText());
        assertEquals("Delivery address", created.get("deliveryAddress").asText());
        assertEquals(2, created.at("/lines/0/quantity").asInt());
        assertFalse(created.has("customer"));
        assertFalse(created.has("commission"));

        emf.runInTransaction(em -> {
            Product product = em.find(Product.class, productId);
            product.setName("Changed menu name");
            product.setPrice(new BigDecimal("200.00"));
            product.setAvailable(false);
            em.find(User.class, fixtures.accounts().get(Role.CUSTOMER)).setName("Changed profile name");
        });

        for (int reload = 0; reload < 2; reload++) {
            HttpResponse<String> response = request("GET", ORDERS + "/" + created.get("id").asText(), token, null);
            JsonNode saved = json(200, response);
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(created.get("id"), saved.get("id"));
            assertEquals(created.get("customerName"), saved.get("customerName"));
            assertEquals("Burger", saved.at("/lines/0/productName").asText());
            assertEquals(0, new BigDecimal("50.00").compareTo(saved.at("/lines/0/unitPrice").decimalValue()));
            assertEquals(0, new BigDecimal("100.00").compareTo(saved.get("total").decimalValue()));
        }
        assertOrderCount(1);
    }

    @Test
    void changedPricesReturnAQuoteAndRequireANewAcceptedTotal() throws Exception {
        String token = login("customer@example.test");
        emf.runInTransaction(em -> em.find(Product.class, productId).setPrice(new BigDecimal("60.00")));
        JsonNode conflict = json(409, request("POST", ORDERS, token, checkout("100.00", "APPROVED")));
        assertEquals("PRICE_CHANGED", conflict.get("code").asText());
        assertEquals(0, new BigDecimal("120.00").compareTo(conflict.get("updatedTotal").decimalValue()));
        assertEquals(productId.toString(), conflict.at("/products/0/productId").asText());
        assertEquals("Burger", conflict.at("/products/0/productName").asText());
        assertEquals(0, new BigDecimal("60.00").compareTo(conflict.at("/products/0/unitPrice").decimalValue()));
        assertOrderCount(0);
        json(201, request("POST", ORDERS, token, checkout("120.00", "APPROVED")));
        assertOrderCount(1);
    }

    @Test
    void unavailableAndRemovedDishesAreIdentifiedWithoutCreatingOrders() throws Exception {
        String token = login("customer@example.test");
        emf.runInTransaction(em -> em.find(Product.class, productId).setAvailable(false));
        JsonNode unavailable = json(409, request("POST", ORDERS, token, checkout("100.00", "APPROVED")));
        assertEquals("PRODUCT_UNAVAILABLE", unavailable.get("code").asText());
        assertEquals(productId.toString(), unavailable.get("productId").asText());
        emf.runInTransaction(em -> em.remove(em.find(Product.class, productId)));
        JsonNode removed = json(404, request("POST", ORDERS, token, checkout("100.00", "APPROVED")));
        assertEquals("PRODUCT_UNAVAILABLE", removed.get("code").asText());
        assertEquals(productId.toString(), removed.get("productId").asText());
        assertOrderCount(0);
    }

    @Test
    void declinedSimulationDoesNotCreateAnOrder() throws Exception {
        json(402, request("POST", ORDERS, login("customer@example.test"), checkout("100.00", "DECLINED")));
        assertOrderCount(0);
    }

    @Test
    void otherCustomersCannotReadAConfirmation() throws Exception {
        JsonNode created = json(201, request("POST", ORDERS, login("customer@example.test"), checkout("100.00", "APPROVED")));
        json(404, request("GET", ORDERS + "/" + created.get("id").asText(), login("other-customer@example.test"), null));
        json(404, request("GET", ORDERS + "/" + UUID.randomUUID(), login("customer@example.test"), null));
    }

    @Test
    void nonCustomersCannotCheckoutOrReadConfirmations() throws Exception {
        for (String role : List.of("courier", "admin", "restaurant_owner")) {
            String token = login(role + "@example.test");
            json(403, request("POST", ORDERS, token, checkout("100.00", "APPROVED")));
            json(403, request("GET", ORDERS + "/" + UUID.randomUUID(), token, null));
        }
        assertOrderCount(0);
    }

    @Test
    void missingOrInvalidAuthenticationIsRejected() throws Exception {
        json(401, request("POST", ORDERS, null, checkout("100.00", "APPROVED")));
        json(401, request("GET", ORDERS + "/" + UUID.randomUUID(), "invalid-token", null));
    }

    @Test
    void malformedConfirmationIdsAreRejected() throws Exception {
        String token = login("customer@example.test");
        json(400, request("GET", ORDERS + "/not-a-uuid", token, null));
        json(400, request("GET", ORDERS + "/1-1-1-1-1", token, null));
    }

    @Test
    void restaurantOwnerCanAcceptAndReadyOwnOrderOnly() throws Exception {
        JsonNode created = json(201, request("POST", ORDERS, login("customer@example.test"), checkout("100.00", "APPROVED")));
        String path = "/api/owner/orders/" + created.get("id").asText();
        String owner = login("restaurant_owner@example.test");
        json(401, request("POST", path + "/accept", null, null));
        json(403, request("POST", path + "/accept", login("customer@example.test"), null));
        json(403, request("POST", path + "/accept", login("other-owner@example.test"), null));
        json(400, request("POST", "/api/owner/orders/not-a-uuid/accept", owner, null));
        assertEquals("ACCEPTED", json(200, request("POST", path + "/accept", owner, null)).get("status").asText());
        json(409, request("POST", path + "/reject", owner, null));
        assertEquals("READY_FOR_DELIVERY", json(200, request("POST", path + "/ready", owner, null)).get("status").asText());
        json(409, request("POST", path + "/ready", owner, null));
    }

    @Test
    void restaurantRejectionIsPersistedAndCannotBeRepeated() throws Exception {
        JsonNode created = json(201, request("POST", ORDERS, login("customer@example.test"), checkout("100.00", "APPROVED")));
        String path = "/api/owner/orders/" + created.get("id").asText();
        String owner = login("restaurant_owner@example.test");
        JsonNode rejected = json(200, request("POST", path + "/reject", owner, null));
        assertEquals("REJECTED", rejected.get("status").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(rejected.get("refundedAmount").decimalValue()));
        assertFalse(rejected.get("refundedAt").isNull());
        json(409, request("POST", path + "/reject", owner, null));
        try (var em = emf.createEntityManager()) {
            var order = em.find(app.entities.orderFlow.Order.class, UUID.fromString(created.get("id").asText()));
            assertEquals(new BigDecimal("100.00"), order.getRefundedAmount());
            assertEquals(new BigDecimal("4.80"), order.getCommission());
        }
    }

    private String checkout(String total, String paymentResult) throws Exception {
        return mapper.writeValueAsString(Map.of("restaurantId", fixtures.restaurant(),
                "products", List.of(Map.of("productId", productId, "quantity", 2)),
                "name", "Customer at checkout", "phone", "12345678", "address", "Delivery address",
                "acceptedTotal", new BigDecimal(total), "simulatedPaymentResult", paymentResult));
    }

    private void assertOrderCount(long expected) {
        try (var em = emf.createEntityManager()) {
            assertEquals(expected, em.createQuery("select count(o) from Order o", Long.class).getSingleResult());
            assertEquals(expected, em.createQuery("select count(l) from OrderLine l", Long.class).getSingleResult());
        }
    }

    private String login(String email) throws Exception {
        String credentials = mapper.writeValueAsString(Map.of("email", email, "password", SecurityTestData.PASSWORD));
        return json(200, request("POST", "/api/auth/login", null, credentials)).get("token").asText();
    }

    private JsonNode json(int status, HttpResponse<String> response) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(5))
                .header("Origin", "http://localhost:3000");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
