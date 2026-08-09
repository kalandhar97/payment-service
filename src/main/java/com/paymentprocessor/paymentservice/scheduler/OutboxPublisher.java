package com.paymentprocessor.paymentservice.scheduler;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.paymentprocessor.paymentservice.config.ConnectorProperties;
import com.paymentprocessor.paymentservice.entity.Outbox;
import com.paymentprocessor.paymentservice.repository.OutboxRepository;

/**
 * Relays unpublished {@link Outbox} rows to the event bus / sink, then stamps them
 * published. Runs on a fixed delay and processes a bounded batch so the payment
 * service degrades gracefully under backlog. If no sink URL is configured events
 * are logged (useful in local/dev), preserving at-least-once semantics either way.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepo;
    private final ConnectorProperties.Outbox props;
    private final RestClient sink;

    public OutboxPublisher(OutboxRepository outboxRepo, ConnectorProperties properties) {
        this.outboxRepo = outboxRepo;
        this.props = properties.getOutbox();
        this.sink = (props.getSinkUrl() != null && !props.getSinkUrl().isBlank())
                ? RestClient.create(props.getSinkUrl()) : null;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-ms:5000}")
    @Transactional
    public void publishPending() {
        List<Outbox> batch = outboxRepo.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, props.getBatchSize()));
        if (batch.isEmpty()) {
            return;
        }
        for (Outbox event : batch) {
            try {
                deliver(event);
                event.setPublishedAt(Instant.now());
                outboxRepo.save(event);
            } catch (Exception e) {
                // Leave publishedAt null so it is retried on the next cycle.
                log.warn("Failed to publish outbox event {} ({}), will retry: {}",
                        event.getId(), event.getEventType(), e.getMessage());
            }
        }
        log.debug("Published {} outbox events", batch.size());
    }

    private void deliver(Outbox event) {
        if (sink == null) {
            log.info("EVENT {} aggregate={} payload={}",
                    event.getEventType(), event.getAggregateId(), event.getPayload());
            return;
        }
        sink.post()
                .header("X-Event-Type", event.getEventType())
                .header("X-Aggregate-Id", event.getAggregateId())
                .header("Content-Type", "application/json")
                .body(event.getPayload() == null ? "{}" : event.getPayload())
                .retrieve()
                .toBodilessEntity();
    }
}
