package app.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommissionServiceTest {
    private final CommissionService service = new CommissionService();

    @Test
    void coversZeroAndBothRateBoundaries() {
        assertAll(
                () -> assertCommission("0", "0.00"),
                () -> assertCommission("99.99", "6.00"),
                () -> assertCommission("100.00", "6.00"),
                () -> assertCommission("100.01", "6.00"),
                () -> assertCommission("999.99", "30.00"),
                () -> assertCommission("1000.00", "30.00"),
                () -> assertCommission("1000.01", "30.00")
        );
    }

    @Test
    void distinguishesRateBandsWhenWholeOreRoundingWouldHideTheDifference() {
        assertAll(
                () -> assertCommission("99.915", "5.99"),
                () -> assertCommission("100.08772", "6.00"),
                () -> assertCommission("999.83", "30.00"),
                () -> assertCommission("1000.17", "30.01")
        );
    }

    @Test
    void matchesTheAgreedExamplesWithoutAnExtraFee() {
        assertAll(
                () -> assertCommission("250", "13.75"),
                () -> assertCommission("500", "23.33"),
                () -> assertCommission("950", "30.08"),
                () -> assertCommission("1000", "30.00"),
                () -> assertCommission("1100", "33.00")
        );
    }

    @Test
    void retainsTheAcceptedDecreaseBetween950And1000() {
        assertTrue(service.calculate(new BigDecimal("950"))
                .compareTo(service.calculate(new BigDecimal("1000"))) > 0);
    }

    @Test
    void roundsHalfOreUpOnlyAtTheFinalCommission() {
        assertAll(
                () -> assertCommission("0.25", "0.02"),
                () -> assertCommission("0.2499999999999999999999999999", "0.01"),
                () -> assertCommission("333.33", "17.41"),
                () -> assertCommission("1000.50", "30.02")
        );
    }

    @Test
    void rejectsNegativeAmountsBeforeRounding() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.calculate(new BigDecimal("-0.001"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.calculate(new BigDecimal("-100"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.calculate(new BigDecimal("-1000")))
        );
    }

    @Test
    void rejectsMissingAmounts() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(null));
    }

    private void assertCommission(String netAmount, String expectedCommission) {
        assertEquals(new BigDecimal(expectedCommission), service.calculate(new BigDecimal(netAmount)),
                "Commission for net amount " + netAmount);
    }
}
