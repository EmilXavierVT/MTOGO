package app.controller;

import app.App;
import app.config.H2TestDatabase;
import app.entities.enums.Role;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomerOrderIntegrationTest {
    private static final String SECRET = "integration-test-only-secret-at-least-32-bytes";
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
            Menu menu = new Menu();
            menu.setName("Dinner");
            menu.setRestaurant(em.find(Restaurant.class, fixtures.restaurant()));
            em.persist(menu);
            Product product = new Product();
            product.setName("Dish");
            product.setPrice(new BigDecimal("50.00"));
            product.setMenu(menu);
            em.persist(product);
            productId = product.getId();
        });
        server = App.createApp(emf, SECRET).start("127.0.0.1", 0);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop();
        if (emf != null) emf.close();
    }

    @Test
    void customerCanReloadOwnOrderWithRestaurant() throws Exception {
        String token = login("customer@example.test");
        JsonNode created = json(201, request("POST", "/api/customer/orders", token, checkout("100.00")));
        assertEquals("PENDING", created.get("status").asText());
        assertEquals("First restaurant", created.get("restaurant").get("name").asText());
        assertEquals("First address", created.get("restaurant").get("address").asText());
        assertEquals(fixtures.restaurant().toString(), created.get("restaurant").get("id").asText());

        JsonNode loaded = json(200, request("GET", "/api/customer/orders/" + created.get("id").asText(), token, null));
        assertEquals(created, loaded);
    }

    @Test
    void otherCustomerCannotReadOrder() throws Exception {
        String owner = login("customer@example.test");
        String other = login("other-customer@example.test");
        String id = json(201, request("POST", "/api/customer/orders", owner, checkout("100.00")))
                .get("id").asText();
        JsonNode error = json(403, request("GET", "/api/customer/orders/" + id, other, null));
        assertEquals("Access denied", error.get("error").asText());
    }

    @Test
    void missingOrderAndBadIdAreHandled() throws Exception {
        String token = login("customer@example.test");
        assertEquals("Order not found", json(404, request("GET", "/api/customer/orders/" + UUID.randomUUID(), token, null))
                .get("error").asText());
        assertEquals("orderId must be a UUID", json(400, request("GET", "/api/customer/orders/bad-id", token, null))
                .get("error").asText());
    }

    @Test
    void unauthenticatedAndWrongRoleCannotReadOrder() throws Exception {
        String token = login("customer@example.test");
        String id = json(201, request("POST", "/api/customer/orders", token, checkout("100.00")))
                .get("id").asText();
        String path = "/api/customer/orders/" + id;
        json(401, request("GET", path, null, null));
        json(401, request("GET", path, "bad-token", null));
        json(403, request("GET", path, login("admin@example.test"), null));
    }

    @Test
    void changedPriceReturnsStructuredTotalAndKeepsError() throws Exception {
        JsonNode error = json(409, request("POST", "/api/customer/orders",
                login("customer@example.test"), checkout("99.00")));
        assertEquals("Total changed. Accept the updated total: 100.00", error.get("error").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(error.get("updatedTotal").decimalValue()));
        try (var em = emf.createEntityManager()) {
            assertEquals(0L, em.createQuery("select count(o) from Order o", Long.class).getSingleResult());
        }
    }

    private String checkout(String acceptedTotal) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "restaurantId", fixtures.restaurant().toString(),
                "products", new Object[]{Map.of("productId", productId.toString(), "quantity", 2)},
                "name", "Customer", "phone", "12345678", "address", "Delivery address",
                "acceptedTotal", new BigDecimal(acceptedTotal), "simulatedPaymentResult", "APPROVED"));
    }

    private String login(String email) throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", email, "password", SecurityTestData.PASSWORD));
        return json(200, request("POST", "/api/auth/login", null, body)).get("token").asText();
    }

    private JsonNode json(int status, HttpResponse<String> response) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
