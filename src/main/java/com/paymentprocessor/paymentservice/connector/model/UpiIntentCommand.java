package com.paymentprocessor.paymentservice.connector.model;

/** Request to generate a UPI intent (deep link / QR) for a pull payment. */
public record UpiIntentCommand(
        String intentId,
        long amountMinor,
        String currency,
        String note) {
}
