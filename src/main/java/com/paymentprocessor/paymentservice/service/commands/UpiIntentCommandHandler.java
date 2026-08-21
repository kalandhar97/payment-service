package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentResult;
import com.paymentprocessor.paymentservice.dto.UpiIntentResponse;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.support.AuthorizationFactory;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;

/**
 * Generates a UPI intent link for a CREATED UPI intent and records a pending authorization
 * so the callback can be matched when the payer completes the flow.
 */
@Component
public class UpiIntentCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final AuthorizationRepository authRepo;
    private final ConnectorRegistry connectors;
    private final AuthorizationFactory authorizationFactory;
    private final PaymentMethodResolver methodResolver;

    public UpiIntentCommandHandler(PaymentIntentRepository intentRepo,
                                  AuthorizationRepository authRepo,
                                  ConnectorRegistry connectors,
                                  AuthorizationFactory authorizationFactory,
                                  PaymentMethodResolver methodResolver) {
        this.intentRepo = intentRepo;
        this.authRepo = authRepo;
        this.connectors = connectors;
        this.authorizationFactory = authorizationFactory;
        this.methodResolver = methodResolver;
    }

    @Transactional
    public UpiIntentResponse initiate(PaymentIntent intent) {
        if (methodResolver.resolveMethod(intent.getPaymentMethod()) != PaymentMethod.UPI) {
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

        Authorization pending = authorizationFactory.createPendingForUpi(intent, res.providerRef());
        authRepo.save(pending);

        return new UpiIntentResponse(intent.getId(), res.providerRef(),
                res.intentUri(), res.qrPayload(), (int) authorizationFactory.upiExpirySeconds());
    }
}
