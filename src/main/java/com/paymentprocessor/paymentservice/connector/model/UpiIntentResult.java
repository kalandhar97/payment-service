package com.paymentprocessor.paymentservice.connector.model;

/** A UPI intent link and its QR payload for the payer to scan or open. */
public record UpiIntentResult(
        String providerRef,
        String intentUri,
        String qrPayload) {
}
