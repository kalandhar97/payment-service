package com.paymentprocessor.paymentservice.exception;

public class PaymentValidationException extends ApiException {
    public PaymentValidationException(String message) {
        super(422, "PAYMENT_VALIDATION_ERROR", message);
    }
}
