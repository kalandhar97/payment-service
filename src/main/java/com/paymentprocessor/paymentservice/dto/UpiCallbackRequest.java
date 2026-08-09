package com.paymentprocessor.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;

/** Asynchronous UPI debit result delivered by the PSP. */
public record UpiCallbackRequest(
        @NotBlank String intentId,
        String providerRef,
        @NotBlank String status,
        String npciTxnId,
        String declineCode) {
}
