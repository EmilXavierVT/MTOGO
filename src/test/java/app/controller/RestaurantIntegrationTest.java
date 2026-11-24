package app.controller;

import app.App;
import app.config.H2TestDatabase;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
import app.support.SecurityTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestaurantIntegrationTest {
    private static final String RESTAURANTS = "/api/restaurants";
    private static final String CUSTOMER_EMAIL = "customer@example.test";
    private static final String COURIER_EMAIL = "courier@example.test";
    private static final String SECRET = "integration-test-only-secret-at-least-32-bytes";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private EntityManagerFactory emf;
    private Javalin server;
    private SecurityTestData.Fixtures fixtures;

    @BeforeEach
    void start() {
        emf = H2TestDatabase.create();
        fixtures = SecurityTestData.populate(emf);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();

            Menu lunch = addMenu(em, fixtures.restaurant(), "Frokost");
            addProduct(em, lunch, "Rugbrød", "89.95", true);
            addProduct(em, lunch, "Smørrebrød", "60.00", false);

            Menu dinner = addMenu(em, fixtures.otherRestaurant(), "Aften");
            addProduct(em, dinner, "Pizza", "99.00", true);

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
    void customerShownAllRestaurants() throws Exception {
        JsonNode restaurants = json(200, request("GET", RESTAURANTS, login(CUSTOMER_EMAIL), null));

        assertEquals(2, restaurants.size());
        List<String> addresses = new ArrayList<>();
        restaurants.forEach(r -> addresses.add(r.get("address").asText()));
        assertTrue(addresses.contains("First address"));
        assertTrue(addresses.contains("Second address"));
    }

    @Test
    void restaurantListDoesNotExposeOwnerOrMenus() throws Exception {
        JsonNode restaurant = json(200, request("GET", RESTAURANTS, login(CUSTOMER_EMAIL), null)).get(0);

        assertFalse(restaurant.has("owner"));
        assertFalse(restaurant.has("menus"));
    }

    @Test
    void returnsOnlyMenusForTheSelectedRestaurant() throws Exception {
        JsonNode menus = json(200, request("GET", menusPath(fixtures.restaurant()), login(CUSTOMER_EMAIL), null));

        assertEquals(1, menus.size());
        assertEquals("Frokost", menus.get(0).get("name").asText());
        assertEquals(2, menus.get(0).get("products").size());
    }

    @Test
    void menuShowsWhenAProductIsSoldOut() throws Exception {
        JsonNode menus = json(200, request("GET", menusPath(fixtures.restaurant()), login(CUSTOMER_EMAIL), null));
        JsonNode products = menus.get(0).get("products");

        int soldOut = 0;
        for (JsonNode product : products) {
            if (!product.get("available").asBoolean()) {
                soldOut++;
            }
        }

        assertEquals(1, soldOut);
    }

    @Test
    void invalidRestaurantIdGives400() throws Exception {
        json(400, request("GET", RESTAURANTS + "/not-a-uuid/menus", login(CUSTOMER_EMAIL), null));
    }

    @Test
    void requestsWithoutValidTokenGive401() throws Exception {
        json(401, request("GET", RESTAURANTS, null, null));
        json(401, request("GET", menusPath(fixtures.restaurant()), "invalid-token", null));
    }

    @Test
    void nonCustomersGet403() throws Exception {
        String courier = login(COURIER_EMAIL);
        json(403, request("GET", RESTAURANTS, courier, null));
        json(403, request("GET", menusPath(fixtures.restaurant()), courier, null));
    }

    private static Menu addMenu(EntityManager em, UUID restaurantId, String name) {
        Menu menu = new Menu();
        menu.setRestaurant(em.getReference(Restaurant.class, restaurantId));
        menu.setName(name);
        em.persist(menu);
        return menu;
    }

    private static void addProduct(EntityManager em, Menu menu, String name, String price, boolean available) {
        Product product = new Product();
        product.setMenu(menu);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setAvailable(available);
        em.persist(product);
    }

    private static String menusPath(UUID restaurantId) {
        return RESTAURANTS + "/" + restaurantId + "/menus";
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}