package com.paymentprocessor.paymentservice.connector.model;

import java.time.Instant;

import com.paymentprocessor.paymentservice.enums.AttemptOutcome;

/**
 * Outcome of an authorization request. When {@code outcome} is {@code PENDING}
 * and {@code acsUrl} is present the caller must complete a 3-D Secure challenge
 * before the hold is confirmed via callback.
 */
public record AuthorizeResult(
        AttemptOutcome outcome,
        String connectorRef,
        String authCode,
        String rrn,
        String arn,
        String eci,
        String avsResult,
        String cvvResult,
        String networkDeclineCode,
        String mappedDeclineCode,
        String acsUrl,
        Instant expiresAt) {
}
