package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectResult;
import com.paymentprocessor.paymentservice.entity.PaymentAttempt;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.PaymentAttemptRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.support.AuthorizationFactory;
import com.paymentprocessor.paymentservice.support.PaymentAttemptFactory;

/**
 * Initiates a UPI COLLECT request for a CREATED UPI intent. The result is normally
 * PENDING until the payer approves and the PSP callback arrives.
 */
@Component
public class UpiCollectHandler {

    private final PaymentIntentRepository intentRepo;
    private final PaymentAttemptRepository attemptRepo;
    private final AuthorizationRepository authRepo;
    private final ConnectorRegistry connectors;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAttemptFactory attemptFactory;
    private final AuthorizationFactory authorizationFactory;

    public UpiCollectHandler(PaymentIntentRepository intentRepo,
                           PaymentAttemptRepository attemptRepo,
                           AuthorizationRepository authRepo,
                           ConnectorRegistry connectors,
                           PaymentEventPublisher eventPublisher,
                           PaymentAttemptFactory attemptFactory,
                           AuthorizationFactory authorizationFactory) {
        this.intentRepo = intentRepo;
        this.attemptRepo = attemptRepo;
        this.authRepo = authRepo;
        this.connectors = connectors;
        this.eventPublisher = eventPublisher;
        this.attemptFactory = attemptFactory;
        this.authorizationFactory = authorizationFactory;
    }

    @Transactional
    public PaymentIntent collect(PaymentIntent intent) {
        PaymentAttempt attempt = attemptFactory.newAttempt(intent, connectors.upi().connectorId());
        UpiCollectResult result = connectors.upi().collect(new UpiCollectCommand(
                intent.getId(), intent.getPayerVpa(), intent.getAmountMinor(),
                intent.getCurrency(), intent.getDescription(), upiExpirySeconds()));
        attempt.setOutcome(result.outcome().name());
        attempt.setMappedDeclineCode(result.declineCode());
        attemptRepo.save(attempt);

        if (result.outcome() == AttemptOutcome.PENDING) {
            intent.setSubStatus("UPI_COLLECT_PENDING");
            intent.setConnectorId(connectors.upi().connectorId());
            intentRepo.save(intent);
            authRepo.save(authorizationFactory.createPendingForUpi(intent, result.providerRef()));
        } else {
            fail(intent, "UPI_COLLECT_DECLINED", result.declineCode());
        }
        return intent;
    }

    private int upiExpirySeconds() {
        return (int) authorizationFactory.upiExpirySeconds();
    }

    private void fail(PaymentIntent intent, String subStatus, String code) {
        intent.setStatus(com.paymentprocessor.paymentservice.enums.PaymentStatus.FAILED.name());
        intent.setSubStatus(subStatus);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentFailedEvent(intent, code));
    }
}
