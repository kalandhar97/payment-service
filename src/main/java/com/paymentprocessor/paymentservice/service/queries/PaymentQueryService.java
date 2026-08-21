package com.paymentprocessor.paymentservice.service.queries;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.dto.PaymentSearchCriteria;
import com.paymentprocessor.paymentservice.dto.TimelineEntry;
import com.paymentprocessor.paymentservice.entity.Outbox;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.exception.ResourceNotFoundException;
import com.paymentprocessor.paymentservice.repository.OutboxRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;

/** Read-only query operations for payment intents. */
@Service
public class PaymentQueryService {

    private final PaymentIntentRepository intentRepo;
    private final OutboxRepository outboxRepo;

    public PaymentQueryService(PaymentIntentRepository intentRepo, OutboxRepository outboxRepo) {
        this.intentRepo = intentRepo;
        this.outboxRepo = outboxRepo;
    }

    @Transactional(readOnly = true)
    public PaymentIntent get(String intentId) {
        return intentRepo.findById(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + intentId));
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(String intentId) {
        get(intentId);
        return outboxRepo.findByAggregateIdOrderByCreatedAtAsc(intentId).stream()
                .map(this::toTimelineEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> search(PaymentSearchCriteria criteria, Pageable pageable) {
        return intentRepo.findAll(buildSpec(criteria), pageable);
    }

    private TimelineEntry toTimelineEntry(Outbox outbox) {
        return new TimelineEntry(outbox.getEventType(), outbox.getCreatedAt(), outbox.getPayload());
    }

    private Specification<PaymentIntent> buildSpec(PaymentSearchCriteria c) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (c.merchantId() != null) {
                predicates.add(cb.equal(root.get("merchantId"), c.merchantId()));
            }
            if (c.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), c.customerId()));
            }
            if (c.currency() != null) {
                predicates.add(cb.equal(root.get("currency"), c.currency().toUpperCase()));
            }
            if (c.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), c.paymentMethod().toUpperCase()));
            }
            if (c.status() != null && !c.status().isEmpty()) {
                predicates.add(root.get("status").in(c.status()));
            }
            if (c.minAmountMinor() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amountMinor"), c.minAmountMinor()));
            }
            if (c.maxAmountMinor() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amountMinor"), c.maxAmountMinor()));
            }
            if (c.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdFrom()));
            }
            if (c.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), c.createdTo()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
