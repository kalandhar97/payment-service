package com.paymentprocessor.paymentservice.dto;

import java.time.Instant;

/** Public representation of a payment intent returned by the API. */
public record PaymentResponse(
        String id,
        String merchantId,
        String customerId,
        String paymentMethod,
        String status,
        String subStatus,
        Long amountMinor,
        String currency,
        String captureMethod,
        String connectorId,
        Long authorizedMinor,
        Long capturedMinor,
        Long refundedMinor,
        String statementDescriptor,
        String description,
        String acsUrl,
        String upiIntentUri,
        Instant createdAt,
        Instant updatedAt) {
}
