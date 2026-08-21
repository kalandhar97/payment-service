package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.model.CaptureCommand;
import com.paymentprocessor.paymentservice.connector.model.CaptureResult;
import com.paymentprocessor.paymentservice.dto.CaptureRequest;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.Capture;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.exception.ConnectorException;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.CaptureRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.AmountCalculator;
import com.paymentprocessor.paymentservice.support.IdGenerator;
import com.paymentprocessor.paymentservice.support.LimitReservationService;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;
import com.paymentprocessor.paymentservice.support.PaymentStatusChecker;

/**
 * Captures funds against an active card authorization. Supports partial captures
 * and final-capture semantics while updating the intent's running totals.
 */
@Component
public class CaptureCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final AuthorizationRepository authRepo;
    private final CaptureRepository captureRepo;
    private final ConnectorRegistry connectors;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final AmountCalculator amountCalculator;
    private final LimitReservationService limitReservationService;
    private final PaymentMethodResolver methodResolver;
    private final PaymentStatusChecker statusChecker;
    private final IdGenerator idGenerator;

    public CaptureCommandHandler(PaymentIntentRepository intentRepo,
                                AuthorizationRepository authRepo,
                                CaptureRepository captureRepo,
                                ConnectorRegistry connectors,
                                PaymentStateMachine stateMachine,
                                PaymentEventPublisher eventPublisher,
                                AmountCalculator amountCalculator,
                                LimitReservationService limitReservationService,
                                PaymentMethodResolver methodResolver,
                                PaymentStatusChecker statusChecker,
                                IdGenerator idGenerator) {
        this.intentRepo = intentRepo;
        this.authRepo = authRepo;
        this.captureRepo = captureRepo;
        this.connectors = connectors;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.amountCalculator = amountCalculator;
        this.limitReservationService = limitReservationService;
        this.methodResolver = methodResolver;
        this.statusChecker = statusChecker;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PaymentIntent capture(PaymentIntent intent, CaptureRequest req) {
        requireCard(intent, "capture");
        requireCapturableState(intent);

        Authorization auth = findActiveAuthorization(intent.getId());
        validateAuthorizationNotExpired(auth);

        long amount = amountCalculator.calculateCaptureAmount(intent, req);
        boolean isFinal = amountCalculator.isFinalCapture(intent, req, amount);

        doCapture(intent, auth, amount, isFinal);
        return intent;
    }

    @Transactional
    public void captureRemaining(PaymentIntent intent, Authorization auth) {
        long remaining = amountCalculator.remainingCaptureAmount(intent);
        if (remaining > 0) {
            doCapture(intent, auth, remaining, true);
        }
    }

    private void doCapture(PaymentIntent intent, Authorization auth, long amount, boolean isFinal) {
        CaptureResult cr = connectors.card().capture(new CaptureCommand(
                connectorRef(auth), amount, intent.getCurrency(), isFinal));
        if (!cr.success()) {
            throw new ConnectorException("Capture declined by connector: " + cr.declineCode());
        }

        Capture capture = buildCapture(intent, auth, amount, isFinal, cr);
        captureRepo.save(capture);

        intent.setCapturedMinor(nullToZero(intent.getCapturedMinor()) + amount);
        boolean full = intent.getCapturedMinor() >= nullToZero(intent.getAuthorizedMinor());
        if (full) {
            stateMachine.transition(intent, PaymentStatus.CAPTURED, null);
            intentRepo.save(intent);
            eventPublisher.publish(new PaymentEvent.PaymentCapturedEvent(intent));
            limitReservationService.commit(intent);
        } else {
            stateMachine.transition(intent, PaymentStatus.PARTIALLY_CAPTURED, null);
            intentRepo.save(intent);
            eventPublisher.publish(new PaymentEvent.PaymentPartiallyCapturedEvent(intent));
        }
    }

    private Capture buildCapture(PaymentIntent intent, Authorization auth, long amount,
                                boolean isFinal, CaptureResult result) {
        Capture capture = new Capture();
        capture.setId(idGenerator.newId());
        capture.setAuthorizationId(auth.getId());
        capture.setIntentId(intent.getId());
        capture.setAmountMinor(amount);
        capture.setCurrency(intent.getCurrency());
        capture.setStatus("CAPTURED");
        capture.setIsFinal(isFinal);
        capture.setNetworkRef(result.networkRef());
        return capture;
    }

    private Authorization findActiveAuthorization(String intentId) {
        return authRepo
                .findFirstByIntentIdAndStatusOrderByCreatedAtDesc(intentId, PaymentStatus.AUTHORIZED.name())
                .orElseThrow(() -> new PaymentValidationException("No active authorization to capture"));
    }

    private void validateAuthorizationNotExpired(Authorization auth) {
        if (auth.getExpiresAt() != null && auth.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new PaymentValidationException("Authorization has expired and cannot be captured");
        }
    }

    private void requireCapturableState(PaymentIntent intent) {
        if (!statusChecker.isInState(intent, PaymentStatus.AUTHORIZED, PaymentStatus.PARTIALLY_CAPTURED)) {
            throw new PaymentValidationException("Only AUTHORIZED payments can be captured");
        }
    }

    private void requireCard(PaymentIntent intent, String op) {
        if (methodResolver.resolveMethod(intent.getPaymentMethod()) != PaymentMethod.CARD) {
            throw new PaymentValidationException(op + " is only supported for CARD payments");
        }
    }

    private String connectorRef(Authorization auth) {
        return auth.getNetworkTxnId() != null ? auth.getNetworkTxnId() : auth.getId();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
