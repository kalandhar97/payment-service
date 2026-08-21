package com.paymentprocessor.paymentservice.service.commands;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.FraudClient;
import com.paymentprocessor.paymentservice.connector.FraudDecision;
import com.paymentprocessor.paymentservice.connector.LimitClient;
import com.paymentprocessor.paymentservice.connector.LimitDecisionResult;
import com.paymentprocessor.paymentservice.connector.PaymentConnector;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeCommand;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeResult;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentAttempt;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.entity.ThreeDsSession;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.PaymentAttemptRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.repository.ThreeDsSessionRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.AuthorizationFactory;
import com.paymentprocessor.paymentservice.support.LimitReservationService;
import com.paymentprocessor.paymentservice.support.PaymentAttemptFactory;

/**
 * Authorizes a CARD payment intent. Reserves limits, runs fraud checks, calls the card
 * connector and transitions the intent to AUTHORIZED, 3DS_PENDING or FAILED. Supports
 * automatic capture when the capture method is AUTOMATIC.
 */
@Component
public class CardAuthorizationHandler {

    private static final Logger log = LoggerFactory.getLogger(CardAuthorizationHandler.class);

    private final PaymentIntentRepository intentRepo;
    private final PaymentAttemptRepository attemptRepo;
    private final AuthorizationRepository authRepo;
    private final ThreeDsSessionRepository threeDsRepo;
    private final ConnectorRegistry connectors;
    private final FraudClient fraudClient;
    private final LimitClient limitClient;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAttemptFactory attemptFactory;
    private final AuthorizationFactory authorizationFactory;
    private final LimitReservationService limitReservationService;
    private final CaptureCommandHandler captureHandler;

    public CardAuthorizationHandler(PaymentIntentRepository intentRepo,
                                    PaymentAttemptRepository attemptRepo,
                                    AuthorizationRepository authRepo,
                                    ThreeDsSessionRepository threeDsRepo,
                                    ConnectorRegistry connectors,
                                    FraudClient fraudClient,
                                    LimitClient limitClient,
                                    PaymentStateMachine stateMachine,
                                    PaymentEventPublisher eventPublisher,
                                    PaymentAttemptFactory attemptFactory,
                                    AuthorizationFactory authorizationFactory,
                                    LimitReservationService limitReservationService,
                                    CaptureCommandHandler captureHandler) {
        this.intentRepo = intentRepo;
        this.attemptRepo = attemptRepo;
        this.authRepo = authRepo;
        this.threeDsRepo = threeDsRepo;
        this.connectors = connectors;
        this.fraudClient = fraudClient;
        this.limitClient = limitClient;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.attemptFactory = attemptFactory;
        this.authorizationFactory = authorizationFactory;
        this.limitReservationService = limitReservationService;
        this.captureHandler = captureHandler;
    }

    @Transactional
    public PaymentIntent authorize(PaymentIntent intent) {
        LimitDecisionResult limit = reserveLimit(intent);
        if (!limit.approved()) {
            recordLimitDecline(intent, limit);
            return intent;
        }
        intent.setLimitReservationId(limit.reservationId());
        intentRepo.save(intent);

        FraudDecision fraud = fraudClient.evaluate(intent.getId(), intent.getMerchantId(),
                intent.getAmountMinor(), intent.getCurrency(), intent.getInstrumentToken());

        PaymentAttempt attempt = attemptFactory.newAttempt(intent, connectors.card().connectorId());
        if (!fraud.approved()) {
            releaseLimitAndFail(intent, attempt, "FRAUD_BLOCK", "FRAUD_BLOCKED");
            return intent;
        }

        AuthorizeResult result = callConnector(intent, attempt, fraud);
        attemptRepo.save(attempt);

        return switch (result.outcome()) {
            case APPROVED -> handleApproved(intent, attempt, result);
            case PENDING -> handlePending(intent, result);
            default -> handleDecline(intent, attempt, result);
        };
    }

    private LimitDecisionResult reserveLimit(PaymentIntent intent) {
        return limitClient.reserve(intent.getId(), intent.getMerchantId(), intent.getCustomerId(),
                intent.getAmountMinor(), intent.getCurrency());
    }

