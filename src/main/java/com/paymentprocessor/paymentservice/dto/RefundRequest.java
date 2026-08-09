package com.paymentprocessor.paymentservice.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Refund request. A null {@code amountMinor} refunds the full refundable amount. */
public record RefundRequest(
        @Positive Long amountMinor,
        @Size(max = 200) String reason) {
}
