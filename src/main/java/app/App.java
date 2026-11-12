package app;

import app.config.HibernateConfig;
import app.exceptions.ApiException;
import app.security.SecurityController;
import app.server.Routing;
import io.javalin.Javalin;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

public class App {
    public static void initiate() {
        EntityManagerFactory emf = HibernateConfig.getEntityManagerFactory();
        createApp(emf, System.getenv("SECRET_KEY")).start(7070);
    }

    public static Javalin createApp(EntityManagerFactory emf, String secret) {
        SecurityController security = new SecurityController(emf, secret);
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.allowHost("http://localhost:5173")));
        });
        app.exception(ApiException.class, (e, ctx) -> ctx.status(e.getCode()).json(Map.of("error", e.getMessage())));
        app.beforeMatched(security::authenticate);
        app.beforeMatched(security::authorize);
        Routing.registerRoutes(app, security);
        return app;
    }
}
