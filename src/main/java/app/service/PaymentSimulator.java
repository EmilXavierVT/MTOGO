package app.service;

public class PaymentSimulator {
    public enum PaymentResult {
        APPROVED,
        DECLINED
    }

    public PaymentResult simulate(PaymentResult requestedResult) {
        if (requestedResult == null) {
            throw new IllegalArgumentException("Simulation result is required");
        }
        return requestedResult;
    }
}
