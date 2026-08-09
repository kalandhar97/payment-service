package com.paymentprocessor.paymentservice.connector.model;

/** Request to push a UPI collect request to a payer's VPA. */
public record UpiCollectCommand(
        String intentId,
        String payerVpa,
        long amountMinor,
        String currency,
        String note,
        int expirySeconds) {
}
