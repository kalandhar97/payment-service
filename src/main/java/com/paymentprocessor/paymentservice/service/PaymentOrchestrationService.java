package com.paymentprocessor.paymentservice.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.dto.CaptureRequest;
import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.dto.PaymentSearchCriteria;
import com.paymentprocessor.paymentservice.dto.RefundRequest;
import com.paymentprocessor.paymentservice.dto.ThreeDsCallbackRequest;
import com.paymentprocessor.paymentservice.dto.TimelineEntry;
import com.paymentprocessor.paymentservice.dto.UpiCallbackRequest;
import com.paymentprocessor.paymentservice.dto.UpiIntentResponse;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.service.commands.AuthorizeCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.CaptureCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.ConfirmCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.CreatePaymentCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.ExpireStaleAuthorizationsHandler;
import com.paymentprocessor.paymentservice.service.commands.RefundCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.RetryCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.ThreeDsCallbackHandler;
import com.paymentprocessor.paymentservice.service.commands.UpiCallbackHandler;
import com.paymentprocessor.paymentservice.service.commands.UpiIntentCommandHandler;
import com.paymentprocessor.paymentservice.service.commands.VoidCommandHandler;
import com.paymentprocessor.paymentservice.service.queries.PaymentQueryService;

/**
 * Facade over the payment lifecycle. The real work is delegated to small, focused
 * command handlers and the read-only query service; this class keeps the REST
 * controllers decoupled from the command/handler wiring.
 */
@Service
public class PaymentOrchestrationService {

    private final CreatePaymentCommandHandler createHandler;
    private final AuthorizeCommandHandler authorizeHandler;
    private final CaptureCommandHandler captureHandler;
    private final VoidCommandHandler voidHandler;
    private final RefundCommandHandler refundHandler;
    private final ConfirmCommandHandler confirmHandler;
    private final RetryCommandHandler retryHandler;
    private final UpiIntentCommandHandler upiIntentHandler;
    private final UpiCallbackHandler upiCallbackHandler;
    private final ThreeDsCallbackHandler threeDsCallbackHandler;
    private final ExpireStaleAuthorizationsHandler expiryHandler;
    private final PaymentQueryService queryService;

    public PaymentOrchestrationService(CreatePaymentCommandHandler createHandler,
                                      AuthorizeCommandHandler authorizeHandler,
                                      CaptureCommandHandler captureHandler,
                                      VoidCommandHandler voidHandler,
                                      RefundCommandHandler refundHandler,
                                      ConfirmCommandHandler confirmHandler,
                                      RetryCommandHandler retryHandler,
                                      UpiIntentCommandHandler upiIntentHandler,
                                      UpiCallbackHandler upiCallbackHandler,
                                      ThreeDsCallbackHandler threeDsCallbackHandler,
                                      ExpireStaleAuthorizationsHandler expiryHandler,
                                      PaymentQueryService queryService) {
        this.createHandler = createHandler;
        this.authorizeHandler = authorizeHandler;
        this.captureHandler = captureHandler;
        this.voidHandler = voidHandler;
        this.refundHandler = refundHandler;
        this.confirmHandler = confirmHandler;
        this.retryHandler = retryHandler;
        this.upiIntentHandler = upiIntentHandler;
        this.upiCallbackHandler = upiCallbackHandler;
        this.threeDsCallbackHandler = threeDsCallbackHandler;
        this.expiryHandler = expiryHandler;
        this.queryService = queryService;
    }

    @Transactional
    public PaymentIntent createPayment(CreatePaymentRequest req, String idempotencyKey) {
        return createHandler.create(req, idempotencyKey);
    }

    @Transactional
    public PaymentIntent authorize(String intentId) {
        PaymentIntent intent = queryService.get(intentId);
        return authorizeHandler.authorize(intent);
    }

    @Transactional
    public PaymentIntent capture(String intentId, CaptureRequest req) {
        PaymentIntent intent = queryService.get(intentId);
        return captureHandler.capture(intent, req);
    }

    @Transactional
    public PaymentIntent voidAuthorization(String intentId) {
        PaymentIntent intent = queryService.get(intentId);
        return voidHandler.voidAuthorization(intent);
    }

    @Transactional
    public PaymentIntent refund(String intentId, RefundRequest req) {
        PaymentIntent intent = queryService.get(intentId);
        return refundHandler.refund(intent, req);
    }

    @Transactional
    public PaymentIntent confirm(String intentId) {
        PaymentIntent intent = queryService.get(intentId);
        return confirmHandler.confirm(intent);
    }

    @Transactional
    public PaymentIntent retry(String intentId) {
        PaymentIntent intent = queryService.get(intentId);
        return retryHandler.retry(intent);
    }

    @Transactional
    public UpiIntentResponse initiateUpiIntent(String intentId) {
        PaymentIntent intent = queryService.get(intentId);
        return upiIntentHandler.initiate(intent);
    }

    @Transactional
    public PaymentIntent handleUpiCallback(UpiCallbackRequest req) {
        return upiCallbackHandler.handle(req);
    }

    @Transactional
    public PaymentIntent handleThreeDsCallback(ThreeDsCallbackRequest req) {
        return threeDsCallbackHandler.handle(req);
    }

    @Transactional
    public int expireStaleAuthorizations() {
        return expiryHandler.expire();
    }

    @Transactional(readOnly = true)
    public PaymentIntent get(String intentId) {
        return queryService.get(intentId);
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(String intentId) {
        return queryService.timeline(intentId);
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> search(PaymentSearchCriteria criteria, Pageable pageable) {
        return queryService.search(criteria, pageable);
    }
}
