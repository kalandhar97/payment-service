package com.paymentprocessor.paymentservice.enums;

/**
 * Supported payment rails. Drives connector routing in {@code ConnectorRegistry}.
 */
public enum PaymentMethod {
    CARD,
    UPI
}
