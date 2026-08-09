package com.paymentprocessor.paymentservice.connector.model;

/** Request to capture (fully or partially) an existing authorization hold. */
public record CaptureCommand(
        String authorizationRef,
        long amountMinor,
        String currency,
        boolean finalCapture) {
}
