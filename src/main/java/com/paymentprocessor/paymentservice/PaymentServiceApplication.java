package com.paymentprocessor.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service - core orchestration service of the payment processor.
 *
 * <p>Coordinates the full payment lifecycle (create, authorize, capture, void,
 * refund, confirm, expire, retry) across card and UPI rails while owning the
 * payment intent state machine, idempotency guarantees and the transactional
 * outbox used to publish domain events.</p>
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
