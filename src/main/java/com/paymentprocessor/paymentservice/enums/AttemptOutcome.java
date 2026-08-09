package com.paymentprocessor.paymentservice.enums;

/**
 * Outcome of a single {@code PaymentAttempt} against a connector.
 */
public enum AttemptOutcome {
    PENDING,
    APPROVED,
    SOFT_DECLINE,
    HARD_DECLINE,
    ERROR,
    TIMEOUT
}
