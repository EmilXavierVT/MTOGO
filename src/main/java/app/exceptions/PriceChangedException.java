package app.exceptions;

import java.math.BigDecimal;

public class PriceChangedException extends ApiException {
    private final BigDecimal updatedTotal;

    public PriceChangedException(BigDecimal updatedTotal) {
        super(409, "Total changed. Accept the updated total: " + updatedTotal);
        this.updatedTotal = updatedTotal;
    }

    public BigDecimal getUpdatedTotal() {
        return updatedTotal;
    }
}
