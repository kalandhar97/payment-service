package com.paymentprocessor.paymentservice.dto;

/** UPI intent link + QR payload for the payer to scan or open. */
public record UpiIntentResponse(
        String intentId,
        String providerRef,
        String intentUri,
        String qrPayload,
        int expirySeconds) {
}
