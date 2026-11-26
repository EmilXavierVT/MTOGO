package app.controller;

import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Restaurant;
import app.exceptions.ApiException;
import app.security.SecurityController;
import app.service.DeliveryService;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class DeliveryController {
    private final DeliveryService deliveryService;

    public record RestaurantResponse(UUID id, String name, String address) {
        private static RestaurantResponse from(Restaurant restaurant) {
            return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getAddress());
        }
    }

    public record AvailableOrderResponse(UUID orderId, String orderTime, RestaurantResponse restaurant) {
        private static AvailableOrderResponse from(Order order) {
            return new AvailableOrderResponse(order.getId(), order.getOrderTime().toString(),
                    RestaurantResponse.from(order.getRestaurant()));
        }
    }

    public record ActiveDeliveryResponse(UUID id, UUID orderId, Status status, RestaurantResponse restaurant,
                                         String customerName, String customerPhone, String deliveryAddress,
                                         String claimedTime, String pickupTime) {
        private static ActiveDeliveryResponse from(Delivery delivery) {
            Order order = delivery.getOrder();
            return new ActiveDeliveryResponse(delivery.getId(), order.getId(), delivery.getStatus(),
                    RestaurantResponse.from(order.getRestaurant()), order.getCustomerName(), order.getCustomerPhone(),
                    order.getDeliveryAddress(), Objects.toString(delivery.getClaimedTime(), null),
                    Objects.toString(delivery.getPickupTime(), null));
        }
    }

    public record DeliveryResponse(UUID id, UUID orderId, UUID courierId, Status status,
                                   String claimedTime, String pickupTime, String deliveryTime, BigDecimal earnedBonus) {
        private static DeliveryResponse from(Delivery delivery) {
            return new DeliveryResponse(delivery.getId(), delivery.getOrder().getId(),
                    delivery.getCourier().getId(), delivery.getStatus(),
                    Objects.toString(delivery.getClaimedTime(), null),
                    Objects.toString(delivery.getPickupTime(), null),
                    Objects.toString(delivery.getDeliveryTime(), null), delivery.getEarnedBonus());
        }
    }

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    public void listAvailableOrders(Context ctx) {
        SecurityController.CurrentUser courier = ctx.attribute("user");
        ctx.header("Cache-Control", "no-store");
        ctx.json(deliveryService.availableOrders(courier).stream().map(AvailableOrderResponse::from).toList());
    }

    public void showActiveDelivery(Context ctx) {
        SecurityController.CurrentUser courier = ctx.attribute("user");
        Delivery delivery = deliveryService.activeDelivery(courier);
        ctx.header("Cache-Control", "no-store");
        if (delivery == null) {
            ctx.status(204);
            return;
        }
        ctx.json(ActiveDeliveryResponse.from(delivery));
    }

    public void handleDeliveryAccept(Context ctx) {
        SecurityController.CurrentUser courier = ctx.attribute("user");
        Delivery delivery = deliveryService.acceptDelivery(courier, orderId(ctx));
        ctx.header("Cache-Control", "no-store");
        ctx.status(201).json(DeliveryResponse.from(delivery));
    }

    public void handlePickupDelivery(Context ctx) {
        SecurityController.CurrentUser courier = ctx.attribute("user");
        Delivery delivery = deliveryService.pickupDelivery(courier, orderId(ctx));
        ctx.header("Cache-Control", "no-store");
        ctx.json(DeliveryResponse.from(delivery));
    }

    public void handleDelivered(Context ctx) {
        SecurityController.CurrentUser courier = ctx.attribute("user");
        Delivery delivery = deliveryService.deliveredDelivery(courier, orderId(ctx));
        ctx.header("Cache-Control", "no-store");
        ctx.json(DeliveryResponse.from(delivery));
    }

    private static UUID orderId(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("orderId"));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Invalid order ID");
        }
    }
}
