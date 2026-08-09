package com.paymentprocessor.paymentservice.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paymentprocessor.paymentservice.dto.CaptureRequest;
import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.dto.PaymentResponse;
import com.paymentprocessor.paymentservice.dto.PaymentSearchCriteria;
import com.paymentprocessor.paymentservice.dto.RefundRequest;
import com.paymentprocessor.paymentservice.dto.TimelineEntry;
import com.paymentprocessor.paymentservice.dto.UpiIntentResponse;
import com.paymentprocessor.paymentservice.service.PaymentMapper;
import com.paymentprocessor.paymentservice.service.PaymentOrchestrationService;

/**
 * Merchant-facing payment API. Exposes the full lifecycle described in the service
 * README: create, authorize, capture (incl. partial), void, refund, confirm, retry,
 * status, timeline, search, plus UPI intent generation.
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentOrchestrationService orchestration;
    private final PaymentMapper mapper;

    public PaymentController(PaymentOrchestrationService orchestration, PaymentMapper mapper) {
        this.orchestration = orchestration;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse body = mapper.toResponse(orchestration.createPayment(request, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/authorize")
    public PaymentResponse authorize(@PathVariable String id) {
        return mapper.toResponse(orchestration.authorize(id));
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@PathVariable String id,
                                   @RequestBody(required = false) CaptureRequest request) {
        return mapper.toResponse(orchestration.capture(id, request));
    }

    @PostMapping("/{id}/void")
    public PaymentResponse voidAuthorization(@PathVariable String id) {
        return mapper.toResponse(orchestration.voidAuthorization(id));
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable String id,
                                  @RequestBody(required = false) RefundRequest request) {
        return mapper.toResponse(orchestration.refund(id, request));
    }

    @PostMapping("/{id}/confirm")
    public PaymentResponse confirm(@PathVariable String id) {
        return mapper.toResponse(orchestration.confirm(id));
    }

    @PostMapping("/{id}/retry")
    public PaymentResponse retry(@PathVariable String id) {
        return mapper.toResponse(orchestration.retry(id));
    }

    @PostMapping("/{id}/upi-intent")
    public UpiIntentResponse upiIntent(@PathVariable String id) {
        return orchestration.initiateUpiIntent(id);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        return mapper.toResponse(orchestration.get(id));
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineEntry> timeline(@PathVariable String id) {
        return orchestration.timeline(id);
    }

    @GetMapping
    public Page<PaymentResponse> search(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Long minAmountMinor,
            @RequestParam(required = false) Long maxAmountMinor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            Pageable pageable) {
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(
                merchantId, customerId, status, currency, paymentMethod,
                minAmountMinor, maxAmountMinor, createdFrom, createdTo);
        return orchestration.search(criteria, pageable).map(mapper::toResponse);
    }
}
