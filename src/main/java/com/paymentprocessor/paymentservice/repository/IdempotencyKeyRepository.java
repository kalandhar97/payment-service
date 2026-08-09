package com.paymentprocessor.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.IdempotencyKey;
import com.paymentprocessor.paymentservice.entity.IdempotencyKeyId;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {
}
