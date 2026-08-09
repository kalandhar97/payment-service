package com.paymentprocessor.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.IntentTransition;
import com.paymentprocessor.paymentservice.entity.IntentTransitionId;

@Repository
public interface IntentTransitionRepository extends JpaRepository<IntentTransition, IntentTransitionId> {
    boolean existsByFromStatusAndToStatus(String fromStatus, String toStatus);
}
