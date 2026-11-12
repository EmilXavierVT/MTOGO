package app.controller;

import io.javalin.Javalin;

public class SystemController {
    public static void addRoutes(Javalin app) {
        app.get("/", ctx -> ctx.result("Server running"));
    }
}
