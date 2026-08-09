package com.paymentprocessor.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;

/** Asynchronous 3-D Secure authentication result for a card intent. */
public record ThreeDsCallbackRequest(
        @NotBlank String intentId,
        @NotBlank String status,
        String eci,
        String cavvRef,
        Boolean liabilityShift) {
}
