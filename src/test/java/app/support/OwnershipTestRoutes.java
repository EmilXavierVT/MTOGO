package app.support;

import app.entities.enums.Role;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.security.Ownership;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;


public final class OwnershipTestRoutes {
    public record Change(String value) {}
    private OwnershipTestRoutes() {}

    public static void register(Javalin app, EntityManagerFactory emf) {
        app.get("/integration/customers/{id}", ctx -> customer(ctx, emf, false), Role.CUSTOMER);
        app.patch("/integration/customers/{id}", ctx -> customer(ctx, emf, true), Role.CUSTOMER);
        app.get("/integration/restaurants/{id}", ctx -> restaurant(ctx, emf, false), Role.RESTAURANT_OWNER);
        app.patch("/integration/restaurants/{id}", ctx -> restaurant(ctx, emf, true), Role.RESTAURANT_OWNER);
    }

    private static void customer(Context ctx, EntityManagerFactory emf, boolean write) {
        try (EntityManager em = emf.createEntityManager()) {
            User customer = em.find(User.class, UUID.fromString(ctx.pathParam("id")));
            if (customer == null) throw new NotFoundResponse();
            Ownership.requireCustomer(ctx.attribute("user"), customer.getId());
            if (write) {
                em.getTransaction().begin();
                customer.setName(ctx.bodyAsClass(Change.class).value());
                em.getTransaction().commit();
            }
            ctx.json(Map.of("id", customer.getId(), "name", customer.getName()));
        }
    }

    private static void restaurant(Context ctx, EntityManagerFactory emf, boolean write) {
        try (EntityManager em = emf.createEntityManager()) {
            Restaurant restaurant = em.find(Restaurant.class, UUID.fromString(ctx.pathParam("id")));
            if (restaurant == null) throw new NotFoundResponse();
            Ownership.requireRestaurantOwner(ctx.attribute("user"), restaurant.getOwner().getId());
            if (write) {
                em.getTransaction().begin();
                restaurant.setAddress(ctx.bodyAsClass(Change.class).value());
                em.getTransaction().commit();
            }
            ctx.json(Map.of("id", restaurant.getId(), "address", restaurant.getAddress()));
        }
    }
}
