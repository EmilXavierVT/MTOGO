package app.service;

import org.junit.jupiter.api.Test;

import static app.service.PaymentSimulator.PaymentResult.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentSimulatorTest {
    private final PaymentSimulator simulator = new PaymentSimulator();

    @Test
    void supportsBothLocalOutcomes() {
        assertEquals(APPROVED, simulator.simulate(APPROVED));
        assertEquals(DECLINED, simulator.simulate(DECLINED));
    }

    @Test
    void requiresAnExplicitOutcome() {
        assertThrows(IllegalArgumentException.class, () -> simulator.simulate(null));
    }
}
