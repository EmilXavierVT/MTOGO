package app.controller;

import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.exceptions.ApiException;
import app.security.SecurityController;
import app.service.DeliveryService;
import io.javalin.http.Context;

import java.util.Objects;
import java.util.UUID;

public class DeliveryController {
    private final DeliveryService deliveryService;

    public record DeliveryResponse(UUID id, UUID orderId, UUID courierId, Status status,
                                   String claimedTime, String pickupTime, String deliveryTime) {
        private static DeliveryResponse from(Delivery delivery) {
            return new DeliveryResponse(delivery.getId(), delivery.getOrder().getId(),
                    delivery.getCourier().getId(), delivery.getStatus(),
                    Objects.toString(delivery.getClaimedTime(), null),
                    Objects.toString(delivery.getPickupTime(), null),
                    Objects.toString(delivery.getDeliveryTime(), null));
        }
    }

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
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
