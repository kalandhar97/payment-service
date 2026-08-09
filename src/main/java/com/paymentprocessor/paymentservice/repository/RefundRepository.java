package com.paymentprocessor.paymentservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.Refund;

@Repository
public interface RefundRepository extends JpaRepository<Refund, String> {
    List<Refund> findByIntentId(String intentId);
    List<Refund> findByCaptureId(String captureId);
}
