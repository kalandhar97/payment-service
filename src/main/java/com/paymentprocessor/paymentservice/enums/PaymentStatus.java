package com.paymentprocessor.paymentservice.enums;

/**
 * Lifecycle states of a {@code PaymentIntent}. Persisted as the string name.
 * Allowed transitions are enforced at runtime via the {@code intent_transitions}
 * table (see {@code PaymentStateMachine}); this enum only defines the vocabulary.
 */
public enum PaymentStatus {
    CREATED,
    AUTHORIZING,
    AUTHORIZED,
    CAPTURING,
    PARTIALLY_CAPTURED,
    CAPTURED,
    CONFIRMED,
    SETTLED,
    CANCELLED,
    FAILED,
    EXPIRED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
