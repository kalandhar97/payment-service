package com.paymentprocessor.paymentservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.Capture;

@Repository
public interface CaptureRepository extends JpaRepository<Capture, String> {
    List<Capture> findByIntentId(String intentId);
    List<Capture> findByAuthorizationId(String authorizationId);
}
