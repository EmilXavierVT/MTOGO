package app.security;

import app.App;
import app.controller.RoleTestController;
import app.config.H2TestDatabase;
import app.entities.enums.Role;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.support.OwnershipTestRoutes;
import app.support.SecurityTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class H2SecurityIntegrationTest {
    private static final String SECRET = "integration-test-only-secret-at-least-32-bytes";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private EntityManagerFactory emf;
    private Javalin server;
    private SecurityTestData.Fixtures fixtures;
    private String baseUrl;

    @BeforeEach void start() {
        emf = H2TestDatabase.create();
        fixtures = SecurityTestData.populate(emf);
        server = App.createApp(emf, SECRET);
        RoleTestController.addRoutes(server);
        OwnershipTestRoutes.register(server, emf);
        server.start(0);
        baseUrl = "http://localhost:" + server.port();
    }

    @AfterEach void stop() {
        try { if (server != null) server.stop(); }
        finally { if (emf != null) emf.close(); }
    }

    @Test void registrationHashesPasswordAndCannotGrantAdmin() throws Exception {
        HttpResponse<String> response = request("POST", "/api/auth/register", null, Map.of(
                "name", "New customer", "email", "New.Customer@Example.Test", "password", SecurityTestData.PASSWORD,
                "role", "ADMIN", "passwordHash", "attacker-supplied"));
        assertEquals(201, response.statusCode(), response.body());
        JsonNode body = mapper.readTree(response.body());
        assertEquals("CUSTOMER", body.get("role").asText());
        assertFalse(body.has("password"));
        assertFalse(body.has("passwordHash"));
        UUID id = UUID.fromString(body.get("id").asText());
        try (EntityManager em = emf.createEntityManager()) {
            User saved = em.find(User.class, id);
            assertEquals(Role.CUSTOMER, saved.getRole());
            assertEquals("new.customer@example.test", saved.getEmail());
            assertNotEquals(SecurityTestData.PASSWORD, saved.getPasswordHash());
            assertTrue(saved.verifyPassword(SecurityTestData.PASSWORD));
        }
        String token = login("NEW.CUSTOMER@EXAMPLE.TEST");
        HttpResponse<String> me = request("GET", "/api/auth/me", token, null);
        assertEquals(200, me.statusCode());
        assertEquals(id.toString(), mapper.readTree(me.body()).get("id").asText());
    }

    @Test void rejectsInvalidRegistrationAndDuplicateEmail() throws Exception {
        assertEquals(400, request("POST", "/api/auth/register", null,
                Map.of("name", "Test", "email", "bad-email", "password", "short")).statusCode());
        HttpResponse<String> duplicate = request("POST", "/api/auth/register", null,
                Map.of("name", "Duplicate", "email", "CUSTOMER@example.test", "password", SecurityTestData.PASSWORD));
        assertEquals(409, duplicate.statusCode(), duplicate.body());
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals(1L, em.createQuery("select count(u) from User u where u.email = :email", Long.class)
                    .setParameter("email", "customer@example.test").getSingleResult());
        }
    }

    @Test void loginRejectsWrongCredentialsAndProtectedRoutesRequireValidToken() throws Exception {
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "customer@example.test", "password", "wrong-password")).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "missing@example.test", "password", SecurityTestData.PASSWORD)).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, null).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", "invalid", null).statusCode());
        String token = login("customer@example.test");
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertEquals(401, request("GET", "/api/auth/me", forged, null).statusCode());
        assertEquals(200, request("GET", "/api/auth/me", token, null).statusCode());
    }

    @Test void loginForEveryRoleEnforcesRoleMatrix() throws Exception {
        Map<Role, String> paths = Map.of(Role.CUSTOMER, "/api/test/customer", Role.COURIER, "/api/test/courier",
                Role.RESTAURANT_OWNER, "/api/test/restaurant-owner", Role.ADMIN, "/api/test/admin");
        Map<Role, String> tokens = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            String token = login(role.name() + "@example.test");
            tokens.put(role, token);
            HttpResponse<String> me = request("GET", "/api/auth/me", token, null);
            JsonNode account = mapper.readTree(me.body());
            assertEquals(fixtures.accounts().get(role).toString(), account.get("id").asText());
            assertEquals(role.name(), account.get("role").asText());
        }
        for (Role accountRole : Role.values()) {
            for (Role endpointRole : Role.values()) {
                int expected = accountRole == endpointRole ? 200 : 403;
                assertEquals(expected, request("GET", paths.get(endpointRole), tokens.get(accountRole), null).statusCode(),
                        accountRole + " accessing " + endpointRole);
            }
        }
    }

    @Test void customerOwnershipAllowsOwnDataAndRejectsOtherCustomerCourierAndAdmin() throws Exception {
        String customerToken = login("customer@example.test");
        UUID ownId = fixtures.accounts().get(Role.CUSTOMER);
        String ownPath = "/integration/customers/" + ownId;
        String otherPath = "/integration/customers/" + fixtures.otherCustomer();
        assertEquals(200, request("GET", ownPath, customerToken, null).statusCode());
        assertEquals(200, request("PATCH", ownPath, customerToken, Map.of("value", "Changed name")).statusCode());
        assertEquals("Changed name", customerName(ownId));
        assertEquals(403, request("GET", otherPath, customerToken, null).statusCode());
        assertEquals(403, request("PATCH", otherPath, customerToken, Map.of("value", "Forbidden")).statusCode());
        assertEquals("Other customer", customerName(fixtures.otherCustomer()));
        for (String email : new String[]{"courier@example.test", "admin@example.test"}) {
            String token = login(email);
            assertEquals(403, request("GET", ownPath, token, null).statusCode());
            assertEquals(403, request("PATCH", ownPath, token, Map.of("value", "Forbidden")).statusCode());
        }
        assertEquals("Changed name", customerName(ownId));
    }

    @Test void restaurantOwnershipRejectsForeignOwnerAndAdminWrites() throws Exception {
        String token = login("restaurant_owner@example.test");
        String ownPath = "/integration/restaurants/" + fixtures.restaurant();
        String otherPath = "/integration/restaurants/" + fixtures.otherRestaurant();
        assertEquals(200, request("GET", ownPath, token, null).statusCode());
        assertEquals(200, request("PATCH", ownPath, token, Map.of("value", "Changed address")).statusCode());
        assertEquals("Changed address", restaurantAddress(fixtures.restaurant()));
        assertEquals(403, request("GET", otherPath, token, null).statusCode());
        assertEquals(403, request("PATCH", otherPath, token, Map.of("value", "Forbidden")).statusCode());
        assertEquals("Second address", restaurantAddress(fixtures.otherRestaurant()));
        String adminToken = login("admin@example.test");
        assertEquals(403, request("PATCH", ownPath, adminToken, Map.of("value", "Admin change")).statusCode());
        assertEquals("Changed address", restaurantAddress(fixtures.restaurant()));
    }

    @Test void existingTokensFollowDatabaseRoleChangesAndDeletedAccountsAreRejected() throws Exception {
        String token = login("customer@example.test");
        UUID id = fixtures.accounts().get(Role.CUSTOMER);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.find(User.class, id).setRole(Role.COURIER);
            em.getTransaction().commit();
        }
        assertEquals(403, request("GET", "/api/test/customer", token, null).statusCode());
        assertEquals(200, request("GET", "/api/test/courier", token, null).statusCode());
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.remove(em.find(User.class, id));
            em.getTransaction().commit();
        }
        assertEquals(401, request("GET", "/api/auth/me", token, null).statusCode());
    }
    private String login(String email) throws Exception {
        HttpResponse<String> response = request("POST", "/api/auth/login", null,
                Map.of("email", email, "password", SecurityTestData.PASSWORD));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = mapper.readTree(response.body());
        assertFalse(body.get("user").has("passwordHash"));
        return body.get("token").asText();
    }

    private String customerName(UUID id) {
        try (EntityManager em = emf.createEntityManager()) { return em.find(User.class, id).getName(); }
    }

    private String restaurantAddress(UUID id) {
        try (EntityManager em = emf.createEntityManager()) { return em.find(Restaurant.class, id).getAddress(); }
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
        }
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}


