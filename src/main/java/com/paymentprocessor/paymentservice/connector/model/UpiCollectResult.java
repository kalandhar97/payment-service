package com.paymentprocessor.paymentservice.connector.model;

import com.paymentprocessor.paymentservice.enums.AttemptOutcome;

/**
 * Outcome of initiating a UPI collect. UPI is asynchronous: a successful
 * initiation returns {@code PENDING} with a provider reference; the terminal
 * result arrives later via callback.
 */
public record UpiCollectResult(
        AttemptOutcome outcome,
        String providerRef,
        String declineCode) {
}
