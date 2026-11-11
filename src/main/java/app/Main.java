package app;

import app.config.HibernateConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public class Main {
    public static void main(String[] args) {
        App.initiate();
        EntityManagerFactory emf = HibernateConfig.getEntityManagerFactory();
        EntityManager entityManager = emf.createEntityManager();
    }
}
