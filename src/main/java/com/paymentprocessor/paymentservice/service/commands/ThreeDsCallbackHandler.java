package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.dto.ThreeDsCallbackRequest;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.entity.ThreeDsSession;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.exception.ResourceNotFoundException;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.repository.ThreeDsSessionRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.AuthorizationFactory;
import com.paymentprocessor.paymentservice.support.IdGenerator;

/**
 * Completes a 3-D Secure authentication and, if successful, creates the authorization
 * for the card payment. Automatic capture is honored when configured.
 */
@Component
public class ThreeDsCallbackHandler {

    private final PaymentIntentRepository intentRepo;
    private final AuthorizationRepository authRepo;
    private final ThreeDsSessionRepository threeDsRepo;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final AuthorizationFactory authorizationFactory;
    private final CaptureCommandHandler captureHandler;

    public ThreeDsCallbackHandler(PaymentIntentRepository intentRepo,
                                 AuthorizationRepository authRepo,
                                 ThreeDsSessionRepository threeDsRepo,
                                 PaymentStateMachine stateMachine,
                                 PaymentEventPublisher eventPublisher,
                                 AuthorizationFactory authorizationFactory,
                                 CaptureCommandHandler captureHandler) {
        this.intentRepo = intentRepo;
        this.authRepo = authRepo;
        this.threeDsRepo = threeDsRepo;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.authorizationFactory = authorizationFactory;
        this.captureHandler = captureHandler;
    }

    @Transactional
    public PaymentIntent handle(ThreeDsCallbackRequest req) {
        PaymentIntent intent = intentRepo.findById(req.intentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + req.intentId()));
        if (!PaymentStatus.CREATED.name().equals(intent.getStatus())
                || !"3DS_PENDING".equals(intent.getSubStatus())) {
            throw new PaymentValidationException("No pending 3-D Secure challenge for this payment");
        }
        ThreeDsSession session = threeDsRepo.findByIntentId(intent.getId()).stream()
                .reduce((a, b) -> b)
                .orElseThrow(() -> new ResourceNotFoundException("3-D Secure session not found"));

        boolean authenticated = "AUTHENTICATED".equalsIgnoreCase(req.status())
                || "SUCCESS".equalsIgnoreCase(req.status());
        session.setStatus(authenticated ? "AUTHENTICATED" : "FAILED");
        session.setLiabilityShift(req.liabilityShift());
        threeDsRepo.save(session);

        if (!authenticated) {
            fail(intent, "3DS_FAILED", "3DS_FAILED");
            return intent;
        }

        Authorization auth = buildAuthorization(intent, session, req);
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

    private Authorization buildAuthorization(PaymentIntent intent, ThreeDsSession session,
                                              ThreeDsCallbackRequest req) {
        Authorization auth = new Authorization();
        auth.setId(newId());
        auth.setIntentId(intent.getId());
        auth.setAmountMinor(intent.getAmountMinor());
        auth.setCurrency(intent.getCurrency());
        auth.setStatus(PaymentStatus.AUTHORIZED.name());
        auth.setEci(req.eci());
        auth.setThreeDsSessionId(session.getId());
        auth.setExpiresAt(java.time.Instant.now().plus(authorizationFactory.cardExpirySeconds(),
                java.time.temporal.ChronoUnit.SECONDS));
        return auth;
    }

    private void autoCaptureAndConfirm(PaymentIntent intent, Authorization auth) {
        try {
            captureHandler.captureRemaining(intent, auth);
            stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
            intentRepo.save(intent);
            eventPublisher.publish(new PaymentEvent.PaymentConfirmedEvent(intent));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ThreeDsCallbackHandler.class)
                    .warn("Automatic capture failed for intent {}, leaving in AUTHORIZED state: {}",
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

    private String newId() {
        return java.util.UUID.randomUUID().toString();
    }
}
