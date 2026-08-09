package com.paymentprocessor.paymentservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.ThreeDsSession;

@Repository
public interface ThreeDsSessionRepository extends JpaRepository<ThreeDsSession, String> {
    List<ThreeDsSession> findByIntentId(String intentId);
}
