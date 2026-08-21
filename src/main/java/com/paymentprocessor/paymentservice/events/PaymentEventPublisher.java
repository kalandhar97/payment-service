package com.paymentprocessor.paymentservice.events;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.service.OutboxWriter;

/**
 * Publishes domain events to the transactional outbox. This is the only component
 * that should know how payment events map onto the outbox table, keeping command
 * handlers free of event wiring details.
 */
@Component
public class PaymentEventPublisher {

    private final OutboxWriter outbox;

    public PaymentEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void publish(PaymentEvent event) {
        outbox.append(event.aggregateId(), event.eventType(), event.payload());
    }
}
