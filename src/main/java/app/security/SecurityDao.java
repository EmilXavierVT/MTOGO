package app.security;

import app.entities.users.User;
import app.exceptions.ValidationException;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.Locale;
import java.util.UUID;

public class SecurityDao {
    private final EntityManagerFactory emf;
    public SecurityDao(EntityManagerFactory emf) { this.emf = emf; }

    public User getVerifiedUser(String email, String password) {
        try (EntityManager em = emf.createEntityManager()) {
            User user = em.createQuery("select u from User u where u.email = :email", User.class)
                    .setParameter("email", normalizeEmail(email)).getResultStream().findFirst().orElse(null);
            if (user == null || !user.verifyPassword(password))
                throw new ValidationException("Invalid email or password");
            return user;
        }
    }

    public User findById(UUID id) {
        try (EntityManager em = emf.createEntityManager()) { return em.find(User.class, id); }
    }

    public User createUser(String name, String password, String email) {
        try (EntityManager em = emf.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                User user = new User(name, password, normalizeEmail(email));
                em.persist(user);
                tx.commit();
                return user;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

