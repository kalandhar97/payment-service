package com.paymentprocessor.paymentservice.service.commands;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.dto.UpiCallbackRequest;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.Capture;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.CaptureRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.AuthorizationFactory;
import com.paymentprocessor.paymentservice.support.IdGenerator;

/**
 * Processes asynchronous UPI debit callbacks. A successful callback collapses the
 * authorize/capture/confirm lifecycle into a single debit.
 */
@Component
public class UpiCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(UpiCallbackHandler.class);

    private final PaymentIntentRepository intentRepo;
    private final AuthorizationRepository authRepo;
    private final CaptureRepository captureRepo;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final AuthorizationFactory authorizationFactory;
    private final IdGenerator idGenerator;

    public UpiCallbackHandler(PaymentIntentRepository intentRepo,
                             AuthorizationRepository authRepo,
                             CaptureRepository captureRepo,
                             PaymentStateMachine stateMachine,
                             PaymentEventPublisher eventPublisher,
                             AuthorizationFactory authorizationFactory,
                             IdGenerator idGenerator) {
        this.intentRepo = intentRepo;
        this.authRepo = authRepo;
        this.captureRepo = captureRepo;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.authorizationFactory = authorizationFactory;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PaymentIntent handle(UpiCallbackRequest req) {
        PaymentIntent intent = intentRepo.findById(req.intentId())
                .orElseThrow(() -> new com.paymentprocessor.paymentservice.exception.ResourceNotFoundException(
                        "Payment not found: " + req.intentId()));
        boolean success = "SUCCESS".equalsIgnoreCase(req.status());
        if (!PaymentStatus.CREATED.name().equals(intent.getStatus())) {
            log.info("Ignoring UPI callback for intent {} in state {}", intent.getId(), intent.getStatus());
            return intent;
        }
        if (!success) {
            fail(intent, "UPI_DEBIT_FAILED", req.declineCode());
            return intent;
        }

        Authorization auth = resolveUpiAuthorization(intent, req.npciTxnId());
        authRepo.save(auth);

        intent.setAuthorizedMinor(intent.getAmountMinor());
        stateMachine.transition(intent, PaymentStatus.AUTHORIZED, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentAuthorizedEvent(intent));

        Capture capture = buildCapture(intent, auth, req.npciTxnId());
        captureRepo.save(capture);

        intent.setCapturedMinor(intent.getAmountMinor());
        stateMachine.transition(intent, PaymentStatus.CAPTURED, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentCapturedEvent(intent));

        stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentConfirmedEvent(intent));
        return intent;
    }

    private Authorization resolveUpiAuthorization(PaymentIntent intent, String npciTxnId) {
        Optional<Authorization> pending = authRepo.findByIntentId(intent.getId()).stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .findFirst();
        return authorizationFactory.createAuthorizedForUpi(intent, pending.orElse(null), npciTxnId);
    }

    private Capture buildCapture(PaymentIntent intent, Authorization auth, String npciTxnId) {
        Capture capture = new Capture();
        capture.setId(idGenerator.newId());
        capture.setAuthorizationId(auth.getId());
        capture.setIntentId(intent.getId());
        capture.setAmountMinor(intent.getAmountMinor());
        capture.setCurrency(intent.getCurrency());
        capture.setStatus("CAPTURED");
        capture.setIsFinal(true);
        capture.setNetworkRef(npciTxnId);
        return capture;
    }

    private void fail(PaymentIntent intent, String subStatus, String code) {
        stateMachine.transition(intent, PaymentStatus.FAILED, subStatus);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentFailedEvent(intent, code));
    }
}
