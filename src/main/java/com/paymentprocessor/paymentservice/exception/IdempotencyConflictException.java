package com.paymentprocessor.paymentservice.exception;

public class IdempotencyConflictException extends ApiException {
    public IdempotencyConflictException(String message) {
        super(409, "IDEMPOTENCY_CONFLICT", message);
    }
}
