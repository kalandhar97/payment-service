package com.paymentprocessor.paymentservice.support;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.UpiFlow;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;

/** Resolves payment-method related enumerations and validates method-specific input. */
@Component
public class PaymentMethodResolver {

    public PaymentMethod resolveMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new PaymentValidationException("Unsupported paymentMethod: " + value);
        }
    }

    public CaptureMethod resolveCaptureMethod(CreatePaymentRequest req, PaymentMethod method) {
        if (method == PaymentMethod.UPI) {
            return CaptureMethod.AUTOMATIC;
        }
        if (req.captureMethod() == null || req.captureMethod().isBlank()) {
            return CaptureMethod.AUTOMATIC;
        }
        try {
            return CaptureMethod.valueOf(req.captureMethod().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException("Unknown captureMethod: " + req.captureMethod());
        }
    }

    public UpiFlow resolveUpiFlow(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return UpiFlow.COLLECT;
        }
        try {
            return UpiFlow.valueOf(rawValue.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException("Unknown upiFlow: " + rawValue);
        }
    }

    public boolean isCard(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    public boolean isUpi(PaymentMethod method) {
        return method == PaymentMethod.UPI;
    }
}
