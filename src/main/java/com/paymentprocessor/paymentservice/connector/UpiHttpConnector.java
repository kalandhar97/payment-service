package com.paymentprocessor.paymentservice.connector;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paymentprocessor.paymentservice.config.ConnectorProperties;
import com.paymentprocessor.paymentservice.config.HttpClientConfig.RestClientFactory;
import com.paymentprocessor.paymentservice.connector.model.RefundCommand;
import com.paymentprocessor.paymentservice.connector.model.RefundResult;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectResult;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentResult;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;

/**
 * UPI PSP connector. Supports the collect flow (push a request to the payer's
 * VPA) and the intent flow (return a {@code upi://pay} deep link / QR the payer
 * scans). Both are asynchronous; the terminal debit result is delivered to the
 * UPI callback endpoint. Configured via {@link ConnectorProperties#getUpi()}.
 */
@Component
public class UpiHttpConnector implements UpiConnector {

    private static final Logger log = LoggerFactory.getLogger(UpiHttpConnector.class);

    private final RestClient client;
    private final ConnectorProperties.Upi props;

    public UpiHttpConnector(ConnectorProperties props, RestClientFactory factory) {
        this.props = props.getUpi();
        this.client = factory.build(props.getUpi());
    }

    @Override
    public String connectorId() {
        return "upi-psp";
    }

    @Override
    public UpiCollectResult collect(UpiCollectCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payerVpa", cmd.payerVpa());
        body.put("payeeVpa", props.getPayeeVpa());
        body.put("payeeName", props.getPayeeName());
        body.put("amountMinor", cmd.amountMinor());
        body.put("currency", cmd.currency());
        body.put("note", cmd.note());
        body.put("transactionRef", cmd.intentId());
        body.put("expirySeconds", cmd.expirySeconds());
        try {
            ConnectorResponse r = client.post().uri("/v1/collect")
                    .body(body).retrieve().body(ConnectorResponse.class);
            String ref = r == null ? cmd.intentId() : firstNonBlank(r.providerRef(), r.reference());
            // A successful collect initiation is PENDING until the payer approves.
            return new UpiCollectResult(AttemptOutcome.PENDING, ref, null);
        } catch (RestClientResponseException e) {
            log.warn("UPI collect rejected: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return new UpiCollectResult(AttemptOutcome.HARD_DECLINE, cmd.intentId(),
                    "PSP_" + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            return new UpiCollectResult(AttemptOutcome.TIMEOUT, cmd.intentId(), "NETWORK_TIMEOUT");
        } catch (Exception e) {
            log.error("UPI collect failed", e);
            return new UpiCollectResult(AttemptOutcome.ERROR, cmd.intentId(), "CONNECTOR_ERROR");
        }
    }

    @Override
    public UpiIntentResult intent(UpiIntentCommand cmd) {
        String intentUri = buildIntentUri(cmd);
        String providerRef = cmd.intentId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payeeVpa", props.getPayeeVpa());
        body.put("payeeName", props.getPayeeName());
        body.put("amountMinor", cmd.amountMinor());
        body.put("currency", cmd.currency());
        body.put("note", cmd.note());
        body.put("transactionRef", cmd.intentId());
        try {
            ConnectorResponse r = client.post().uri("/v1/intent")
                    .body(body).retrieve().body(ConnectorResponse.class);
            if (r != null) {
                if (r.providerRef() != null && !r.providerRef().isBlank()) providerRef = r.providerRef();
                if (r.intentUri() != null && !r.intentUri().isBlank()) intentUri = r.intentUri();
            }
        } catch (Exception e) {
            // The intent link is deterministic and usable even if PSP registration is unavailable.
            log.warn("UPI intent PSP registration unavailable, using locally-built link: {}", e.getMessage());
        }
        return new UpiIntentResult(providerRef, intentUri, intentUri);
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originalRef", cmd.captureRef());
        body.put("amountMinor", cmd.amountMinor());
        body.put("currency", cmd.currency());
        body.put("reason", cmd.reason());
        try {
            ConnectorResponse r = client.post().uri("/v1/refunds")
                    .body(body).retrieve().body(ConnectorResponse.class);
            String ref = r == null ? null : firstNonBlank(r.providerRef(), r.reference());
            boolean ok = r == null || r.status() == null || !r.status().toUpperCase().contains("FAIL");
            return new RefundResult(ok, ref, r == null ? null : r.declineCode());
        } catch (RestClientResponseException e) {
            return new RefundResult(false, null, "PSP_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("UPI refund failed", e);
            return new RefundResult(false, null, "CONNECTOR_ERROR");
        }
    }

    private String buildIntentUri(UpiIntentCommand cmd) {
        String amount = String.format(Locale.US, "%.2f", cmd.amountMinor() / 100.0);
        return "upi://pay?pa=" + enc(props.getPayeeVpa())
                + "&pn=" + enc(props.getPayeeName())
                + "&am=" + enc(amount)
                + "&cu=" + enc(cmd.currency())
                + "&tn=" + enc(cmd.note() == null ? "" : cmd.note())
                + "&tr=" + enc(cmd.intentId());
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
