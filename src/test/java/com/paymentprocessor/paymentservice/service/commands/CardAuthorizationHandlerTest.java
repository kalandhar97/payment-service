package com.paymentprocessor.paymentservice.service.commands;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.paymentprocessor.paymentservice.connector.ConnectorRegistry;
import com.paymentprocessor.paymentservice.connector.FraudClient;
import com.paymentprocessor.paymentservice.connector.FraudDecision;
import com.paymentprocessor.paymentservice.connector.LimitClient;
import com.paymentprocessor.paymentservice.connector.LimitDecisionResult;
import com.paymentprocessor.paymentservice.connector.PaymentConnector;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeCommand;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeResult;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentAttempt;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.AuthorizationRepository;
import com.paymentprocessor.paymentservice.repository.IntentTransitionRepository;
import com.paymentprocessor.paymentservice.repository.PaymentAttemptRepository;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.repository.ThreeDsSessionRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;
import com.paymentprocessor.paymentservice.support.AuthorizationFactory;
import com.paymentprocessor.paymentservice.support.LimitReservationService;
import com.paymentprocessor.paymentservice.support.PaymentAttemptFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardAuthorizationHandlerTest {

    @Mock
    private PaymentIntentRepository intentRepo;
    @Mock
    private PaymentAttemptRepository attemptRepo;
    @Mock
    private AuthorizationRepository authRepo;
    @Mock
    private ThreeDsSessionRepository threeDsRepo;
    @Mock
    private ConnectorRegistry connectors;
    @Mock
    private PaymentConnector cardConnector;
    @Mock
    private FraudClient fraudClient;
    @Mock
    private LimitClient limitClient;
    @Mock
    private IntentTransitionRepository transitions;
    @Mock
    private PaymentEventPublisher eventPublisher;
    @Mock
    private PaymentAttemptFactory attemptFactory;
    @Mock
    private AuthorizationFactory authorizationFactory;
    @Mock
    private LimitReservationService limitReservationService;
    @Mock
    private CaptureCommandHandler captureHandler;

    private CardAuthorizationHandler handler;

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine(transitions);
        handler = new CardAuthorizationHandler(
                intentRepo, attemptRepo, authRepo, threeDsRepo, connectors,
                fraudClient, limitClient, stateMachine, eventPublisher,
                attemptFactory, authorizationFactory, limitReservationService, captureHandler);
        lenient().when(connectors.card()).thenReturn(cardConnector);
        lenient().when(cardConnector.connectorId()).thenReturn("card-gateway");
        lenient().when(attemptRepo.countByIntentId(any())).thenReturn(0L);
        lenient().when(transitions.existsByFromStatusAndToStatus(any(), any())).thenReturn(true);
    }

    @Test
    void authorize_approvedWithManualCapture_transitionsToAuthorized() {
        PaymentIntent intent = cardIntent(CaptureMethod.MANUAL);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId("att_1");
        Authorization auth = new Authorization();
        auth.setId("auth_1");

        when(limitClient.reserve(any(), any(), any(), anyLong(), any()))
                .thenReturn(new LimitDecisionResult(true, false, "resv_1", "RESERVED", null));
        when(fraudClient.evaluate(any(), any(), anyLong(), any(), any()))
                .thenReturn(FraudDecision.approve());
        when(attemptFactory.newAttempt(intent, "card-gateway")).thenReturn(attempt);
        when(cardConnector.authorize(any(AuthorizeCommand.class)))
                .thenReturn(new AuthorizeResult(AttemptOutcome.APPROVED, "ref", "authCode",
                        null, null, null, null, null, null, null, null, Instant.now().plusSeconds(600)));
        when(authorizationFactory.createFromCardResult(eq(intent), eq("att_1"), any(AuthorizeResult.class)))
                .thenReturn(auth);

        PaymentIntent result = handler.authorize(intent);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED.name());
        verify(authRepo).save(auth);
        verify(eventPublisher).publish(any(PaymentEvent.PaymentAuthorizedEvent.class));
    }

    @Test
    void authorize_limitDecline_transitionsToFailed() {
        PaymentIntent intent = cardIntent(CaptureMethod.MANUAL);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId("att_1");

        when(limitClient.reserve(any(), any(), any(), anyLong(), any()))
                .thenReturn(new LimitDecisionResult(false, false, null, "DECLINED", "LIMIT_EXCEEDED"));
        when(attemptFactory.newAttempt(intent, "card-gateway")).thenReturn(attempt);

        PaymentIntent result = handler.authorize(intent);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED.name());
        verify(eventPublisher).publish(any(PaymentEvent.PaymentFailedEvent.class));
    }

    @Test
    void authorize_pendingThreeDs_setsSubStatus() {
        PaymentIntent intent = cardIntent(CaptureMethod.MANUAL);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId("att_1");

        when(limitClient.reserve(any(), any(), any(), anyLong(), any()))
                .thenReturn(new LimitDecisionResult(true, false, "resv_1", "RESERVED", null));
        when(fraudClient.evaluate(any(), any(), anyLong(), any(), any()))
                .thenReturn(FraudDecision.approve());
        when(attemptFactory.newAttempt(intent, "card-gateway")).thenReturn(attempt);
        when(cardConnector.authorize(any(AuthorizeCommand.class)))
                .thenReturn(new AuthorizeResult(AttemptOutcome.PENDING, "ref", null,
                        null, null, null, null, null, null, null, "https://acs.test", null));

        PaymentIntent result = handler.authorize(intent);

        assertThat(result.getSubStatus()).isEqualTo("3DS_PENDING");
        verify(threeDsRepo).save(any());
    }

    private PaymentIntent cardIntent(CaptureMethod captureMethod) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_1");
        intent.setMerchantId("merchant_1");
        intent.setAmountMinor(1000L);
        intent.setCurrency("USD");
        intent.setPaymentMethod(PaymentMethod.CARD.name());
        intent.setInstrumentToken("tok_1");
        intent.setCaptureMethod(captureMethod.name());
        intent.setStatus(PaymentStatus.CREATED.name());
        return intent;
    }
}
