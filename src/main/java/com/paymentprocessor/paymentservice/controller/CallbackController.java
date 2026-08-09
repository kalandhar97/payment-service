package com.paymentprocessor.paymentservice.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paymentprocessor.paymentservice.dto.PaymentResponse;
import com.paymentprocessor.paymentservice.dto.ThreeDsCallbackRequest;
import com.paymentprocessor.paymentservice.dto.UpiCallbackRequest;
import com.paymentprocessor.paymentservice.service.PaymentMapper;
import com.paymentprocessor.paymentservice.service.PaymentOrchestrationService;

/**
 * Inbound asynchronous provider callbacks. UPI debit results and 3-D Secure
 * authentication outcomes arrive here and drive the intent to its terminal state.
 * In production these endpoints must verify the provider's signature before use.
 */
@RestController
@RequestMapping("/v1/callbacks")
public class CallbackController {

    private final PaymentOrchestrationService orchestration;
    private final PaymentMapper mapper;

    public CallbackController(PaymentOrchestrationService orchestration, PaymentMapper mapper) {
        this.orchestration = orchestration;
        this.mapper = mapper;
    }

    @PostMapping("/upi")
    public PaymentResponse upiCallback(@Valid @RequestBody UpiCallbackRequest request) {
        return mapper.toResponse(orchestration.handleUpiCallback(request));
    }

    @PostMapping("/3ds")
    public PaymentResponse threeDsCallback(@Valid @RequestBody ThreeDsCallbackRequest request) {
        return mapper.toResponse(orchestration.handleThreeDsCallback(request));
    }
}
