package app.controller;
import app.entities.orderFlow.Order;
import app.entities.enums.Status;
import app.entities.restaurant.Restaurant;
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
    public record RestaurantResponse(UUID id, String name, String address) {
        public static RestaurantResponse from(Restaurant restaurant) {
            return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getAddress());
        }
    }
    public record OrderResponse(UUID id, Status status, BigDecimal total, RestaurantResponse restaurant) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getStatus(), order.getTotalPrice(),
                    RestaurantResponse.from(order.getRestaurant()));
        }
    }

    public OrderController(OrderService orderService){
        this.orderService = orderService;
    }

    public void handleCheckout(Context ctx){
        SecurityController.CurrentUser customer = ctx.attribute("user");
        CheckoutRequest request = ctx.bodyAsClass(CheckoutRequest.class);
        Order order = orderService.checkout(customer, request);
        ctx.status(201).json(OrderResponse.from(order));
    }

    public void getOrder(Context ctx) {
        UUID orderId;
        try {
            orderId = UUID.fromString(ctx.pathParam("orderId"));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "orderId must be a UUID");
        }
        SecurityController.CurrentUser customer = ctx.attribute("user");
        ctx.json(OrderResponse.from(orderService.getCustomerOrder(customer, orderId)));
    }
}
