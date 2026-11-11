package app;

import app.controller.SystemController;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinThymeleaf;

public class App {
    public static void initiate()
    {
        Javalin app = Javalin.create(config ->
        {

        }).start(7072);

        SystemController.addRoutes(app);
        // Routing
        // add controllers here

    }
}
