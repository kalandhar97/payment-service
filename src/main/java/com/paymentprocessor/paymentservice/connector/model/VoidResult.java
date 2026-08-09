package com.paymentprocessor.paymentservice.connector.model;

/** Outcome of a void request. */
public record VoidResult(boolean success, String networkRef) {
}
