package app.service;

import app.controller.OrderController.OrderItemRequest;
import app.entities.restaurant.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PricingService {
    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.25");
    private final CommissionService commissionService = new CommissionService();

    public record PricingResult(
            BigDecimal total,
            BigDecimal netAmount,
            BigDecimal commission
    ) {}

    /** Uses the VAT-inclusive menu prices already validated and locked by checkout. */
    public PricingResult calculate(List<OrderItemRequest> products, Map<UUID, Product> validatedProducts) {
        BigDecimal total = products.stream()
                .map(item -> validatedProducts.get(item.productId()).getPrice().multiply(
                        BigDecimal.valueOf(item.quantity())
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netAmount = total.divide(VAT_DIVISOR);
        BigDecimal commission = commissionService.calculate(netAmount);

        return new PricingResult(total, netAmount, commission);
    }
}
