package com.paymentprocessor.paymentservice.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.Outbox;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
    List<Outbox> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);
}
