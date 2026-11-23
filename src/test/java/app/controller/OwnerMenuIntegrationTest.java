package app.controller;

import app.App;
import app.config.H2TestDatabase;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.support.SecurityTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import jakarta.persistence.EntityManager;
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

class OwnerMenuIntegrationTest {
    private static final String MENUS = "/api/owner/menus";
    private static final String OWNER_EMAIL = "restaurant_owner@example.test";
    private static final String OTHER_OWNER_EMAIL = "other-owner@example.test";
    private static final String SECRET = "integration-test-only-secret-at-least-32-bytes";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private EntityManagerFactory emf;
    private Javalin server;
    private SecurityTestData.Fixtures fixtures;
    private UUID ownMenu;
    private UUID otherMenu;

    @BeforeEach
    void start() {
        emf = H2TestDatabase.create();
        fixtures = SecurityTestData.populate(emf);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            ownMenu = addMenu(em, fixtures.restaurant(), "Frokost");
            otherMenu = addMenu(em, fixtures.otherRestaurant(), "Aften");
            em.getTransaction().commit();
        }
        server = App.createApp(emf, SECRET).start("127.0.0.1", 0);
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
    void listsOnlyTheSignedInOwnersMenus() throws Exception {
        JsonNode firstOwnersMenus = json(200, request("GET", MENUS, login(OWNER_EMAIL), null));
        assertEquals(1, firstOwnersMenus.size());
        JsonNode menu = firstOwnersMenus.get(0);
        assertEquals(ownMenu.toString(), menu.get("id").asText());
        assertEquals("Frokost", menu.get("name").asText());
        assertTrue(menu.get("products").isArray());
        assertEquals(0, menu.get("products").size());

        JsonNode otherOwnersMenus = json(200, request("GET", MENUS, login(OTHER_OWNER_EMAIL), null));
        assertEquals(1, otherOwnersMenus.size());
        assertEquals(otherMenu.toString(), otherOwnersMenus.get(0).get("id").asText());
    }

    @Test
    void anOwnerWithoutAProvisionedMenuGetsAnEmptyList() throws Exception {
        emf.runInTransaction(em -> em.remove(em.find(Menu.class, ownMenu)));
        JsonNode menus = json(200, request("GET", MENUS, login(OWNER_EMAIL), null));
        assertTrue(menus.isArray());
        assertTrue(menus.isEmpty());
    }

    @Test
    void createsADishAndListsItsSavedDetails() throws Exception {
        String owner = login(OWNER_EMAIL);
        String input = """
                {
                  "nameDa": "  Rugbrød  ",
                  "descriptionDa": "  Bagt her  ",
                  "nameEn": "  ",
                  "descriptionEn": "  Baked here  ",
                  "priceDkkInclVat": 89.950,
                  "available": true
                }
                """;
        JsonNode saved = json(201, request("POST", productsPath(ownMenu), owner, input));
        UUID.fromString(saved.get("id").asText());
        assertEquals("Rugbrød", saved.get("nameDa").asText());
        assertEquals("Bagt her", saved.get("descriptionDa").asText());
        assertTrue(saved.get("nameEn").isNull());
        assertEquals("Baked here", saved.get("descriptionEn").asText());
        assertEquals(new BigDecimal("89.95"), saved.get("priceDkkInclVat").decimalValue());
        assertTrue(saved.get("available").booleanValue());

        JsonNode menus = json(200, request("GET", MENUS, owner, null));
        assertEquals(saved, menus.get(0).get("products").get(0));
    }

