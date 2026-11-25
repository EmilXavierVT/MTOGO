package app.controller;
import app.entities.orderFlow.Order;
import app.entities.enums.Status;
import app.entities.orderFlow.OrderLine;
import app.exceptions.ApiException;
import app.security.SecurityController;
import io.javalin.http.Context;
import app.service.OrderService;
import app.service.PaymentSimulator.PaymentResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OrderController {
    private final OrderService orderService;

    public record OrderItemRequest(UUID productId, int quantity) {}

    public record CheckoutRequest(
            UUID restaurantId,
            List<OrderItemRequest> products,
            String name,
            String phone,
            String address,
            BigDecimal acceptedTotal,
            PaymentResult simulatedPaymentResult
    ) {}
    public record RestaurantResponse(UUID id, String name, String address) {}
    public record LineResponse(UUID productId, String productName, int quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {
        private static LineResponse from(OrderLine line) {
            return new LineResponse(line.getProductId(), line.getProductName(), line.getQuantity(),
                    line.getUnitPrice(), line.getLineTotal());
        }
    }
    public record OrderResponse(UUID id, Status status, BigDecimal total, String orderTime,
                                RestaurantResponse restaurant, String customerName,
                                String customerPhone, String deliveryAddress, List<LineResponse> lines,
                                BigDecimal refundedAmount, String refundedAt) {
        private static OrderResponse from(Order order) {
            var restaurant = order.getRestaurant();
            return new OrderResponse(order.getId(), order.getStatus(), order.getTotalPrice(),
                    order.getOrderTime().toString(),
                    new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getAddress()),
                    order.getCustomerName(), order.getCustomerPhone(), order.getDeliveryAddress(),
                    order.getLines().stream().map(LineResponse::from).toList(),
                    order.getRefundedAmount(),
                    order.getRefundedAt() == null ? null : order.getRefundedAt().toString());
        }
    }
    public record RestaurantTransitionResponse(UUID id, Status status, BigDecimal refundedAmount,
                                               String refundedAt) {
        private static RestaurantTransitionResponse from(Order order) {
            return new RestaurantTransitionResponse(order.getId(), order.getStatus(), order.getRefundedAmount(),
                    order.getRefundedAt() == null ? null : order.getRefundedAt().toString());
        }
    }

    public OrderController(OrderService orderService){
        this.orderService = orderService;
    }

    public void handleCheckout(Context ctx){
        SecurityController.CurrentUser customer = ctx.attribute("user");
        CheckoutRequest request = ctx.bodyAsClass(CheckoutRequest.class);
        Order order = orderService.checkout(customer, request);
        ctx.header("Cache-Control", "no-store");
        ctx.status(201).json(OrderResponse.from(order));
    }

    public void showConfirmation(Context ctx) {
        SecurityController.CurrentUser customer = ctx.attribute("user");
        String value = ctx.pathParam("orderId");
        UUID orderId;
        try {
            orderId = UUID.fromString(value);
            if (!orderId.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "orderId must be a UUID");
        }
        ctx.header("Cache-Control", "no-store");
        ctx.json(OrderResponse.from(orderService.findCustomerOrder(customer, orderId)));
    }

    public void listCustomerOrders(Context ctx) {
        SecurityController.CurrentUser customer = ctx.attribute("user");
        ctx.header("Cache-Control", "no-store");
        ctx.json(orderService.findCustomerOrders(customer).stream().map(OrderResponse::from).toList());
    }

    public void acceptRestaurantOrder(Context ctx) {
        transition(ctx, Status.ACCEPTED);
    }

    public void rejectRestaurantOrder(Context ctx) {
        transition(ctx, Status.REJECTED);
    }

    public void readyRestaurantOrder(Context ctx) {
        transition(ctx, Status.READY_FOR_DELIVERY);
    }

    private void transition(Context ctx, Status target) {
        UUID orderId;
        try {
            String value = ctx.pathParam("orderId");
            orderId = UUID.fromString(value);
            if (!orderId.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "orderId must be a UUID");
        }
        SecurityController.CurrentUser owner = ctx.attribute("user");
        ctx.header("Cache-Control", "no-store");
        ctx.json(RestaurantTransitionResponse.from(orderService.transitionRestaurantOrder(owner, orderId, target)));
    }
}
