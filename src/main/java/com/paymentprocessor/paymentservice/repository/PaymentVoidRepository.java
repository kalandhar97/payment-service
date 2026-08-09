package com.paymentprocessor.paymentservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.PaymentVoid;

@Repository
public interface PaymentVoidRepository extends JpaRepository<PaymentVoid, String> {
    List<PaymentVoid> findByAuthorizationId(String authorizationId);
}
