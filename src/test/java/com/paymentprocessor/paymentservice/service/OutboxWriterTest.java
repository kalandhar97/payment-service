package com.paymentprocessor.paymentservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paymentprocessor.paymentservice.entity.Outbox;
import com.paymentprocessor.paymentservice.repository.OutboxRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxRepository outboxRepo;

    private OutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OutboxWriter(outboxRepo, new ObjectMapper());
    }

    @Test
    void append_serializesPayloadAndSaves() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "AUTHORIZED");
        payload.put("amountMinor", 1000L);

        writer.append("pi_1", "PaymentAuthorized", payload);

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepo).save(captor.capture());
        Outbox saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo("pi_1");
        assertThat(saved.getEventType()).isEqualTo("PaymentAuthorized");
        assertThat(saved.getAggregateType()).isEqualTo("PaymentIntent");
        assertThat(saved.getPayload()).isEqualTo("{\"status\":\"AUTHORIZED\",\"amountMinor\":1000}");
    }

    @Test
    void append_unserializablePayload_throws() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bad", new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("boom");
            }
        });

        assertThatThrownBy(() -> writer.append("pi_1", "PaymentFailed", payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize outbox payload");
    }
}
