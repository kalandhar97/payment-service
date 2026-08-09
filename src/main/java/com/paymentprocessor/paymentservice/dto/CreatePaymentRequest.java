package com.paymentprocessor.paymentservice.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Create-payment request. For {@code CARD} supply an {@code instrumentToken}; for
 * {@code UPI} supply either a {@code payerVpa} (COLLECT flow) or set
 * {@code upiFlow=INTENT} to receive a scannable UPI link.
 */
public record CreatePaymentRequest(
        @NotBlank String merchantId,
        String customerId,
        @NotBlank String paymentMethod,
        String instrumentToken,
        String payerVpa,
        String upiFlow,
        @NotNull @Positive Long amountMinor,
        @NotBlank @Size(min = 3, max = 3) String currency,
        String captureMethod,
        @Size(max = 100) String statementDescriptor,
        @Size(max = 500) String description,
        Map<String, String> metadata) {
}
