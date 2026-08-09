package com.paymentprocessor.paymentservice.connector.model;

/** Outcome of a refund request. */
public record RefundResult(boolean success, String networkRef, String declineCode) {
}
