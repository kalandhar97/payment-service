package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.model.VoidCommand;
import com.paymentprocessor.paymentservice.connector.model.VoidResult;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.entity.PaymentVoid;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.exception.ConnectorException;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.repository.PaymentVoidRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.IdGenerator;
import com.paymentprocessor.paymentservice.support.LimitReservationService;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;
import com.paymentprocessor.paymentservice.support.PaymentStatusChecker;

/** Voids an active card authorization, releasing any held funds. */
@Component
public class VoidCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final AuthorizationRepository authRepo;
    private final PaymentVoidRepository voidRepo;
    private final ConnectorRegistry connectors;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final LimitReservationService limitReservationService;
    private final PaymentMethodResolver methodResolver;
    private final PaymentStatusChecker statusChecker;
    private final IdGenerator idGenerator;

    public VoidCommandHandler(PaymentIntentRepository intentRepo,
                             AuthorizationRepository authRepo,
                             PaymentVoidRepository voidRepo,
                             ConnectorRegistry connectors,
                             PaymentStateMachine stateMachine,
                             PaymentEventPublisher eventPublisher,
                             LimitReservationService limitReservationService,
                             PaymentMethodResolver methodResolver,
                             PaymentStatusChecker statusChecker,
                             IdGenerator idGenerator) {
        this.intentRepo = intentRepo;
        this.authRepo = authRepo;
        this.voidRepo = voidRepo;
        this.connectors = connectors;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.limitReservationService = limitReservationService;
        this.methodResolver = methodResolver;
        this.statusChecker = statusChecker;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PaymentIntent voidAuthorization(PaymentIntent intent) {
        requireCard(intent, "void");
        if (!statusChecker.isInState(intent, PaymentStatus.AUTHORIZED)) {
            throw new PaymentValidationException("Only AUTHORIZED payments can be voided");
        }
        if (nullToZero(intent.getCapturedMinor()) > 0) {
            throw new PaymentValidationException("Cannot void after capture; issue a refund instead");
        }
        Authorization auth = findActiveAuthorization(intent.getId());
        VoidResult vr = connectors.card().voidAuthorization(
                new VoidCommand(connectorRef(auth), auth.getAmountMinor()));
        if (!vr.success()) {
            throw new ConnectorException("Void declined by connector");
        }

        auth.setStatus("VOIDED");
        authRepo.save(auth);

        PaymentVoid pv = new PaymentVoid();
        pv.setId(idGenerator.newId());
        pv.setAuthorizationId(auth.getId());
        pv.setAmountMinor(auth.getAmountMinor());
        pv.setStatus("VOIDED");
        voidRepo.save(pv);

        stateMachine.transition(intent, PaymentStatus.CANCELLED, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentCancelledEvent(intent));
        limitReservationService.release(intent, "VOIDED");
        return intent;
    }

    private Authorization findActiveAuthorization(String intentId) {
        return authRepo
                .findFirstByIntentIdAndStatusOrderByCreatedAtDesc(intentId, PaymentStatus.AUTHORIZED.name())
                .orElseThrow(() -> new PaymentValidationException("No active authorization to void"));
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