    private void recordLimitDecline(PaymentIntent intent, LimitDecisionResult limit) {
        PaymentAttempt attempt = attemptFactory.newAttempt(intent, connectors.card().connectorId());
        attempt.setOutcome(AttemptOutcome.HARD_DECLINE.name());
        attempt.setMappedDeclineCode(limit.serviceUnavailable() ? "LIMIT_SERVICE_UNAVAILABLE" : "LIMIT_EXCEEDED");
        attemptRepo.save(attempt);
        fail(intent, limit.serviceUnavailable() ? "LIMIT_SERVICE_UNAVAILABLE" : "LIMIT_DECLINED",
                limit.serviceUnavailable() ? "LIMIT_SERVICE_UNAVAILABLE" : "LIMIT_EXCEEDED");
    }

    private AuthorizeResult callConnector(PaymentIntent intent, PaymentAttempt attempt, FraudDecision fraud) {
        long start = System.currentTimeMillis();
        PaymentConnector cardConnector = connectors.card();
        AuthorizeResult result = cardConnector.authorize(new AuthorizeCommand(
                intent.getId(), intent.getInstrumentToken(), intent.getAmountMinor(),
                intent.getCurrency(), intent.getStatementDescriptor(), fraud.requireThreeDs()));
        attempt.setLatencyMs(System.currentTimeMillis() - start);
        attempt.setOutcome(result.outcome().name());
        attempt.setNetworkDeclineCode(result.networkDeclineCode());
        attempt.setMappedDeclineCode(result.mappedDeclineCode());
        return result;
    }

    private PaymentIntent handleApproved(PaymentIntent intent, PaymentAttempt attempt, AuthorizeResult result) {
        Authorization auth = authorizationFactory.createFromCardResult(intent, attempt.getId(), result);
        authRepo.save(auth);

        intent.setAuthorizedMinor(intent.getAmountMinor());
        stateMachine.transition(intent, PaymentStatus.AUTHORIZED, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentAuthorizedEvent(intent));

        if (CaptureMethod.AUTOMATIC.name().equals(intent.getCaptureMethod())) {
            autoCaptureAndConfirm(intent, auth);
        }
        return intent;
    }

    private PaymentIntent handlePending(PaymentIntent intent, AuthorizeResult result) {
        ThreeDsSession session = new ThreeDsSession();
        session.setId(newId());
        session.setIntentId(intent.getId());
        session.setStatus("PENDING");
        session.setAcsUrl(result.acsUrl());
        session.setVersion("2.2.0");
        threeDsRepo.save(session);
        intent.setSubStatus("3DS_PENDING");
        intentRepo.save(intent);
        return intent;
    }

    private PaymentIntent handleDecline(PaymentIntent intent, PaymentAttempt attempt, AuthorizeResult result) {
        limitReservationService.release(intent, "AUTH_DECLINED");
        fail(intent, mapSubStatus(result), result.mappedDeclineCode());
        return intent;
    }

    private void releaseLimitAndFail(PaymentIntent intent, PaymentAttempt attempt,
                                    String declineCode, String subStatus) {
        limitReservationService.release(intent, declineCode);
        attempt.setOutcome(AttemptOutcome.HARD_DECLINE.name());
        attempt.setMappedDeclineCode(declineCode);
        attemptRepo.save(attempt);
        fail(intent, subStatus, declineCode);
    }

    private void autoCaptureAndConfirm(PaymentIntent intent, Authorization auth) {
        try {
            captureHandler.captureRemaining(intent, auth);
            stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
            intentRepo.save(intent);
            eventPublisher.publish(new PaymentEvent.PaymentConfirmedEvent(intent));
        } catch (Exception e) {
            log.warn("Automatic capture failed for intent {}, leaving in AUTHORIZED state: {}",
                    intent.getId(), e.getMessage());
            stateMachine.transition(intent, PaymentStatus.AUTHORIZED, "AUTO_CAPTURE_FAILED");
            intentRepo.save(intent);
        }
    }

    private void fail(PaymentIntent intent, String subStatus, String code) {
        stateMachine.transition(intent, PaymentStatus.FAILED, subStatus);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentFailedEvent(intent, code));
    }

    private String mapSubStatus(AuthorizeResult r) {
        return switch (r.outcome()) {
            case TIMEOUT -> "PROVIDER_TIMEOUT";
            case SOFT_DECLINE -> "SOFT_DECLINED";
            case HARD_DECLINE -> "HARD_DECLINED";
            default -> "AUTH_ERROR";
        };
    }

    private String newId() {
        return java.util.UUID.randomUUID().toString();
    }
}
