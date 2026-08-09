package com.paymentprocessor.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;

@Repository
public interface PaymentIntentRepository
        extends JpaRepository<PaymentIntent, String>, JpaSpecificationExecutor<PaymentIntent> {
}
