package com.paymentprocessor.paymentservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.FraudClient;
import com.paymentprocessor.paymentservice.connector.FraudDecision;
import com.paymentprocessor.paymentservice.connector.LimitClient;
import com.paymentprocessor.paymentservice.connector.LimitDecisionResult;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeCommand;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeResult;
import com.paymentprocessor.paymentservice.connector.model.CaptureCommand;
import com.paymentprocessor.paymentservice.connector.model.CaptureResult;
import com.paymentprocessor.paymentservice.connector.model.RefundCommand;
import com.paymentprocessor.paymentservice.connector.model.RefundResult;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectResult;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentResult;
import com.paymentprocessor.paymentservice.connector.model.VoidCommand;
import com.paymentprocessor.paymentservice.connector.model.VoidResult;
import com.paymentprocessor.paymentservice.dto.CaptureRequest;
import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.dto.PaymentSearchCriteria;
import com.paymentprocessor.paymentservice.dto.RefundRequest;
import com.paymentprocessor.paymentservice.dto.ThreeDsCallbackRequest;
import com.paymentprocessor.paymentservice.dto.TimelineEntry;
import com.paymentprocessor.paymentservice.dto.UpiCallbackRequest;
import com.paymentprocessor.paymentservice.dto.UpiIntentResponse;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.Capture;
import com.paymentprocessor.paymentservice.entity.PaymentAttempt;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.entity.PaymentVoid;
import com.paymentprocessor.paymentservice.entity.Refund;
import com.paymentprocessor.paymentservice.entity.ThreeDsSession;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.exception.ConnectorException;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.exception.ResourceNotFoundException;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.CaptureRepository;
import com.paymentprocessor.paymentservice.repository.OutboxRepository;
import com.paymentprocessor.paymentservice.repository.PaymentAttemptRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.repository.PaymentVoidRepository;
import com.paymentprocessor.paymentservice.repository.RefundRepository;
import com.paymentprocessor.paymentservice.repository.ThreeDsSessionRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;

/**
 * Central workflow engine. Owns the payment intent state machine and coordinates
 * fraud, vault, card and UPI connectors while writing every state change and its
 * domain event atomically (via the transactional outbox). All mutating methods
 * run in a single database transaction so an intent's running totals and status
 * can never drift out of sync with its ledger of attempts/captures/refunds.
 */
