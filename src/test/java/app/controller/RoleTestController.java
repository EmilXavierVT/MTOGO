package app.controller;

import app.entities.enums.Role;
import app.security.SecurityController;
import io.javalin.Javalin;
import io.javalin.http.Context;

/** Test-only endpoints registered explicitly by the HTTP access suites. */
public final class RoleTestController {
    private RoleTestController() {}

    public static void addRoutes(Javalin app) {
        app.get("/api/test/customer", RoleTestController::currentUser, Role.CUSTOMER);
        app.get("/api/test/courier", RoleTestController::currentUser, Role.COURIER);
        app.get("/api/test/restaurant-owner", RoleTestController::currentUser, Role.RESTAURANT_OWNER);
        app.get("/api/test/admin", RoleTestController::currentUser, Role.ADMIN);
    }

    private static void currentUser(Context ctx) {
        SecurityController.CurrentUser user = ctx.attribute("user");
        ctx.json(user);
    }
}

