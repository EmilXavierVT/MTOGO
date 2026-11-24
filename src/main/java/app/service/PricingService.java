package app.service;

import app.controller.OrderController.OrderItemRequest;

import java.math.BigDecimal;
import java.util.List;

public class PricingService {

    public record PricingResult(
            BigDecimal total,
            BigDecimal netAmount,
            BigDecimal commission
    ) {}

    public PricingResult calculate(List<OrderItemRequest> products) {
        BigDecimal dummyUnitPrice = unitPrice();

        BigDecimal total = products.stream()
                .map(item -> dummyUnitPrice.multiply(
                        BigDecimal.valueOf(item.quantity())
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netAmount = total.divide(new BigDecimal("1.25"));
        BigDecimal commission = new BigDecimal("0.00");

        return new PricingResult(total, netAmount, commission);
    }

    public BigDecimal unitPrice() {
        return new BigDecimal("50.00");
    }
}
