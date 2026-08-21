package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;

/**
 * Entry point for payment authorization. Validates the aggregate is in CREATED state,
 * then delegates to the rail-specific handler (card or UPI collect).
 */
@Component
public class AuthorizeCommandHandler {

    private final CardAuthorizationHandler cardHandler;
    private final UpiCollectHandler upiHandler;
    private final PaymentMethodResolver methodResolver;

    public AuthorizeCommandHandler(CardAuthorizationHandler cardHandler,
                                  UpiCollectHandler upiHandler,
                                  PaymentMethodResolver methodResolver) {
        this.cardHandler = cardHandler;
        this.upiHandler = upiHandler;
        this.methodResolver = methodResolver;
    }

    @Transactional
    public PaymentIntent authorize(PaymentIntent intent) {
        if (!PaymentStatus.CREATED.name().equals(intent.getStatus())) {
            throw new PaymentValidationException("Payment must be in CREATED state to authorize");
        }
        PaymentMethod method = methodResolver.resolveMethod(intent.getPaymentMethod());
        if (method == PaymentMethod.CARD) {
            return cardHandler.authorize(intent);
        }
        if (intent.getPayerVpa() == null || intent.getPayerVpa().isBlank()) {
            throw new PaymentValidationException(
                    "UPI intent flow: initiate via POST /v1/payments/{id}/upi-intent");
        }
        return upiHandler.collect(intent);
    }
}
