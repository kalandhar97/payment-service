package com.paymentprocessor.paymentservice.connector.model;

/** Request to refund a settled/captured amount back to the customer. */
public record RefundCommand(
        String captureRef,
        long amountMinor,
        String currency,
        String reason) {
}
