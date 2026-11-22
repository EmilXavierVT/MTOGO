package app.controller;
import app.entities.orderFlow.Order;
import app.entities.enums.Status;
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
    public record OrderResponse(UUID id, Status status, BigDecimal total) {}

    public OrderController(OrderService orderService){
        this.orderService = orderService;
    }

    public void handleCheckout(Context ctx){
        SecurityController.CurrentUser customer = ctx.attribute("user");
        CheckoutRequest request = ctx.bodyAsClass(CheckoutRequest.class);
        Order order = orderService.checkout(customer, request);
        ctx.status(201).json(new OrderResponse(order.getId(), order.getStatus(), order.getTotalPrice()));
    }
}
