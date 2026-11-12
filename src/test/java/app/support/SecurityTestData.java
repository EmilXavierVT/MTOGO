package app.support;

import app.entities.enums.Role;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.services.security.PasswordService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;


public final class SecurityTestData {
    public static final String PASSWORD = "IntegrationPassword123!";
    public record Fixtures(Map<Role, UUID> accounts, UUID otherCustomer, UUID restaurant, UUID otherRestaurant) {}
    private SecurityTestData() {}

    public static Fixtures populate(EntityManagerFactory emf) {
        String hash = new PasswordService().hash(PASSWORD);
        Map<Role, UUID> accounts = new EnumMap<>(Role.class);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            for (Role role : Role.values()) {
                User user = account(role.name(), role.name() + "@example.test", role, hash);
                em.persist(user);
                accounts.put(role, user.getId());
            }
            User otherCustomer = account("Other customer", "other-customer@example.test", Role.CUSTOMER, hash);
            User otherOwner = account("Other owner", "other-owner@example.test", Role.RESTAURANT_OWNER, hash);
            em.persist(otherCustomer);
            em.persist(otherOwner);
            Restaurant restaurant = restaurant(em.find(User.class, accounts.get(Role.RESTAURANT_OWNER)), "First address");
            Restaurant otherRestaurant = restaurant(otherOwner, "Second address");
            em.persist(restaurant);
            em.persist(otherRestaurant);
            em.getTransaction().commit();
            return new Fixtures(accounts, otherCustomer.getId(), restaurant.getId(), otherRestaurant.getId());
        }
    }

    private static User account(String name, String email, Role role, String hash) {
        User user = new User();
        user.setName(name);
        user.setEmail(email.toLowerCase(java.util.Locale.ROOT));
        user.setRole(role);
        user.setPasswordHash(hash);
        return user;
    }

    private static Restaurant restaurant(User owner, String address) {
        Restaurant restaurant = new Restaurant();
        restaurant.setOwner(owner);
        restaurant.setAddress(address);
        return restaurant;
    }
}
