package app.server;

import app.controller.DeliveryController;
import app.controller.OrderController;
import app.controller.OwnerMenuController;
import app.controller.RestaurantController;
import app.controller.SystemController;
import app.entities.enums.Role;
import app.security.SecurityController;
import io.javalin.Javalin;

public final class Routing {
    private Routing() {}

    public static void registerRoutes(Javalin app, SecurityController security, OwnerMenuController ownerMenus,
                                      RestaurantController restaurant, OrderController orders, DeliveryController deliveries) {
        SystemController.addRoutes(app);
        app.post("/api/auth/login", security::login);
        app.post("/api/auth/register", security::register);
        app.get("/api/auth/me", security::me, Role.values());
        app.get("/api/owner/menus", ownerMenus::listMenus, Role.RESTAURANT_OWNER);
        app.post("/api/owner/menus/{menuId}/products", ownerMenus::createProduct, Role.RESTAURANT_OWNER);
        app.put("/api/owner/menus/{menuId}/products/{productId}", ownerMenus::updateProduct, Role.RESTAURANT_OWNER);
        app.get("/api/restaurants", restaurant :: displayRestaurants, Role.CUSTOMER);
        app.get("/api/restaurants/{restaurantId}/menus", restaurant :: displayRestaurantMenus, Role.CUSTOMER);
        app.post("/api/customer/orders", orders :: handleCheckout, Role.CUSTOMER);
        app.get("/api/customer/orders/{orderId}", orders::showConfirmation, Role.CUSTOMER);
        app.post("/api/courier/orders/{orderId}/delivery/accept", deliveries::handleDeliveryAccept, Role.COURIER);
        app.post("/api/courier/orders/{orderId}/delivery/pickup", deliveries::handlePickupDelivery, Role.COURIER);
        app.post("/api/courier/orders/{orderId}/delivery/delivered", deliveries::handleDelivered, Role.COURIER);
    }
}


