package app.security;

import app.App;
import app.controller.RoleTestController;
import io.javalin.Javalin;
import app.entities.enums.Role;
import app.entities.users.User;
import dk.bugelhartmann.TokenSecurity;
import dk.bugelhartmann.UserDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Focused filter tests with mocked persistence; database-backed flows live in H2SecurityIntegrationTest. */
class AccessTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";

    @Test void ownershipRejectsOtherAccountsAndAdmin() {
        UUID owner = UUID.randomUUID();
        SecurityController.CurrentUser customer = new SecurityController.CurrentUser(owner, "Customer", "c@example.test", Role.CUSTOMER);
        Ownership.requireCustomer(customer, owner);
        assertThrows(app.exceptions.ApiException.class, () -> Ownership.requireCustomer(customer, UUID.randomUUID()));
        SecurityController.CurrentUser restaurant = new SecurityController.CurrentUser(owner, "Owner", "r@example.test", Role.RESTAURANT_OWNER);
        Ownership.requireRestaurantOwner(restaurant, owner);
        assertThrows(app.exceptions.ApiException.class, () -> Ownership.requireRestaurantOwner(restaurant, UUID.randomUUID()));
        SecurityController.CurrentUser admin = new SecurityController.CurrentUser(owner, "Admin", "a@example.test", Role.ADMIN);
        assertThrows(app.exceptions.ApiException.class, () -> Ownership.requireCustomer(admin, owner));
        assertThrows(app.exceptions.ApiException.class, () -> Ownership.requireRestaurantOwner(admin, owner));
    }

    @Test void httpAuthenticationRolesAndOwnership() throws Exception {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        EntityManager em = mock(EntityManager.class);
        when(emf.createEntityManager()).thenReturn(em);
        User user = new User("Test", "password123", "test@example.test");
        user.setId(UUID.randomUUID());
        when(em.find(User.class, user.getId())).thenReturn(user);
        Javalin server = App.createApp(emf, SECRET);
        server.get("/test/customer/{id}", ctx -> {
            Ownership.requireCustomer(ctx.attribute("user"), UUID.fromString(ctx.pathParam("id")));
            ctx.result("OK");
        }, Role.CUSTOMER);
        server.get("/test/unclassified", ctx -> ctx.result("Must not be reachable"));
        server.start(0);
        try {
            String token = new TokenSecurity().createToken(new UserDTO(user.getId().toString(), user.getRolesAsStrings()), "mtogo", "3600000", SECRET);
            String url = "http://localhost:" + server.port();
            assertEquals(401, get(url + "/api/auth/me", null));
            assertEquals(401, get(url + "/api/auth/me", "invalid"));
            assertEquals(200, get(url + "/api/auth/me", token));
            assertEquals(200, get(url + "/test/customer/" + user.getId(), token));
            assertEquals(403, get(url + "/test/customer/" + UUID.randomUUID(), token));
            assertEquals(403, get(url + "/test/unclassified", token));
            user.setRole(Role.ADMIN); // DB role overrides the old token's CUSTOMER claim.
            assertEquals(403, get(url + "/test/customer/" + user.getId(), token));
        } finally { server.stop(); }
    }

    @Test void temporaryEndpointsEnforceEveryRoleOverHttp() throws Exception {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        EntityManager em = mock(EntityManager.class);
        when(emf.createEntityManager()).thenReturn(em);
        java.util.Map<Role, String> tokens = new java.util.EnumMap<>(Role.class);
        java.util.Map<Role, String> paths = java.util.Map.of(
                Role.CUSTOMER, "/api/test/customer",
                Role.COURIER, "/api/test/courier",
                Role.RESTAURANT_OWNER, "/api/test/restaurant-owner",
                Role.ADMIN, "/api/test/admin");
        for (Role role : Role.values()) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setName(role.name());
            user.setEmail(role.name().toLowerCase(java.util.Locale.ROOT) + "@example.test");
            user.setRole(role);
            when(em.find(User.class, user.getId())).thenReturn(user);
            tokens.put(role, new TokenSecurity().createToken(
                    new UserDTO(user.getId().toString(), user.getRolesAsStrings()), "mtogo", "3600000", SECRET));
        }
        Javalin server = App.createApp(emf, SECRET);
        RoleTestController.addRoutes(server);
        server.start(0);
        try {
            String baseUrl = "http://localhost:" + server.port();
            for (Role endpointRole : Role.values()) {
                String url = baseUrl + paths.get(endpointRole);
                assertEquals(401, get(url, null), "Missing token: " + endpointRole);
                assertEquals(401, get(url, "invalid"), "Invalid token: " + endpointRole);
                for (Role accountRole : Role.values()) {
                    int expected = accountRole == endpointRole ? 200 : 403;
                    assertEquals(expected, get(url, tokens.get(accountRole)),
                            accountRole + " accessing " + endpointRole);
                }
            }
        } finally { server.stop(); }
    }
    private int get(String url, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}





