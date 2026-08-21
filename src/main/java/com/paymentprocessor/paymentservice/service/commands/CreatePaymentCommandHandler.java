package com.paymentprocessor.paymentservice.service.commands;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.exception.ResourceNotFoundException;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.service.IdempotencyService;
import com.paymentprocessor.paymentservice.support.IdGenerator;
import com.paymentprocessor.paymentservice.support.PaymentIntentValidator;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;

/**
 * Handles creation of a {@link PaymentIntent} with idempotency support and a domain event.
 */
@Component
public class CreatePaymentCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final ConnectorRegistry connectors;
    private final IdempotencyService idempotency;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentMethodResolver methodResolver;
    private final PaymentIntentValidator validator;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public CreatePaymentCommandHandler(PaymentIntentRepository intentRepo,
                                       ConnectorRegistry connectors,
                                       IdempotencyService idempotency,
                                       PaymentEventPublisher eventPublisher,
                                       PaymentMethodResolver methodResolver,
                                       PaymentIntentValidator validator,
                                       IdGenerator idGenerator,
                                       ObjectMapper objectMapper) {
        this.intentRepo = intentRepo;
        this.connectors = connectors;
        this.idempotency = idempotency;
        this.eventPublisher = eventPublisher;
        this.methodResolver = methodResolver;
        this.validator = validator;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentIntent create(CreatePaymentRequest req, String idempotencyKey) {
        PaymentMethod method = methodResolver.resolveMethod(req.paymentMethod());
        validator.validateCreate(req, method);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> prior = idempotency.begin(req.merchantId(), idempotencyKey, canonical(req));
            if (prior.isPresent()) {
                return intentRepo.findById(prior.get())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Idempotent replay references missing intent " + prior.get()));
            }
        }

        PaymentIntent intent = buildIntent(req, method);
        intentRepo.save(intent);

        eventPublisher.publish(new PaymentEvent.PaymentCreatedEvent(intent));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.complete(req.merchantId(), idempotencyKey, 201, intent.getId());
        }
        return intent;
    }

    private PaymentIntent buildIntent(CreatePaymentRequest req, PaymentMethod method) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(idGenerator.newId());
        intent.setMerchantId(req.merchantId());
        intent.setCustomerId(req.customerId());
        intent.setPaymentMethod(method.name());
        intent.setInstrumentToken(req.instrumentToken());
        intent.setPayerVpa(req.payerVpa());
        intent.setAmountMinor(req.amountMinor());
        intent.setCurrency(req.currency().toUpperCase());
        intent.setStatus(com.paymentprocessor.paymentservice.enums.PaymentStatus.CREATED.name());
        intent.setCaptureMethod(methodResolver.resolveCaptureMethod(req, method).name());
        intent.setConnectorId(method == PaymentMethod.CARD
                ? connectors.card().connectorId() : connectors.upi().connectorId());
        intent.setStatementDescriptor(req.statementDescriptor());
        intent.setDescription(req.description());
        intent.setMetadata(writeMetadata(req.metadata()));
        intent.setAuthorizedMinor(0L);
        intent.setCapturedMinor(0L);
        intent.setRefundedMinor(0L);
        return intent;
    }

    private String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new com.paymentprocessor.paymentservice.exception.PaymentValidationException("metadata is not serializable");
        }
    }

    private String canonical(CreatePaymentRequest req) {
        try {
            return objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            return String.valueOf(req);
        }
    }
}
