package com.paymentprocessor.paymentservice.connector;

/**
 * Result of a limit-service reserve call.
 *
 * <p>Unlike {@link FraudDecision}, this fails <b>closed</b>: {@code approved}
 * is {@code false} whenever limit-service declines the reservation (a hard
 * limit was breached) AND whenever limit-service could not be reached at all.
 * Silently auto-approving here would let a payment bypass velocity/exposure
 * controls during an outage, which is not a safe default for a limits system
 * (unlike fraud scoring, which is a risk signal rather than a hard control) -
 * callers should surface this as a retryable decline, not a permanent one.
 */
public record LimitDecisionResult(boolean approved, boolean serviceUnavailable,
                                  String reservationId, String status, String reason) {

    public static LimitDecisionResult unavailable(String reason) {
        return new LimitDecisionResult(false, true, null, null, reason);
    }
}
