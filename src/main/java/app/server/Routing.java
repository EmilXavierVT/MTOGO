package app.server;

import app.controller.SystemController;
import app.entities.enums.Role;
import app.security.SecurityController;
import io.javalin.Javalin;

public final class Routing {
    private Routing() {}

    public static void registerRoutes(Javalin app, SecurityController security) {
        SystemController.addRoutes(app);
        app.post("/api/auth/login", security::login);
        app.post("/api/auth/register", security::register);
        app.get("/api/auth/me", security::me, Role.values());
    }
}


