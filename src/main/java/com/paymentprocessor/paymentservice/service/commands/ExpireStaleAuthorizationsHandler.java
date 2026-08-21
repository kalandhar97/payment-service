package com.paymentprocessor.paymentservice.service.commands;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.PaymentStatusChecker;

/**
 * Transitions expired authorizations to EXPIRED and emits domain events. Card holds
 * and pending UPI collect requests are processed in separate passes.
 */
@Component
public class ExpireStaleAuthorizationsHandler {

    private static final Logger log = LoggerFactory.getLogger(ExpireStaleAuthorizationsHandler.class);

    private final AuthorizationRepository authRepo;
    private final PaymentIntentRepository intentRepo;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentStatusChecker statusChecker;

    public ExpireStaleAuthorizationsHandler(AuthorizationRepository authRepo,
                                           PaymentIntentRepository intentRepo,
                                           PaymentStateMachine stateMachine,
                                           PaymentEventPublisher eventPublisher,
                                           PaymentStatusChecker statusChecker) {
        this.authRepo = authRepo;
        this.intentRepo = intentRepo;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.statusChecker = statusChecker;
    }

    @Transactional
    public int expire() {
        Instant now = Instant.now();
        int expired = expireAuthorizations(authRepo.findByStatusAndExpiresAtBefore(PaymentStatus.AUTHORIZED.name(), now), now, false);
        expired += expireAuthorizations(authRepo.findByStatusAndExpiresAtBefore("PENDING", now), now, true);
        return expired;
    }

    private int expireAuthorizations(List<Authorization> authorizations, Instant now, boolean upiPending) {
        int expired = 0;
        for (Authorization auth : authorizations) {
            PaymentIntent intent = intentRepo.findById(auth.getIntentId()).orElse(null);
            if (intent == null) {
                continue;
            }
            try {
                if (upiPending) {
                    expireUpiPending(auth, intent);
                } else {
                    expireCardAuthorization(auth, intent);
                }
                expired++;
            } catch (Exception e) {
                log.warn("Failed to expire authorization {} for intent {}: {}",
                        auth.getId(), intent.getId(), e.getMessage());
            }
        }
        return expired;
    }

    private void expireCardAuthorization(Authorization auth, PaymentIntent intent) {
        if (statusChecker.isInState(intent, PaymentStatus.AUTHORIZED, PaymentStatus.PARTIALLY_CAPTURED)) {
            markExpired(auth, intent, null);
        }
    }

    private void expireUpiPending(Authorization auth, PaymentIntent intent) {
        if (statusChecker.isInState(intent, PaymentStatus.CREATED)) {
            markExpired(auth, intent, "UPI_TIMEOUT");
        }
    }

    private void markExpired(Authorization auth, PaymentIntent intent, String reason) {
        auth.setStatus("EXPIRED");
        authRepo.save(auth);
        stateMachine.transition(intent, PaymentStatus.EXPIRED, reason);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentExpiredEvent(intent, reason));
    }
}