@Service
public class PaymentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestrationService.class);

    private static final String EVT_CREATED = "PaymentCreated";
    private static final String EVT_AUTHORIZED = "PaymentAuthorized";
    private static final String EVT_CAPTURED = "PaymentCaptured";
    private static final String EVT_PARTIAL_CAPTURE = "PaymentPartiallyCaptured";
    private static final String EVT_FAILED = "PaymentFailed";
    private static final String EVT_CANCELLED = "PaymentCancelled";
    private static final String EVT_CONFIRMED = "PaymentConfirmed";
    private static final String EVT_REFUNDED = "PaymentRefunded";
    private static final String EVT_EXPIRED = "PaymentExpired";

    private final PaymentIntentRepository intentRepo;
    private final PaymentAttemptRepository attemptRepo;
    private final AuthorizationRepository authRepo;
    private final CaptureRepository captureRepo;
    private final RefundRepository refundRepo;
    private final PaymentVoidRepository voidRepo;
    private final ThreeDsSessionRepository threeDsRepo;
    private final OutboxRepository outboxRepo;

    private final ConnectorRegistry connectors;
    private final FraudClient fraudClient;
    private final LimitClient limitClient;
    private final PaymentStateMachine stateMachine;
    private final OutboxWriter outbox;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    @Value("${authorization.expiry.card-seconds:604800}")
    private long cardExpirySeconds;

    @Value("${authorization.expiry.upi-seconds:300}")
    private long upiExpirySeconds;

    public PaymentOrchestrationService(PaymentIntentRepository intentRepo,
                                       PaymentAttemptRepository attemptRepo,
                                       AuthorizationRepository authRepo,
                                       CaptureRepository captureRepo,
                                       RefundRepository refundRepo,
                                       PaymentVoidRepository voidRepo,
                                       ThreeDsSessionRepository threeDsRepo,
                                       OutboxRepository outboxRepo,
                                       ConnectorRegistry connectors,
                                       FraudClient fraudClient,
                                       LimitClient limitClient,
                                       PaymentStateMachine stateMachine,
                                       OutboxWriter outbox,
                                       IdempotencyService idempotency,
                                       ObjectMapper objectMapper) {
        this.intentRepo = intentRepo;
        this.attemptRepo = attemptRepo;
        this.authRepo = authRepo;
        this.captureRepo = captureRepo;
        this.refundRepo = refundRepo;
        this.voidRepo = voidRepo;
        this.threeDsRepo = threeDsRepo;
        this.outboxRepo = outboxRepo;
        this.connectors = connectors;
        this.fraudClient = fraudClient;
        this.limitClient = limitClient;
        this.stateMachine = stateMachine;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public PaymentIntent createPayment(CreatePaymentRequest req, String idempotencyKey) {
        PaymentMethod method = parseMethod(req.paymentMethod());
        validateCreate(req, method);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> prior = idempotency.begin(req.merchantId(), idempotencyKey, canonical(req));
            if (prior.isPresent()) {
                return intentRepo.findById(prior.get())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idempotent replay references missing intent " + prior.get()));
            }
        }

        PaymentIntent intent = new PaymentIntent();
        intent.setId(newId());
        intent.setMerchantId(req.merchantId());
        intent.setCustomerId(req.customerId());
        intent.setPaymentMethod(method.name());
        intent.setInstrumentToken(req.instrumentToken());
        intent.setPayerVpa(req.payerVpa());
        intent.setAmountMinor(req.amountMinor());
        intent.setCurrency(req.currency().toUpperCase());
        intent.setStatus(PaymentStatus.CREATED.name());
        intent.setCaptureMethod(resolveCaptureMethod(req, method).name());
        intent.setConnectorId(method == PaymentMethod.CARD
                ? connectors.card().connectorId() : connectors.upi().connectorId());
        intent.setStatementDescriptor(req.statementDescriptor());
        intent.setDescription(req.description());
        intent.setMetadata(writeMetadata(req.metadata()));
        intent.setAuthorizedMinor(0L);
        intent.setCapturedMinor(0L);
        intent.setRefundedMinor(0L);
        intentRepo.save(intent);

        outbox.append(intent.getId(), EVT_CREATED, basePayload(intent));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.complete(req.merchantId(), idempotencyKey, 201, intent.getId());
        }
        return intent;
    }

    // --------------------------------------------------------------- authorize

    @Transactional
    public PaymentIntent authorize(String intentId) {
        PaymentIntent intent = load(intentId);
        if (!PaymentStatus.CREATED.name().equals(intent.getStatus())) {
            throw new PaymentValidationException("Payment must be in CREATED state to authorize");
        }
        PaymentMethod method = parseMethod(intent.getPaymentMethod());
        if (method == PaymentMethod.CARD) {
            return authorizeCard(intent);
        }
        if (intent.getPayerVpa() == null || intent.getPayerVpa().isBlank()) {
            throw new PaymentValidationException(
                    "UPI intent flow: initiate via POST /v1/payments/{id}/upi-intent");
        }
        return upiCollect(intent);
    }

    private PaymentIntent authorizeCard(PaymentIntent intent) {
        LimitDecisionResult limit = limitClient.reserve(intent.getId(), intent.getMerchantId(),
                intent.getCustomerId(), intent.getAmountMinor(), intent.getCurrency());
        if (!limit.approved()) {
            PaymentAttempt limitAttempt = newAttempt(intent, connectors.card().connectorId());
            limitAttempt.setOutcome(AttemptOutcome.HARD_DECLINE.name());
            limitAttempt.setMappedDeclineCode(limit.serviceUnavailable() ? "LIMIT_SERVICE_UNAVAILABLE" : "LIMIT_EXCEEDED");
            attemptRepo.save(limitAttempt);
            fail(intent, limit.serviceUnavailable() ? "LIMIT_SERVICE_UNAVAILABLE" : "LIMIT_DECLINED",
                    limit.serviceUnavailable() ? "LIMIT_SERVICE_UNAVAILABLE" : "LIMIT_EXCEEDED");
            return intent;
        }
        intent.setLimitReservationId(limit.reservationId());
        intentRepo.save(intent);

        FraudDecision fraud = fraudClient.evaluate(intent.getId(), intent.getMerchantId(),
                intent.getAmountMinor(), intent.getCurrency(), intent.getInstrumentToken());

        PaymentAttempt attempt = newAttempt(intent, connectors.card().connectorId());
        if (!fraud.approved()) {
            releaseLimitReservation(intent, "FRAUD_BLOCK");
            attempt.setOutcome(AttemptOutcome.HARD_DECLINE.name());
            attempt.setMappedDeclineCode("FRAUD_BLOCK");
            attemptRepo.save(attempt);
            fail(intent, "FRAUD_BLOCKED", "FRAUD_BLOCK");
            return intent;
        }

        long start = System.currentTimeMillis();
        AuthorizeResult r = connectors.card().authorize(new AuthorizeCommand(
                intent.getId(), intent.getInstrumentToken(), intent.getAmountMinor(),
                intent.getCurrency(), intent.getStatementDescriptor(), fraud.requireThreeDs()));
        attempt.setLatencyMs(System.currentTimeMillis() - start);
        attempt.setOutcome(r.outcome().name());
        attempt.setNetworkDeclineCode(r.networkDeclineCode());
        attempt.setMappedDeclineCode(r.mappedDeclineCode());
        attemptRepo.save(attempt);

        switch (r.outcome()) {
            case APPROVED -> {
                Authorization auth = persistAuthorization(intent, attempt.getId(), r, cardExpirySeconds);
                intent.setAuthorizedMinor(intent.getAmountMinor());
                stateMachine.transition(intent, PaymentStatus.AUTHORIZED, null);
                intentRepo.save(intent);
                outbox.append(intent.getId(), EVT_AUTHORIZED, basePayload(intent));
                if (CaptureMethod.AUTOMATIC.name().equals(intent.getCaptureMethod())) {
                    autoCaptureAndConfirm(intent, auth);
                }
            }
            case PENDING -> {
                ThreeDsSession session = new ThreeDsSession();
                session.setId(newId());
                session.setIntentId(intent.getId());
                session.setStatus("PENDING");
                session.setAcsUrl(r.acsUrl());
                session.setVersion("2.2.0");
                threeDsRepo.save(session);
                intent.setSubStatus("3DS_PENDING");
                intentRepo.save(intent);
            }
            default -> {
                releaseLimitReservation(intent, "AUTH_DECLINED");
                fail(intent, mapSubStatus(r), r.mappedDeclineCode());
            }
        }
        return intent;
    }

    // ----------------------------------------------------------------- capture

    @Transactional
    public PaymentIntent capture(String intentId, CaptureRequest req) {
        PaymentIntent intent = load(intentId);
        requireCard(intent, "capture");
        if (!isOneOf(intent.getStatus(), PaymentStatus.AUTHORIZED, PaymentStatus.PARTIALLY_CAPTURED)) {
            throw new PaymentValidationException("Only AUTHORIZED payments can be captured");
        }
        Authorization auth = authRepo
                .findFirstByIntentIdAndStatusOrderByCreatedAtDesc(intentId, PaymentStatus.AUTHORIZED.name())
                .orElseThrow(() -> new PaymentValidationException("No active authorization to capture"));
        if (auth.getExpiresAt() != null && auth.getExpiresAt().isBefore(Instant.now())) {
            throw new PaymentValidationException("Authorization has expired and cannot be captured");
        }
        long remaining = intent.getAuthorizedMinor() - intent.getCapturedMinor();
        long amount = req != null && req.amountMinor() != null ? req.amountMinor() : remaining;
        if (amount <= 0 || amount > remaining) {
            throw new PaymentValidationException(
                    "Capture amount " + amount + " must be > 0 and <= remaining " + remaining);
        }
        boolean isFinal = (req != null && Boolean.TRUE.equals(req.finalCapture())) || amount == remaining;
        doCapture(intent, auth, amount, isFinal);
        return intent;
    }

    private void autoCaptureAndConfirm(PaymentIntent intent, Authorization auth) {
        long remaining = intent.getAuthorizedMinor() - intent.getCapturedMinor();
        doCapture(intent, auth, remaining, true);
        stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_CONFIRMED, basePayload(intent));
    }

    private void doCapture(PaymentIntent intent, Authorization auth, long amount, boolean isFinal) {
        CaptureResult cr = connectors.card().capture(new CaptureCommand(
                connectorRef(auth), amount, intent.getCurrency(), isFinal));
        if (!cr.success()) {
            throw new ConnectorException("Capture declined by connector: " + cr.declineCode());
        }
        Capture capture = new Capture();
        capture.setId(newId());
        capture.setAuthorizationId(auth.getId());
        capture.setIntentId(intent.getId());
        capture.setAmountMinor(amount);
        capture.setCurrency(intent.getCurrency());
        capture.setStatus("CAPTURED");
        capture.setIsFinal(isFinal);
        capture.setNetworkRef(cr.networkRef());
        captureRepo.save(capture);

        intent.setCapturedMinor(intent.getCapturedMinor() + amount);
        boolean full = intent.getCapturedMinor() >= intent.getAuthorizedMinor();
        if (full) {
            stateMachine.transition(intent, PaymentStatus.CAPTURED, null);
            intentRepo.save(intent);
            outbox.append(intent.getId(), EVT_CAPTURED, basePayload(intent));
            commitLimitReservation(intent);
        } else {
            stateMachine.transition(intent, PaymentStatus.PARTIALLY_CAPTURED, null);
            intentRepo.save(intent);
            outbox.append(intent.getId(), EVT_PARTIAL_CAPTURE, basePayload(intent));
        }
    }

    // -------------------------------------------------------------------- void

    @Transactional
    public PaymentIntent voidAuthorization(String intentId) {
        PaymentIntent intent = load(intentId);
        requireCard(intent, "void");
        if (!PaymentStatus.AUTHORIZED.name().equals(intent.getStatus())) {
            throw new PaymentValidationException("Only AUTHORIZED payments can be voided");
        }
        if (intent.getCapturedMinor() != null && intent.getCapturedMinor() > 0) {
            throw new PaymentValidationException("Cannot void after capture; issue a refund instead");
        }
        Authorization auth = authRepo
                .findFirstByIntentIdAndStatusOrderByCreatedAtDesc(intentId, PaymentStatus.AUTHORIZED.name())
                .orElseThrow(() -> new PaymentValidationException("No active authorization to void"));
        VoidResult vr = connectors.card().voidAuthorization(
                new VoidCommand(connectorRef(auth), auth.getAmountMinor()));
        if (!vr.success()) {
            throw new ConnectorException("Void declined by connector");
        }
        auth.setStatus("VOIDED");
        authRepo.save(auth);
        PaymentVoid pv = new PaymentVoid();
        pv.setId(newId());
        pv.setAuthorizationId(auth.getId());
        pv.setAmountMinor(auth.getAmountMinor());
        pv.setStatus("VOIDED");
        voidRepo.save(pv);

        stateMachine.transition(intent, PaymentStatus.CANCELLED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_CANCELLED, basePayload(intent));
        releaseLimitReservation(intent, "VOIDED");
        return intent;
    }

    // ------------------------------------------------------------------ refund

    @Transactional
    public PaymentIntent refund(String intentId, RefundRequest req) {
        PaymentIntent intent = load(intentId);
        if (!isOneOf(intent.getStatus(), PaymentStatus.CAPTURED, PaymentStatus.CONFIRMED,
                PaymentStatus.SETTLED, PaymentStatus.PARTIALLY_REFUNDED)) {
            throw new PaymentValidationException("Payment is not in a refundable state");
        }
        long refundable = intent.getCapturedMinor() - intent.getRefundedMinor();
        long amount = req != null && req.amountMinor() != null ? req.amountMinor() : refundable;
        if (amount <= 0 || amount > refundable) {
            throw new PaymentValidationException(
                    "Refund amount " + amount + " must be > 0 and <= refundable " + refundable);
        }
        Capture capture = captureRepo.findByIntentId(intentId).stream().findFirst()
                .orElseThrow(() -> new PaymentValidationException("No capture found to refund"));
        String reason = req != null ? req.reason() : null;

        RefundCommand cmd = new RefundCommand(
                capture.getNetworkRef() != null ? capture.getNetworkRef() : capture.getId(),
                amount, intent.getCurrency(), reason);
        RefundResult rr = parseMethod(intent.getPaymentMethod()) == PaymentMethod.UPI
                ? connectors.upi().refund(cmd)
                : connectors.card().refund(cmd);
        if (!rr.success()) {
            throw new ConnectorException("Refund declined by connector: " + rr.declineCode());
        }

        Refund refund = new Refund();
        refund.setId(newId());
        refund.setCaptureId(capture.getId());
        refund.setIntentId(intent.getId());
        refund.setAmountMinor(amount);
        refund.setCurrency(intent.getCurrency());
        refund.setReason(reason);
        refund.setStatus("REFUNDED");
        refund.setNetworkRef(rr.networkRef());
        refundRepo.save(refund);

        intent.setRefundedMinor(intent.getRefundedMinor() + amount);
        PaymentStatus target = intent.getRefundedMinor() >= intent.getCapturedMinor()
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        stateMachine.transition(intent, target, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_REFUNDED, basePayload(intent));
        return intent;
    }

    // ----------------------------------------------------------------- confirm

    @Transactional
    public PaymentIntent confirm(String intentId) {
        PaymentIntent intent = load(intentId);
        stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_CONFIRMED, basePayload(intent));
        return intent;
    }

    // ------------------------------------------------------------------- retry

    @Transactional
    public PaymentIntent retry(String intentId) {
        PaymentIntent intent = load(intentId);
        if (!PaymentStatus.FAILED.name().equals(intent.getStatus())) {
            throw new PaymentValidationException("Only FAILED payments can be retried");
        }
        stateMachine.transition(intent, PaymentStatus.CREATED, null);
        intentRepo.save(intent);
        return authorize(intentId);
    }

    // -------------------------------------------------------------- UPI intent

    @Transactional
    public UpiIntentResponse initiateUpiIntent(String intentId) {
        PaymentIntent intent = load(intentId);
        if (parseMethod(intent.getPaymentMethod()) != PaymentMethod.UPI) {
            throw new PaymentValidationException("UPI intent is only valid for UPI payments");
        }
        if (!PaymentStatus.CREATED.name().equals(intent.getStatus())) {
            throw new PaymentValidationException("UPI intent can only be generated for CREATED payments");
        }
        UpiIntentResult res = connectors.upi().intent(new UpiIntentCommand(
                intent.getId(), intent.getAmountMinor(), intent.getCurrency(), intent.getDescription()));
        intent.setConnectorId(connectors.upi().connectorId());
        intent.setSubStatus("UPI_INTENT_PENDING");
        intentRepo.save(intent);
        persistPendingUpiAuthorization(intent, res.providerRef());
        return new UpiIntentResponse(intent.getId(), res.providerRef(),
                res.intentUri(), res.qrPayload(), (int) upiExpirySeconds);
    }

    private PaymentIntent upiCollect(PaymentIntent intent) {
        PaymentAttempt attempt = newAttempt(intent, connectors.upi().connectorId());
        UpiCollectResult cr = connectors.upi().collect(new UpiCollectCommand(
                intent.getId(), intent.getPayerVpa(), intent.getAmountMinor(),
                intent.getCurrency(), intent.getDescription(), (int) upiExpirySeconds));
        attempt.setOutcome(cr.outcome().name());
        attempt.setMappedDeclineCode(cr.declineCode());
        attemptRepo.save(attempt);

        if (cr.outcome() == AttemptOutcome.PENDING) {
            intent.setSubStatus("UPI_COLLECT_PENDING");
            intent.setConnectorId(connectors.upi().connectorId());
            intentRepo.save(intent);
            persistPendingUpiAuthorization(intent, cr.providerRef());
        } else {
            fail(intent, "UPI_COLLECT_DECLINED", cr.declineCode());
        }
        return intent;
    }

    // --------------------------------------------------------------- callbacks

    @Transactional
    public PaymentIntent handleUpiCallback(UpiCallbackRequest req) {
        PaymentIntent intent = load(req.intentId());
        boolean success = "SUCCESS".equalsIgnoreCase(req.status());
        if (!PaymentStatus.CREATED.name().equals(intent.getStatus())) {
            log.info("Ignoring UPI callback for intent {} in state {}", intent.getId(), intent.getStatus());
            return intent;
        }
        if (!success) {
            fail(intent, "UPI_DEBIT_FAILED", req.declineCode());
            return intent;
        }
        // UPI collapses authorize + capture + confirm into one debit.
        Authorization auth = resolveUpiAuthorization(intent, req.npciTxnId());
        intent.setAuthorizedMinor(intent.getAmountMinor());
        stateMachine.transition(intent, PaymentStatus.AUTHORIZED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_AUTHORIZED, basePayload(intent));

        Capture capture = new Capture();
        capture.setId(newId());
        capture.setAuthorizationId(auth.getId());
        capture.setIntentId(intent.getId());
        capture.setAmountMinor(intent.getAmountMinor());
        capture.setCurrency(intent.getCurrency());
        capture.setStatus("CAPTURED");
        capture.setIsFinal(true);
        capture.setNetworkRef(req.npciTxnId());
        captureRepo.save(capture);

        intent.setCapturedMinor(intent.getAmountMinor());
        stateMachine.transition(intent, PaymentStatus.CAPTURED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_CAPTURED, basePayload(intent));

        stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_CONFIRMED, basePayload(intent));
        return intent;
    }

    @Transactional
    public PaymentIntent handleThreeDsCallback(ThreeDsCallbackRequest req) {
        PaymentIntent intent = load(req.intentId());
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
        Authorization auth = new Authorization();
        auth.setId(newId());
        auth.setIntentId(intent.getId());
        auth.setAmountMinor(intent.getAmountMinor());
        auth.setCurrency(intent.getCurrency());
        auth.setStatus(PaymentStatus.AUTHORIZED.name());
        auth.setEci(req.eci());
        auth.setThreeDsSessionId(session.getId());
        auth.setExpiresAt(Instant.now().plus(cardExpirySeconds, ChronoUnit.SECONDS));
        authRepo.save(auth);

        intent.setAuthorizedMinor(intent.getAmountMinor());
        stateMachine.transition(intent, PaymentStatus.AUTHORIZED, null);
        intentRepo.save(intent);
        outbox.append(intent.getId(), EVT_AUTHORIZED, basePayload(intent));
        if (CaptureMethod.AUTOMATIC.name().equals(intent.getCaptureMethod())) {
            autoCaptureAndConfirm(intent, auth);
        }
        return intent;
    }

    // ------------------------------------------------------------------ expiry

    @Transactional
    public int expireStaleAuthorizations() {
        Instant now = Instant.now();
        int expired = 0;
        for (Authorization auth : authRepo.findByStatusAndExpiresAtBefore(PaymentStatus.AUTHORIZED.name(), now)) {
            PaymentIntent intent = intentRepo.findById(auth.getIntentId()).orElse(null);
            if (intent == null) continue;
            if (isOneOf(intent.getStatus(), PaymentStatus.AUTHORIZED, PaymentStatus.PARTIALLY_CAPTURED)) {
                auth.setStatus("EXPIRED");
                authRepo.save(auth);
                stateMachine.transition(intent, PaymentStatus.EXPIRED, null);
                intentRepo.save(intent);
                outbox.append(intent.getId(), EVT_EXPIRED, basePayload(intent));
                expired++;
            }
        }
        // UPI collect requests that were never approved in time.
        for (Authorization auth : authRepo.findByStatusAndExpiresAtBefore("PENDING", now)) {
            PaymentIntent intent = intentRepo.findById(auth.getIntentId()).orElse(null);
            if (intent == null) continue;
            if (PaymentStatus.CREATED.name().equals(intent.getStatus())) {
                auth.setStatus("EXPIRED");
                authRepo.save(auth);
                stateMachine.transition(intent, PaymentStatus.EXPIRED, "UPI_TIMEOUT");
                intentRepo.save(intent);
                outbox.append(intent.getId(), EVT_EXPIRED, basePayload(intent));
                expired++;
            }
        }
        return expired;
    }

    // ------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PaymentIntent get(String intentId) {
        return load(intentId);
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(String intentId) {
        load(intentId);
        return outboxRepo.findByAggregateIdOrderByCreatedAtAsc(intentId).stream()
                .map(o -> new TimelineEntry(o.getEventType(), o.getCreatedAt(), o.getPayload()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> search(PaymentSearchCriteria c, Pageable pageable) {
        return intentRepo.findAll(buildSpec(c), pageable);
    }

    // ----------------------------------------------------------------- helpers

    private PaymentIntent load(String intentId) {
        return intentRepo.findById(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + intentId));
    }

    private void fail(PaymentIntent intent, String subStatus, String code) {
        stateMachine.transition(intent, PaymentStatus.FAILED, subStatus);
        intentRepo.save(intent);
        Map<String, Object> payload = basePayload(intent);
        payload.put("declineCode", code);
        outbox.append(intent.getId(), EVT_FAILED, payload);
    }

    private void releaseLimitReservation(PaymentIntent intent, String reason) {
        String reservationId = intent.getLimitReservationId();
        if (reservationId == null || reservationId.isBlank()) {
            return;
        }
        limitClient.release(reservationId, reason);
    }

    private void commitLimitReservation(PaymentIntent intent) {
        String reservationId = intent.getLimitReservationId();
        if (reservationId == null || reservationId.isBlank()) {
            return;
        }
        limitClient.commit(reservationId, intent.getCapturedMinor());
    }

    private PaymentAttempt newAttempt(PaymentIntent intent, String connectorId) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(newId());
        attempt.setIntentId(intent.getId());
        attempt.setAttemptNo((int) attemptRepo.countByIntentId(intent.getId()) + 1);
        attempt.setConnectorId(connectorId);
        attempt.setOutcome(AttemptOutcome.PENDING.name());
        return attempt;
    }

    private Authorization persistAuthorization(PaymentIntent intent, String attemptId,
                                               AuthorizeResult r, long expirySeconds) {
        Authorization auth = new Authorization();
        auth.setId(newId());
        auth.setIntentId(intent.getId());
        auth.setAttemptId(attemptId);
        auth.setAmountMinor(intent.getAmountMinor());
        auth.setCurrency(intent.getCurrency());
        auth.setStatus(PaymentStatus.AUTHORIZED.name());
        auth.setAuthCode(r.authCode());
        auth.setNetworkTxnId(r.connectorRef());
        auth.setRrn(r.rrn());
        auth.setArn(r.arn());
        auth.setEci(r.eci());
        auth.setAvsResult(r.avsResult());
        auth.setCvvResult(r.cvvResult());
        auth.setExpiresAt(r.expiresAt() != null ? r.expiresAt()
                : Instant.now().plus(expirySeconds, ChronoUnit.SECONDS));
        authRepo.save(auth);
        return auth;
    }

    private void persistPendingUpiAuthorization(PaymentIntent intent, String providerRef) {
        Authorization auth = new Authorization();
        auth.setId(newId());
        auth.setIntentId(intent.getId());
        auth.setAmountMinor(intent.getAmountMinor());
        auth.setCurrency(intent.getCurrency());
        auth.setStatus("PENDING");
        auth.setNetworkTxnId(providerRef);
        auth.setExpiresAt(Instant.now().plus(upiExpirySeconds, ChronoUnit.SECONDS));
        authRepo.save(auth);
    }

    private Authorization resolveUpiAuthorization(PaymentIntent intent, String npciTxnId) {
        Optional<Authorization> pending = authRepo.findByIntentId(intent.getId()).stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .findFirst();
        Authorization auth = pending.orElseGet(Authorization::new);
        if (auth.getId() == null) {
            auth.setId(newId());
            auth.setIntentId(intent.getId());
            auth.setAmountMinor(intent.getAmountMinor());
            auth.setCurrency(intent.getCurrency());
        }
        auth.setStatus(PaymentStatus.AUTHORIZED.name());
        if (npciTxnId != null) auth.setNetworkTxnId(npciTxnId);
        authRepo.save(auth);
        return auth;
    }

    private Specification<PaymentIntent> buildSpec(PaymentSearchCriteria c) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (c.merchantId() != null) ps.add(cb.equal(root.get("merchantId"), c.merchantId()));
            if (c.customerId() != null) ps.add(cb.equal(root.get("customerId"), c.customerId()));
            if (c.currency() != null) ps.add(cb.equal(root.get("currency"), c.currency().toUpperCase()));
            if (c.paymentMethod() != null) ps.add(cb.equal(root.get("paymentMethod"), c.paymentMethod().toUpperCase()));
            if (c.status() != null && !c.status().isEmpty()) ps.add(root.get("status").in(c.status()));
            if (c.minAmountMinor() != null) ps.add(cb.greaterThanOrEqualTo(root.get("amountMinor"), c.minAmountMinor()));
            if (c.maxAmountMinor() != null) ps.add(cb.lessThanOrEqualTo(root.get("amountMinor"), c.maxAmountMinor()));
            if (c.createdFrom() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdFrom()));
            if (c.createdTo() != null) ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), c.createdTo()));
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Map<String, Object> basePayload(PaymentIntent intent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentId", intent.getId());
        m.put("merchantId", intent.getMerchantId());
        m.put("status", intent.getStatus());
        m.put("paymentMethod", intent.getPaymentMethod());
        m.put("amountMinor", intent.getAmountMinor());
        m.put("currency", intent.getCurrency());
        m.put("authorizedMinor", intent.getAuthorizedMinor());
        m.put("capturedMinor", intent.getCapturedMinor());
        m.put("refundedMinor", intent.getRefundedMinor());
        m.put("occurredAt", Instant.now().toString());
        return m;
    }

    private void requireCard(PaymentIntent intent, String op) {
        if (parseMethod(intent.getPaymentMethod()) != PaymentMethod.CARD) {
            throw new PaymentValidationException(op + " is only supported for CARD payments");
        }
    }

    private void validateCreate(CreatePaymentRequest req, PaymentMethod method) {
        if (req.amountMinor() == null || req.amountMinor() <= 0) {
            throw new PaymentValidationException("amountMinor must be positive");
        }
        if (method == PaymentMethod.CARD
                && (req.instrumentToken() == null || req.instrumentToken().isBlank())) {
            throw new PaymentValidationException("instrumentToken is required for CARD payments");
        }
        if (method == PaymentMethod.UPI) {
            boolean intentFlow = req.upiFlow() != null && req.upiFlow().equalsIgnoreCase("INTENT");
            if (!intentFlow && (req.payerVpa() == null || req.payerVpa().isBlank())) {
                throw new PaymentValidationException(
                        "payerVpa is required for UPI COLLECT flow (or set upiFlow=INTENT)");
            }
        }
        if (req.metadata() != null) {
            if (req.metadata().size() > 50) {
                throw new PaymentValidationException("metadata supports at most 50 keys");
            }
            req.metadata().forEach((k, v) -> {
                if (k != null && k.length() > 40) {
                    throw new PaymentValidationException("metadata key exceeds 40 characters: " + k);
                }
                if (v != null && v.length() > 500) {
                    throw new PaymentValidationException("metadata value exceeds 500 characters for key " + k);
                }
            });
        }
    }

    private CaptureMethod resolveCaptureMethod(CreatePaymentRequest req, PaymentMethod method) {
        if (method == PaymentMethod.UPI) {
            return CaptureMethod.AUTOMATIC;
        }
        if (req.captureMethod() == null || req.captureMethod().isBlank()) {
            return CaptureMethod.AUTOMATIC;
        }
        try {
            return CaptureMethod.valueOf(req.captureMethod().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException("Unknown captureMethod: " + req.captureMethod());
        }
    }

    private PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new PaymentValidationException("Unsupported paymentMethod: " + value);
        }
    }

    private String mapSubStatus(AuthorizeResult r) {
        return switch (r.outcome()) {
            case TIMEOUT -> "PROVIDER_TIMEOUT";
            case SOFT_DECLINE -> "SOFT_DECLINED";
            case HARD_DECLINE -> "HARD_DECLINED";
            default -> "AUTH_ERROR";
        };
    }

    private String connectorRef(Authorization auth) {
        return auth.getNetworkTxnId() != null ? auth.getNetworkTxnId() : auth.getId();
    }

    private String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new PaymentValidationException("metadata is not serializable");
        }
    }

    private String canonical(CreatePaymentRequest req) {
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            return String.valueOf(req);
        }
    }

    private boolean isOneOf(String status, PaymentStatus... options) {
        for (PaymentStatus s : options) {
            if (s.name().equals(status)) {
                return true;
            }
        }
        return false;
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
