package com.paymentprocessor.paymentservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.service.PaymentOrchestrationService;

/**
 * Periodically transitions authorizations whose hold has elapsed to EXPIRED,
 * releasing funds and emitting {@code PaymentExpired}. Card holds typically expire
 * after days; UPI collect requests within minutes (both configurable).
 */
@Component
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);

    private final PaymentOrchestrationService orchestration;

    public ExpiryScheduler(PaymentOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @Scheduled(fixedDelayString = "${authorization.expiry.poll-ms:60000}")
    public void sweep() {
        try {
            int expired = orchestration.expireStaleAuthorizations();
            if (expired > 0) {
                log.info("Expired {} stale authorization(s)", expired);
            }
        } catch (Exception e) {
            log.error("Expiry sweep failed", e);
        }
    }
}
