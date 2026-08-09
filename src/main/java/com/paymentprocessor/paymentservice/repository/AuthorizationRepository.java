package com.paymentprocessor.paymentservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.Authorization;

@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, String> {
    List<Authorization> findByIntentId(String intentId);
    Optional<Authorization> findFirstByIntentIdAndStatusOrderByCreatedAtDesc(String intentId, String status);
    List<Authorization> findByStatusAndExpiresAtBefore(String status, Instant cutoff);
}
