package com.paymentprocessor.paymentservice.connector.model;

/** Outcome of a capture request. */
public record CaptureResult(boolean success, String networkRef, String declineCode) {
}
