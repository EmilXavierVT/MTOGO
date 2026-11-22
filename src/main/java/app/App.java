package app;

import app.config.HibernateConfig;
import app.controller.OwnerMenuController;
import app.dao.OwnerMenuDao;
import app.exceptions.ApiException;
import app.security.SecurityController;
import app.server.Routing;
import io.javalin.Javalin;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

public class App {
    public static void initiate() {
        EntityManagerFactory emf = HibernateConfig.getEntityManagerFactory();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (emf.isOpen()) {
                emf.close();
            }
        }));

        try {
            createApp(emf, System.getenv("SECRET_KEY")).start(7070);
        } catch (RuntimeException | Error e) {
            if (emf.isOpen()) {
                emf.close();
            }
            throw e;
        }
    }

    public static Javalin createApp(EntityManagerFactory emf, String secret) {
        SecurityController security = new SecurityController(emf, secret);
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.allowHost("http://localhost:3000")));
        });
        app.exception(ApiException.class, (e, ctx) -> ctx.status(e.getCode()).json(Map.of("error", e.getMessage())));
        app.beforeMatched(security::authenticate);
        app.beforeMatched(security::authorize);
        Routing.registerRoutes(app, security, new OwnerMenuController(new OwnerMenuDao(emf)));
        return app;
    }
}
