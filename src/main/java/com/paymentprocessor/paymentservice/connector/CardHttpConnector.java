package com.paymentprocessor.paymentservice.connector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paymentprocessor.paymentservice.config.ConnectorProperties;
import com.paymentprocessor.paymentservice.config.HttpClientConfig.RestClientFactory;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeCommand;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeResult;
import com.paymentprocessor.paymentservice.connector.model.CaptureCommand;
import com.paymentprocessor.paymentservice.connector.model.CaptureResult;
import com.paymentprocessor.paymentservice.connector.model.RefundCommand;
import com.paymentprocessor.paymentservice.connector.model.RefundResult;
import com.paymentprocessor.paymentservice.connector.model.VoidCommand;
import com.paymentprocessor.paymentservice.connector.model.VoidResult;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;

/**
 * Card gateway connector backed by a {@link RestClient}. Endpoints, timeouts,
 * retries and credentials are supplied by {@link ConnectorProperties#getCard()}.
 * Point {@code payment.card.base-url} at a real acquirer sandbox to go live.
 */
@Component
public class CardHttpConnector implements PaymentConnector {

    private static final Logger log = LoggerFactory.getLogger(CardHttpConnector.class);

    private final RestClient client;
    private final int maxRetries;

    public CardHttpConnector(ConnectorProperties props, RestClientFactory factory) {
        this.client = factory.build(props.getCard());
        this.maxRetries = props.getCard().getMaxRetries();
    }

    @Override
    public String connectorId() {
        return "card-gateway";
    }

    @Override
    public AuthorizeResult authorize(AuthorizeCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intentId", cmd.intentId());
        body.put("instrumentToken", cmd.instrumentToken());
        body.put("amountMinor", cmd.amountMinor());
        body.put("currency", cmd.currency());
        body.put("statementDescriptor", cmd.statementDescriptor());
        body.put("threeDs", cmd.requireThreeDs());
        try {
            // Authorizations are NOT auto-retried on network error to avoid double holds.
            ConnectorResponse r = client.post().uri("/v1/authorizations")
                    .body(body).retrieve().body(ConnectorResponse.class);
            AttemptOutcome outcome = mapAuthOutcome(r);
            return new AuthorizeResult(
                    outcome,
                    firstNonBlank(r == null ? null : r.reference(), cmd.intentId()),
                    r == null ? null : r.authCode(),
                    r == null ? null : r.rrn(),
                    r == null ? null : r.arn(),
                    r == null ? null : r.eci(),
                    r == null ? null : r.avsResult(),
                    r == null ? null : r.cvvResult(),
                    r == null ? null : r.declineCode(),
                    r == null ? null : r.mappedDeclineCode(),
                    r == null ? null : r.acsUrl(),
                    r == null ? null : r.expiresAt());
        } catch (RestClientResponseException e) {
            log.warn("Card authorize declined by gateway: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            AttemptOutcome outcome = e.getStatusCode().is4xxClientError()
                    ? AttemptOutcome.HARD_DECLINE : AttemptOutcome.ERROR;
            return declined(cmd.intentId(), outcome, "GATEWAY_" + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            log.warn("Card authorize timed out: {}", e.getMessage());
            return declined(cmd.intentId(), AttemptOutcome.TIMEOUT, "NETWORK_TIMEOUT");
        } catch (Exception e) {
            log.error("Card authorize failed", e);
            return declined(cmd.intentId(), AttemptOutcome.ERROR, "CONNECTOR_ERROR");
        }
    }

    @Override
    public CaptureResult capture(CaptureCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authorizationRef", cmd.authorizationRef());
        body.put("amountMinor", cmd.amountMinor());
        body.put("currency", cmd.currency());
        body.put("finalCapture", cmd.finalCapture());
        try {
            ConnectorResponse r = withRetries(() -> client.post().uri("/v1/captures")
                    .body(body).retrieve().body(ConnectorResponse.class));
            boolean ok = r == null || isSuccess(r.status());
            return new CaptureResult(ok, r == null ? null : firstNonBlank(r.reference(), r.providerRef()),
                    r == null ? null : r.declineCode());
        } catch (RestClientResponseException e) {
            return new CaptureResult(false, null, "GATEWAY_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Card capture failed", e);
            return new CaptureResult(false, null, "CONNECTOR_ERROR");
        }
    }

    @Override
    public VoidResult voidAuthorization(VoidCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authorizationRef", cmd.authorizationRef());
        body.put("amountMinor", cmd.amountMinor());
        try {
            ConnectorResponse r = withRetries(() -> client.post().uri("/v1/voids")
                    .body(body).retrieve().body(ConnectorResponse.class));
            boolean ok = r == null || isSuccess(r.status());
            return new VoidResult(ok, r == null ? null : firstNonBlank(r.reference(), r.providerRef()));
        } catch (Exception e) {
            log.error("Card void failed", e);
            return new VoidResult(false, null);
        }
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("captureRef", cmd.captureRef());
        body.put("amountMinor", cmd.amountMinor());
        body.put("currency", cmd.currency());
        body.put("reason", cmd.reason());
        try {
            ConnectorResponse r = withRetries(() -> client.post().uri("/v1/refunds")
                    .body(body).retrieve().body(ConnectorResponse.class));
            boolean ok = r == null || isSuccess(r.status());
            return new RefundResult(ok, r == null ? null : firstNonBlank(r.reference(), r.providerRef()),
                    r == null ? null : r.declineCode());
        } catch (RestClientResponseException e) {
            return new RefundResult(false, null, "GATEWAY_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Card refund failed", e);
            return new RefundResult(false, null, "CONNECTOR_ERROR");
        }
    }

    private AttemptOutcome mapAuthOutcome(ConnectorResponse r) {
        if (r == null || r.status() == null) return AttemptOutcome.APPROVED;
        String s = r.status().toUpperCase();
        return switch (s) {
            case "APPROVED", "AUTHORIZED", "SUCCESS" -> AttemptOutcome.APPROVED;
            case "PENDING", "CHALLENGE", "REQUIRES_ACTION" -> AttemptOutcome.PENDING;
            case "SOFT_DECLINE" -> AttemptOutcome.SOFT_DECLINE;
            case "HARD_DECLINE", "DECLINED" -> AttemptOutcome.HARD_DECLINE;
            default -> AttemptOutcome.ERROR;
        };
    }

    private boolean isSuccess(String status) {
        if (status == null) return true;
        String s = status.toUpperCase();
        return s.equals("SUCCESS") || s.equals("CAPTURED") || s.equals("VOIDED")
                || s.equals("REFUNDED") || s.equals("APPROVED") || s.equals("OK");
    }

    private AuthorizeResult declined(String ref, AttemptOutcome outcome, String code) {
        return new AuthorizeResult(outcome, ref, null, null, null, null, null, null, code, code, null, null);
    }

    private <T> T withRetries(Supplier<T> call) {
        int attempts = 0;
        RuntimeException last = null;
        while (attempts <= maxRetries) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                last = e;
                attempts++;
                log.warn("Transient connector error, retry {}/{}", attempts, maxRetries);
            }
        }
        throw last;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
