package app.config;

import jakarta.persistence.EntityManagerFactory;

import java.util.Properties;

/** Runs courier HTTP tests on an explicitly selected disposable PostgreSQL database, or H2 locally. */
public final class CourierTestDatabase {
    private CourierTestDatabase() {}

    public static EntityManagerFactory create() {
        String url = System.getenv("MTOGO_COURIER_TEST_JDBC_URL");
        if (url == null || url.isBlank()) return H2TestDatabase.create();
        if (!url.startsWith("jdbc:postgresql://") || !url.endsWith("/mtogo_courier_test")) {
            throw new IllegalArgumentException("Courier tests require a disposable database named mtogo_courier_test");
        }
        Properties properties = new Properties();
        properties.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        properties.setProperty("hibernate.connection.url", url);
        properties.setProperty("hibernate.connection.username", "courier_test");
        properties.setProperty("hibernate.connection.password", "courier_test");
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.hbm2ddl.halt_on_error", "true");
        properties.setProperty("hibernate.show_sql", "false");
        return HibernateEmfBuilder.build(properties);
    }
}