    @Test
    void editsADishAndTogglesAvailabilityWithoutChangingTheSavedOrderTotal() throws Exception {
        String owner = login(OWNER_EMAIL);
        String products = productsPath(ownMenu);
        ObjectNode original = dish("Rugbrød").put("nameEn", "Rye bread").put("descriptionEn", "Freshly baked");
        JsonNode created = json(201, request("POST", products, owner, original.toString()));
        String id = created.get("id").asText();
        String product = products + "/" + id;
        UUID orderId = saveOrder();

        ObjectNode changes = dish("Surdejsbrød").put("descriptionDa", "Bagværk med surdej")
                .put("nameEn", "  ").put("priceDkkInclVat", new BigDecimal("93.75")).put("available", false);
        JsonNode updated = json(200, request("PUT", product, owner, changes.toString()));
        assertEquals(id, updated.get("id").asText());
        assertEquals("Surdejsbrød", updated.get("nameDa").asText());
        assertEquals("Bagværk med surdej", updated.get("descriptionDa").asText());
        assertEquals(new BigDecimal("93.75"), updated.get("priceDkkInclVat").decimalValue());
        assertTrue(updated.get("nameEn").isNull());
        assertTrue(updated.get("descriptionEn").isNull());
        assertFalse(updated.get("available").booleanValue());
        assertEquals(updated, json(200, request("GET", MENUS, owner, null)).get(0).get("products").get(0));

        changes.put("available", true);
        assertTrue(json(200, request("PUT", product, owner, changes.toString())).get("available").booleanValue());
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals(new BigDecimal("89.95"), em.find(Order.class, orderId).getTotalPrice());
        }
    }

    @Test
    void onlyAuthenticatedRestaurantOwnersCanUseTheMenuRoutes() throws Exception {
        assertMenuAccess(401, null);
        assertMenuAccess(401, "invalid-token");
        for (String email : List.of("customer@example.test", "courier@example.test", "admin@example.test")) {
            assertMenuAccess(403, login(email));
        }
    }

    @Test
    void rejectsWritesToAnotherRestaurantsMenuOrAnotherMenusDish() throws Exception {
        String owner = login(OWNER_EMAIL);
        String otherOwner = login(OTHER_OWNER_EMAIL);
        String foreignProducts = productsPath(otherMenu);
        JsonNode foreignDish = json(201, request("POST", foreignProducts, otherOwner, dish("Salat").toString()));
        String foreignId = foreignDish.get("id").asText();
        String attempt = dish("Ændret").toString();

        json(403, request("POST", foreignProducts, owner, attempt));
        json(403, request("PUT", foreignProducts + "/" + foreignId, owner, attempt));
        json(404, request("PUT", productsPath(ownMenu) + "/" + foreignId, owner, attempt));
        assertEquals(foreignDish, json(200, request("GET", MENUS, otherOwner, null)).get(0).get("products").get(0));

        UUID secondMenu = emf.callInTransaction(em -> addMenu(em, fixtures.restaurant(), "Aftensmad"));
        JsonNode ownDish = json(201, request("POST", productsPath(ownMenu), owner, dish("Rugbrød").toString()));
        json(404, request("PUT", productsPath(secondMenu) + "/" + ownDish.get("id").asText(), owner, attempt));
        json(404, request("POST", productsPath(UUID.randomUUID()), owner, attempt));
    }

    @Test
    void rejectsInvalidProductJsonAndPricesThatCannotBeStoredExactly() throws Exception {
        String owner = login(OWNER_EMAIL);
        for (String amount : List.of("0", "-1", "8.005", "10000000000", "89.950000000000000000000001")) {
            String body = dish("Rugbrød").put("priceDkkInclVat", new BigDecimal(amount)).toString();
            assertBadProduct(owner, body, "priceDkkInclVat");
        }
        assertBadProduct(owner, dish("Rugbrød").put("priceDkkInclVat", "89.95").toString(), "priceDkkInclVat");
        assertBadProduct(owner, dish("  ").toString(), "nameDa");
        assertBadProduct(owner, dish("Rugbrød").put("descriptionDa", "").toString(), "descriptionDa");
        assertBadProduct(owner, dish("Rugbrød").put("available", "true").toString(), "available");
        ObjectNode incomplete = dish("Rugbrød");
        incomplete.remove("available");
        assertBadProduct(owner, incomplete.toString(), "available");
        assertBadProduct(owner, "{", "JSON");
        json(400, request("POST", MENUS + "/1-1-1-1-1/products", owner, dish("Rugbrød").toString()));

        JsonNode menus = json(200, request("GET", MENUS, owner, null));
        assertTrue(menus.get(0).get("products").isEmpty());
    }

    private void assertMenuAccess(int status, String token) throws Exception {
        String products = productsPath(ownMenu);
        String input = dish("Rugbrød").toString();
        json(status, request("GET", MENUS, token, null));
        json(status, request("POST", products, token, input));
        json(status, request("PUT", products + "/" + UUID.randomUUID(), token, input));
    }

    private void assertBadProduct(String owner, String body, String invalidField) throws Exception {
        JsonNode error = json(400, request("POST", productsPath(ownMenu), owner, body));
        assertTrue(error.path("error").asText().contains(invalidField), error::toString);
    }

    private ObjectNode dish(String name) {
        return mapper.createObjectNode().put("nameDa", name).put("descriptionDa", "Bagt her")
                .put("priceDkkInclVat", new BigDecimal("89.95")).put("available", true);
    }

    private UUID saveOrder() {
        return emf.callInTransaction(em -> {
            Order order = new Order();
            order.setCustomer(em.getReference(User.class, fixtures.accounts().get(Role.CUSTOMER)));
            order.setRestaurant(em.getReference(Restaurant.class, fixtures.restaurant()));
            order.setOrderTime(LocalDateTime.of(2026, 11, 18, 12, 0));
            order.setStatus(Status.DELIVERED);
            order.setTotalPrice(new BigDecimal("89.95"));
            order.setCustomerName("Customer");
            order.setCustomerPhone("12345678");
            order.setDeliveryAddress("Test address");
            order.setNetAmount(new BigDecimal("71.96"));
            order.setCommission(BigDecimal.ZERO);
            em.persist(order);
            return order.getId();
        });
    }

    private static String productsPath(UUID menuId) {
        return MENUS + "/" + menuId + "/products";
    }

    private static UUID addMenu(EntityManager em, UUID restaurantId, String name) {
        Menu menu = new Menu();
        menu.setRestaurant(em.getReference(Restaurant.class, restaurantId));
        menu.setName(name);
        em.persist(menu);
        return menu.getId();
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
        URI uri = URI.create("http://127.0.0.1:" + server.port() + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
