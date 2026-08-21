package com.paymentprocessor.paymentservice.service.commands;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.PaymentConnector;
import com.paymentprocessor.paymentservice.connector.UpiConnector;
import com.paymentprocessor.paymentservice.connector.model.RefundCommand;
import com.paymentprocessor.paymentservice.connector.model.RefundResult;
import com.paymentprocessor.paymentservice.dto.RefundRequest;
import com.paymentprocessor.paymentservice.entity.Capture;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.entity.Refund;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.exception.ConnectorException;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.repository.CaptureRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.repository.RefundRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.AmountCalculator;
import com.paymentprocessor.paymentservice.support.IdGenerator;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;
import com.paymentprocessor.paymentservice.support.PaymentStatusChecker;

/**
 * Refunds a previously captured amount against a payment intent. Refunds can be
 * partial and are applied to the most recent capture by default.
 */
@Component
public class RefundCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final CaptureRepository captureRepo;
    private final RefundRepository refundRepo;
    private final ConnectorRegistry connectors;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;
    private final AmountCalculator amountCalculator;
    private final PaymentMethodResolver methodResolver;
    private final PaymentStatusChecker statusChecker;
    private final IdGenerator idGenerator;

    public RefundCommandHandler(PaymentIntentRepository intentRepo,
                               CaptureRepository captureRepo,
                               RefundRepository refundRepo,
                               ConnectorRegistry connectors,
                               PaymentStateMachine stateMachine,
                               PaymentEventPublisher eventPublisher,
                               AmountCalculator amountCalculator,
                               PaymentMethodResolver methodResolver,
                               PaymentStatusChecker statusChecker,
                               IdGenerator idGenerator) {
        this.intentRepo = intentRepo;
        this.captureRepo = captureRepo;
        this.refundRepo = refundRepo;
        this.connectors = connectors;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.amountCalculator = amountCalculator;
        this.methodResolver = methodResolver;
        this.statusChecker = statusChecker;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PaymentIntent refund(PaymentIntent intent, RefundRequest req) {
        if (!statusChecker.isInState(intent, PaymentStatus.CAPTURED, PaymentStatus.CONFIRMED,
                PaymentStatus.SETTLED, PaymentStatus.PARTIALLY_REFUNDED)) {
            throw new PaymentValidationException("Payment is not in a refundable state");
        }
        Capture capture = findRefundTarget(intent.getId());
        long amount = amountCalculator.calculateRefundAmount(intent, req);
        String reason = req != null ? req.reason() : null;

        RefundCommand cmd = new RefundCommand(
                capture.getNetworkRef() != null ? capture.getNetworkRef() : capture.getId(),
                amount, intent.getCurrency(), reason);
        RefundResult rr = methodResolver.resolveMethod(intent.getPaymentMethod()) == PaymentMethod.UPI
                ? connectors.upi().refund(cmd)
                : connectors.card().refund(cmd);
        if (!rr.success()) {
            throw new ConnectorException("Refund declined by connector: " + rr.declineCode());
        }

        Refund refund = buildRefund(intent, capture, amount, reason, rr);
        refundRepo.save(refund);

        intent.setRefundedMinor(nullToZero(intent.getRefundedMinor()) + amount);
        PaymentStatus target = intent.getRefundedMinor() >= nullToZero(intent.getCapturedMinor())
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        stateMachine.transition(intent, target, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentRefundedEvent(intent));
        return intent;
    }

    private Capture findRefundTarget(String intentId) {
        List<Capture> captures = captureRepo.findByIntentId(intentId);
        return captures.stream()
                .max(Comparator.comparing(Capture::getCapturedAt))
                .orElseThrow(() -> new PaymentValidationException("No capture found to refund"));
    }

    private Refund buildRefund(PaymentIntent intent, Capture capture, long amount,
                              String reason, RefundResult result) {
        Refund refund = new Refund();
        refund.setId(idGenerator.newId());
        refund.setCaptureId(capture.getId());
        refund.setIntentId(intent.getId());
        refund.setAmountMinor(amount);
        refund.setCurrency(intent.getCurrency());
        refund.setReason(reason);
        refund.setStatus("REFUNDED");
        refund.setNetworkRef(result.networkRef());
        return refund;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
