package com.paymentprocessor.paymentservice.connector;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.enums.PaymentMethod;

/**
 * Routes a payment to the correct connector by {@link PaymentMethod}. Today there
 * is one connector per rail; extend this to select among multiple acquirers/PSPs
 * based on merchant configuration, cost, or health without touching orchestration.
 */
@Component
public class ConnectorRegistry {

    private final PaymentConnector cardConnector;
    private final UpiConnector upiConnector;

    public ConnectorRegistry(PaymentConnector cardConnector, UpiConnector upiConnector) {
        this.cardConnector = cardConnector;
        this.upiConnector = upiConnector;
    }

    public PaymentConnector card() {
        return cardConnector;
    }

    public UpiConnector upi() {
        return upiConnector;
    }

    /** Convenience for card/void/refund routing where a card connector is expected. */
    public PaymentConnector requireCard(PaymentMethod method) {
        if (method != PaymentMethod.CARD) {
            throw new IllegalArgumentException("Operation only supported for CARD payments, got " + method);
        }
        return cardConnector;
    }
}
