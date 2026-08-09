package com.paymentprocessor.paymentservice.dto;

import jakarta.validation.constraints.Positive;

/** Capture request. A null {@code amountMinor} captures the full remaining amount. */
public record CaptureRequest(
        @Positive Long amountMinor,
        Boolean finalCapture) {
}
