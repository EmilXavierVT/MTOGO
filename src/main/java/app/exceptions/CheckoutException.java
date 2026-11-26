package app.exceptions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Machine-readable checkout failures let the customer review changes before retrying. */
public class CheckoutException extends ApiException {
    public record ItemPrice(UUID productId, String productName, BigDecimal unitPrice) {}
    public record Response(String code, String error, BigDecimal updatedTotal,
                           UUID productId, List<ItemPrice> products) {}

    private final Response response;

    private CheckoutException(int status, Response response) {
        super(status, response.error());
        this.response = response;
    }

    public static CheckoutException priceChanged(BigDecimal total, List<ItemPrice> products) {
        return new CheckoutException(409, new Response("PRICE_CHANGED",
                "Total changed. Accept the updated total before paying.", total, null, products));
    }

    public static CheckoutException unavailableProduct(UUID productId, boolean missing) {
        return new CheckoutException(missing ? 404 : 409, new Response("PRODUCT_UNAVAILABLE",
                "A dish is no longer available. Remove it from the cart before paying.",
                null, productId, List.of()));
    }

    public Response getResponse() {
        return response;
    }
}
