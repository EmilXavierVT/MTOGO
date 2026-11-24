package app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calculates commission on the order value in DKK excluding VAT. */
public class CommissionService {
    private static final BigDecimal LOWER_BOUND = new BigDecimal("100");
    private static final BigDecimal UPPER_BOUND = new BigDecimal("1000");
    private static final BigDecimal LOWER_RATE = new BigDecimal("0.06");
    private static final BigDecimal UPPER_RATE = new BigDecimal("0.03");
    private static final BigDecimal LINEAR_INTERCEPT = new BigDecimal("1900");
    private static final BigDecimal LINEAR_DIVISOR = new BigDecimal("30000");

    /**
     * Keeps the net amount and rate unrounded, then rounds commission to two
     * decimals with HALF_UP. Missing or negative net amounts are rejected.
     */
    public BigDecimal calculate(BigDecimal netAmount) {
        if (netAmount == null || netAmount.signum() < 0) {
            throw new IllegalArgumentException("Net amount must be non-negative");
        }
        if (netAmount.compareTo(LOWER_BOUND) <= 0) {
            return netAmount.multiply(LOWER_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        if (netAmount.compareTo(UPPER_BOUND) >= 0) {
            return netAmount.multiply(UPPER_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        // x * (19/300 - x/30000) = x * (1900 - x) / 30000.
        // Divide only once at the final monetary scale to avoid rounding the rate.
        return netAmount.multiply(LINEAR_INTERCEPT.subtract(netAmount))
                .divide(LINEAR_DIVISOR, 2, RoundingMode.HALF_UP);
    }
}
