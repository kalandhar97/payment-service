package com.paymentprocessor.paymentservice.connector;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.paymentprocessor.paymentservice.config.ConnectorProperties;
import com.paymentprocessor.paymentservice.config.HttpClientConfig.WebClientFactory;

/**
 * Thin client over the real fraud-service ({@code POST /api/fraud/evaluate}).
 * Evaluated before every card authorization. If risk scoring is disabled or the
 * service is unreachable/slow the client fails open (approve) so a risk outage
 * does not halt all payments; tune this policy per your risk appetite.
 */
@Component
public class FraudClient {

    private static final Logger log = LoggerFactory.getLogger(FraudClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient client;
    private final boolean enabled;

    public FraudClient(ConnectorProperties props, WebClientFactory factory) {
        this.client = factory.build(props.getFraud());
        this.enabled = props.getFraud().isEnabled();
    }

    public FraudDecision evaluate(String intentId, String merchantId, long amountMinor,
                                  String currency, String instrumentToken) {
        if (!enabled) {
            return FraudDecision.approve();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intentId", intentId);
        body.put("merchantId", merchantId);
        // fraud-service's amount field is denominated in major units (e.g. 54.00 == $54).
        body.put("amount", BigDecimal.valueOf(amountMinor, 2));
        body.put("currency", currency);
        body.put("cardFingerprint", instrumentToken);
        body.put("timestamp", Instant.now().toString());

        try {
            FraudDecisionResponse r = client.post().uri("/api/fraud/evaluate")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(FraudDecisionResponse.class)
                    .timeout(TIMEOUT)
                    .block();
            if (r == null || r.decision() == null) {
                return FraudDecision.approve();
            }
            return switch (r.decision().toUpperCase()) {
                case "APPROVE" -> new FraudDecision(true, false, r.riskScore(), reason(r));
                case "CHALLENGE" -> new FraudDecision(true, true, r.riskScore(), reason(r));
                // REVIEW/DECLINE/ESCALATE cannot proceed through a fully automated
                // authorization flow; block until a human/queue clears it.
                default -> new FraudDecision(false, false, r.riskScore(), reason(r));
            };
        } catch (Exception e) {
            log.warn("Fraud evaluation unavailable, failing open: {}", e.getMessage());
            return FraudDecision.approve();
        }
    }

    private static String reason(FraudDecisionResponse r) {
        return r.reasonCode() != null ? r.reasonCode() : (r.recommendedAction() != null ? r.recommendedAction() : "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FraudDecisionResponse(String intentId, String decision, int riskScore,
                                         String reasonCode, String recommendedAction) {
    }
}
