package com.paymentprocessor.paymentservice.exception;

import java.time.Instant;
import java.util.List;

/** Standard error envelope returned by the API for any non-2xx outcome. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<String> details) {
}
