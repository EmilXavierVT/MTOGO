package app.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import java.util.UUID;

/** Uses the production entity registry without reading local config or touching PostgreSQL. */
public final class H2TestDatabase {
    private H2TestDatabase() {}

    public static EntityManagerFactory create() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:security_" + UUID.randomUUID());
        properties.setProperty("hibernate.connection.username", "sa");
        properties.setProperty("hibernate.connection.password", "");
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.hbm2ddl.halt_on_error", "true");
        properties.setProperty("hibernate.show_sql", "false");
        return HibernateEmfBuilder.build(properties);
    }
}
