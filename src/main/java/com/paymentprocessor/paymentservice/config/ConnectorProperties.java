package com.paymentprocessor.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for every outbound integration the payment service
 * talks to. Bound from the {@code payment.*} namespace in application.yml so the
 * same artifact can target sandbox or production gateways without code changes.
 */
@ConfigurationProperties(prefix = "payment")
public class ConnectorProperties {

    private Endpoint card = new Endpoint();
    private Upi upi = new Upi();
    private Endpoint fraud = new Endpoint();
    private Endpoint vault = new Endpoint();
    private Endpoint limit = new Endpoint();
    private Outbox outbox = new Outbox();

    public Endpoint getCard() { return card; }
    public void setCard(Endpoint card) { this.card = card; }

    public Upi getUpi() { return upi; }
    public void setUpi(Upi upi) { this.upi = upi; }

    public Endpoint getFraud() { return fraud; }
    public void setFraud(Endpoint fraud) { this.fraud = fraud; }

    public Endpoint getVault() { return vault; }
    public void setVault(Endpoint vault) { this.vault = vault; }

    public Endpoint getLimit() { return limit; }
    public void setLimit(Endpoint limit) { this.limit = limit; }

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    /** Common HTTP endpoint settings shared by all connectors. */
    public static class Endpoint {
        private String baseUrl = "http://localhost:9099";
        private String apiKey = "";
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;
        private int maxRetries = 2;
        private boolean enabled = true;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** UPI adds payee (merchant VPA) identity used to build collect requests / intent links. */
    public static class Upi extends Endpoint {
        private String payeeVpa = "acme@psp";
        private String payeeName = "Acme Payments";
        private int collectExpirySeconds = 300;

        public String getPayeeVpa() { return payeeVpa; }
        public void setPayeeVpa(String payeeVpa) { this.payeeVpa = payeeVpa; }
        public String getPayeeName() { return payeeName; }
        public void setPayeeName(String payeeName) { this.payeeName = payeeName; }
        public int getCollectExpirySeconds() { return collectExpirySeconds; }
        public void setCollectExpirySeconds(int collectExpirySeconds) { this.collectExpirySeconds = collectExpirySeconds; }
    }

    /** Transactional-outbox publisher settings. */
    public static class Outbox {
        private String sinkUrl = "";
        private int batchSize = 100;

        public String getSinkUrl() { return sinkUrl; }
        public void setSinkUrl(String sinkUrl) { this.sinkUrl = sinkUrl; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
