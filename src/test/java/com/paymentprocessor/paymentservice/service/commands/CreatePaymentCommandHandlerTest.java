package com.paymentprocessor.paymentservice.service.commands;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.PaymentConnector;
import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.service.IdempotencyService;
import com.paymentprocessor.paymentservice.support.IdGenerator;
import com.paymentprocessor.paymentservice.support.PaymentIntentValidator;
import com.paymentprocessor.paymentservice.support.PaymentMethodResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentCommandHandlerTest {

    @Mock
    private PaymentIntentRepository intentRepo;
    @Mock
    private ConnectorRegistry connectors;
    @Mock
    private PaymentConnector cardConnector;
    @Mock
    private com.paymentprocessor.paymentservice.connector.UpiConnector upiConnector;
    @Mock
    private IdempotencyService idempotency;
    @Mock
    private PaymentEventPublisher eventPublisher;
    @Mock
    private PaymentIntentValidator validator;
    @Mock
    private IdGenerator idGenerator;

    private PaymentMethodResolver methodResolver;
    private ObjectMapper objectMapper;
    private CreatePaymentCommandHandler handler;

    @BeforeEach
    void setUp() {
        methodResolver = new PaymentMethodResolver();
        objectMapper = new ObjectMapper();
        handler = new CreatePaymentCommandHandler(
                intentRepo, connectors, idempotency, eventPublisher,
                methodResolver, validator, idGenerator, objectMapper);
        lenient().when(connectors.card()).thenReturn(cardConnector);
        lenient().when(connectors.upi()).thenReturn(upiConnector);
        lenient().when(cardConnector.connectorId()).thenReturn("card-gateway");
        lenient().when(upiConnector.connectorId()).thenReturn("upi-psp");
        lenient().when(idGenerator.newId()).thenReturn("pi_test_01");
    }

    @Test
    void create_cardIntent_savesAndPublishesEvent() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "merchant_1", "cust_1", "CARD", "tok_123", null, null,
                5000L, "USD", null, "descriptor", "description", Map.of("key", "value"));
        PaymentIntent result = handler.create(req, null);

        assertThat(result.getId()).isEqualTo("pi_test_01");
        assertThat(result.getPaymentMethod()).isEqualTo("CARD");
        assertThat(result.getCaptureMethod()).isEqualTo(CaptureMethod.AUTOMATIC.name());
        assertThat(result.getConnectorId()).isEqualTo("card-gateway");
        verify(intentRepo).save(result);
        verify(eventPublisher).publish(any(PaymentEvent.PaymentCreatedEvent.class));
    }

    @Test
    void create_withIdempotencyKey_replaysPriorIntent() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "merchant_1", null, "CARD", "tok_123", null, null,
                100L, "USD", null, null, null, null);
        PaymentIntent prior = new PaymentIntent();
        prior.setId("pi_prior");
        when(idempotency.begin(eq("merchant_1"), eq("idem-key"), any())).thenReturn(Optional.of("pi_prior"));
        when(intentRepo.findById("pi_prior")).thenReturn(Optional.of(prior));

        PaymentIntent result = handler.create(req, "idem-key");

        assertThat(result.getId()).isEqualTo("pi_prior");
        verify(intentRepo, never()).save(any());
    }

    @Test
    void create_upiIntent_defaultsToAutomaticCapture() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "merchant_1", null, "UPI", null, "payer@upi", "INTENT",
                10000L, "INR", "MANUAL", null, null, null);

        PaymentIntent result = handler.create(req, null);

        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.UPI.name());
        assertThat(result.getCaptureMethod()).isEqualTo(CaptureMethod.AUTOMATIC.name());
    }
}
