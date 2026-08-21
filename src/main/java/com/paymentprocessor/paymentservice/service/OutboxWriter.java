package com.paymentprocessor.paymentservice.service;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.entity.Outbox;
import com.paymentprocessor.paymentservice.repository.OutboxRepository;

/**
 * Appends domain events to the transactional outbox in the same DB transaction as
 * the state change that produced them, giving reliable at-least-once publication.
 */
@Component
public class OutboxWriter {

    private static final String AGGREGATE_TYPE = "PaymentIntent";

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void append(String intentId, String eventType, Map<String, Object> payload) {
        Outbox row = new Outbox();
        row.setAggregateId(intentId);
        row.setAggregateType(AGGREGATE_TYPE);
        row.setEventType(eventType);
        row.setPayload(toJson(intentId, eventType, payload));
        outbox.save(row);
    }

    private String toJson(String aggregateId, String eventType, Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for aggregate "
                    + aggregateId + ", event " + eventType, e);
        }
    }
}
