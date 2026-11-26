package app;

import app.config.HibernateConfig;
import app.controller.DeliveryController;
import app.controller.FeatureController;
import app.controller.FeedbackController;
import app.controller.OwnerMenuController;
import app.controller.OrderController;
import app.controller.RestaurantController;
import app.dao.OrderDao;
import app.dao.OwnerMenuDao;
import app.dao.RestaurantDao;
import app.exceptions.ApiException;
import app.exceptions.CheckoutException;
import app.features.EnvironmentFeatureFlags;
import app.features.FeatureCatalog;
import app.features.FeatureFlags;
import app.security.SecurityController;
import app.server.BackendMetrics;
import app.server.Routing;
import app.service.DeliveryService;
import app.service.FeedbackService;
import app.service.OrderService;
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
            String metricsPort = System.getenv("MTOGO_METRICS_PORT");
            createApp(emf, System.getenv("SECRET_KEY"), metricsPort == null ? null : Integer.valueOf(metricsPort))
                    .start(7070);
        } catch (RuntimeException | Error e) {
            if (emf.isOpen()) {
                emf.close();
            }
            throw e;
        }
    }

    public static Javalin createApp(EntityManagerFactory emf, String secret) {
        return createApp(emf, secret, null);
    }

    private static Javalin createApp(EntityManagerFactory emf, String secret, Integer metricsPort) {
        FeatureCatalog catalog = FeatureCatalog.APPLICATION;
        return createApp(emf, secret, metricsPort, catalog,
                EnvironmentFeatureFlags.fromEnvironment(catalog.registered()));
    }

    public static Javalin createApp(EntityManagerFactory emf, String secret, FeatureCatalog catalog, FeatureFlags flags) {
        return createApp(emf, secret, null, catalog, flags);
    }

    private static Javalin createApp(EntityManagerFactory emf, String secret, Integer metricsPort,
                                     FeatureCatalog catalog, FeatureFlags flags) {
        SecurityController security = new SecurityController(emf, secret);
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            if (metricsPort != null) {
                BackendMetrics.configure(config, metricsPort);
            }
        });
        app.exception(ApiException.class, (e, ctx) -> ctx.status(e.getCode()).json(Map.of("error", e.getMessage())));
        app.exception(CheckoutException.class, (e, ctx) -> ctx.status(e.getCode()).json(e.getResponse()));
        app.beforeMatched(security::authenticate);
        app.beforeMatched(security::authorize);
        Routing.registerRoutes(app, security, new OwnerMenuController(new OwnerMenuDao(emf)),
                new RestaurantController(new RestaurantDao(emf)), new OrderController(new OrderService(emf)),
                new DeliveryController(new DeliveryService(emf, new OrderDao())), new FeatureController(flags, catalog),
                new FeedbackController(new FeedbackService(emf, flags)));
        return app;
    }
}
