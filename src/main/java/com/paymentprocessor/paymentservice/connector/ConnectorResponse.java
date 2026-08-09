package com.paymentprocessor.paymentservice.connector;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lenient view over a gateway/PSP JSON response. Unknown fields are ignored so a
 * provider adding attributes never breaks deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectorResponse(
        String status,
        String reference,
        String providerRef,
        String authCode,
        String rrn,
        String arn,
        String eci,
        String avsResult,
        String cvvResult,
        String declineCode,
        String mappedDeclineCode,
        String acsUrl,
        String intentUri,
        String qr,
        Instant expiresAt) {
}
