package com.paymentprocessor.paymentservice.connector;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.paymentprocessor.paymentservice.config.ConnectorProperties;
import com.paymentprocessor.paymentservice.config.HttpClientConfig.WebClientFactory;

/**
 * Thin client over the real tokenization-service ({@code GET /api/instruments/{id}}).
 * Used only to fetch masked, non-sensitive instrument metadata (token/kind) for
 * receipts. The payment service never handles raw PAN/CVV. Returns {@code null}
 * when disabled or unavailable.
 *
 * <p>tokenization-service's {@code InstrumentController} only exposes lookup by
 * its own {@code id} primary key (there is no lookup-by-token endpoint), so the
 * value stored as {@code PaymentIntent.instrumentToken} is expected to be that
 * instrument id.
 */
@Component
public class VaultClient {

    private static final Logger log = LoggerFactory.getLogger(VaultClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient client;
    private final boolean enabled;

    public VaultClient(ConnectorProperties props, WebClientFactory factory) {
        this.client = factory.build(props.getVault());
        this.enabled = props.getVault().isEnabled();
    }

    public InstrumentDetails detokenize(String instrumentToken) {
        if (!enabled || instrumentToken == null || instrumentToken.isBlank()) {
            return null;
        }
        try {
            InstrumentResponse r = client.get().uri("/api/instruments/{id}", instrumentToken)
                    .retrieve()
                    .bodyToMono(InstrumentResponse.class)
                    .timeout(TIMEOUT)
                    .block();
            if (r == null) return null;
            return new InstrumentDetails(r.id(), r.token(), r.kind(), r.scopeMerchantId());
        } catch (Exception e) {
            log.warn("Vault detokenize unavailable: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InstrumentResponse(String id, String token, String kind, String scopeMerchantId) {
    }
}
