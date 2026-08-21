package com.paymentprocessor.paymentservice.support;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.UpiFlow;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;

/**
 * Validates create-payment requests and method-specific constraints. Other lifecycle
 * preconditions are checked by the individual command handlers because they depend on
 * the current state of the loaded aggregate.
 */
@Component
public class PaymentIntentValidator {

    private static final int MAX_METADATA_KEYS = 50;
    private static final int MAX_METADATA_KEY_LENGTH = 40;
    private static final int MAX_METADATA_VALUE_LENGTH = 500;

    private final PaymentMethodResolver methodResolver;

    public PaymentIntentValidator(PaymentMethodResolver methodResolver) {
        this.methodResolver = methodResolver;
    }

    public void validateCreate(CreatePaymentRequest req, PaymentMethod method) {
        if (req.amountMinor() == null || req.amountMinor() <= 0) {
            throw new PaymentValidationException("amountMinor must be positive");
        }
        if (method == PaymentMethod.CARD
                && (req.instrumentToken() == null || req.instrumentToken().isBlank())) {
            throw new PaymentValidationException("instrumentToken is required for CARD payments");
        }
        if (method == PaymentMethod.UPI) {
            validateUpiCreate(req);
        }
        validateMetadata(req);
    }

    private void validateUpiCreate(CreatePaymentRequest req) {
        UpiFlow flow = methodResolver.resolveUpiFlow(req.upiFlow());
        if (flow == UpiFlow.COLLECT
                && (req.payerVpa() == null || req.payerVpa().isBlank())) {
            throw new PaymentValidationException(
                    "payerVpa is required for UPI COLLECT flow (or set upiFlow=INTENT)");
        }
    }

    private void validateMetadata(CreatePaymentRequest req) {
        if (req.metadata() == null) {
            return;
        }
        if (req.metadata().size() > MAX_METADATA_KEYS) {
            throw new PaymentValidationException("metadata supports at most " + MAX_METADATA_KEYS + " keys");
        }
        req.metadata().forEach((k, v) -> {
            if (k != null && k.length() > MAX_METADATA_KEY_LENGTH) {
                throw new PaymentValidationException(
                        "metadata key exceeds " + MAX_METADATA_KEY_LENGTH + " characters: " + k);
            }
            if (v != null && v.length() > MAX_METADATA_VALUE_LENGTH) {
                throw new PaymentValidationException(
                        "metadata value exceeds " + MAX_METADATA_VALUE_LENGTH + " characters for key " + k);
            }
        });
    }
}
