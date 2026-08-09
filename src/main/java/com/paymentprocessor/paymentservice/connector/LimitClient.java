package com.paymentprocessor.paymentservice.connector;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.paymentprocessor.paymentservice.config.ConnectorProperties;
import com.paymentprocessor.paymentservice.config.HttpClientConfig.WebClientFactory;

/**
 * Thin client over the real limit-service. Reserves capacity before
 * authorization ({@code POST /api/v1/reservations}), commits it on successful
 * capture ({@code POST /api/v1/reservations/{id}/commit}), and releases it on
 * void/failure ({@code POST /api/v1/reservations/{id}/release}).
 *
 * <p>Reservation is a hard control, not an advisory risk signal, so unlike
 * {@link FraudClient} this client does <b>not</b> fail open: an unreachable
 * limit-service surfaces as a decline (see {@link LimitDecisionResult}) so the
 * caller can fail the payment/attempt and let the merchant retry, rather than
 * silently letting spend bypass velocity/exposure limits during an outage.
 */
@Component
public class LimitClient {

    private static final Logger log = LoggerFactory.getLogger(LimitClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient client;
    private final boolean enabled;

    public LimitClient(ConnectorProperties props, WebClientFactory factory) {
        this.client = factory.build(props.getLimit());
        this.enabled = props.getLimit().isEnabled();
    }

    /** Evaluates and reserves capacity for a prospective transaction. */
    public LimitDecisionResult reserve(String transactionId, String merchantId, String customerId,
                                       long amountMinor, String currency) {
        if (!enabled) {
            return new LimitDecisionResult(true, false, null, "RESERVED", "LIMIT_CHECK_DISABLED");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionId", transactionId);
        body.put("merchantId", merchantId);
        body.put("customerId", customerId);
        body.put("currency", currency);
        body.put("amount", BigDecimal.valueOf(amountMinor, 2));
        body.put("idempotencyKey", transactionId);

        try {
            ReservationResponse r = client.post().uri("/api/v1/reservations")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(ReservationResponse.class)
                    .timeout(TIMEOUT)
                    .block();
            if (r == null) {
                return LimitDecisionResult.unavailable("EMPTY_RESPONSE");
            }
            return new LimitDecisionResult(true, false, r.reservationId(), r.status(), null);
        } catch (WebClientResponseException.UnprocessableEntity e) {
            // 422: a hard limit was exceeded; a genuine decline, not an outage.
            return new LimitDecisionResult(false, false, null, "DECLINED", "LIMIT_EXCEEDED");
        } catch (Exception e) {
            log.warn("Limit-service unavailable, failing closed (declining reservation): {}", e.getMessage());
            return LimitDecisionResult.unavailable(e.getMessage());
        }
    }

    /** Commits (captures) a previously reserved amount. Best-effort: if limit-service is
     *  unreachable the reservation is left RESERVED and will auto-expire/release on its own
     *  schedule rather than blocking an already-successful capture. */
    public void commit(String reservationId, long capturedAmountMinor) {
        if (!enabled || reservationId == null) {
            return;
        }
        Map<String, Object> body = Map.of("capturedAmount", BigDecimal.valueOf(capturedAmountMinor, 2));
        try {
            client.post().uri("/api/v1/reservations/{id}/commit", reservationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(ReservationResponse.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("Limit reservation commit failed for {}: {}", reservationId, e.getMessage());
        }
    }

    /** Releases a previously reserved amount (void/decline/cancellation). Best-effort. */
    public void release(String reservationId, String reason) {
        if (!enabled || reservationId == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        try {
            client.post().uri("/api/v1/reservations/{id}/release", reservationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(ReservationResponse.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("Limit reservation release failed for {}: {}", reservationId, e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReservationResponse(String reservationId, String transactionId, String status,
                                       String currency, BigDecimal reservedAmount, BigDecimal committedAmount) {
    }
}
