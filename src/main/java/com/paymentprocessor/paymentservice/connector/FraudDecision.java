package com.paymentprocessor.paymentservice.connector;

/**
 * Result of a pre-authorization risk evaluation. {@code requireThreeDs} asks the
 * card flow to step up to 3-D Secure; {@code approved == false} blocks the payment.
 */
public record FraudDecision(boolean approved, boolean requireThreeDs, int score, String reason) {

    public static FraudDecision approve() {
        return new FraudDecision(true, false, 0, "OK");
    }
}
