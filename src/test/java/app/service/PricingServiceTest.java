package app.service;

import app.controller.OrderController.OrderItemRequest;
import app.entities.restaurant.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingServiceTest {
    private final PricingService service = new PricingService();

    @Test
    void calculatesCommissionOnNetValueOfActualMenuPricesAndQuantities() {
        Product first = product("20.00");
        Product second = product("126.25");

        var pricing = service.calculate(List.of(
                        new OrderItemRequest(first.getId(), 3),
                        new OrderItemRequest(second.getId(), 2)),
                Map.of(first.getId(), first, second.getId(), second));

        assertAll(
                () -> assertAmount("312.50", pricing.total()),
                () -> assertAmount("250.00", pricing.netAmount()),
                () -> assertEquals(new BigDecimal("13.75"), pricing.commission())
        );
    }

    @Test
    void retainsFractionalOreInTheNetBasisUntilCommissionIsRounded() {
        Product product = product("0.31");

        var pricing = service.calculate(List.of(new OrderItemRequest(product.getId(), 1)),
                Map.of(product.getId(), product));

        assertAll(
                () -> assertAmount("0.31", pricing.total()),
                () -> assertAmount("0.248", pricing.netAmount()),
                () -> assertEquals(new BigDecimal("0.01"), pricing.commission())
        );
    }

    private static Product product(String price) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
