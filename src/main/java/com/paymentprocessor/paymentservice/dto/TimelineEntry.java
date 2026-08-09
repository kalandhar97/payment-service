package com.paymentprocessor.paymentservice.dto;

import java.time.Instant;

/** One immutable entry in a payment's audit timeline. */
public record TimelineEntry(String eventType, Instant occurredAt, String payload) {
}
