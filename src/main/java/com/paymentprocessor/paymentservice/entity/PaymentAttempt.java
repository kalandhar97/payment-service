package com.paymentprocessor.paymentservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One authorization attempt against a connector. Retries create a new attempt
 * with an incremented {@link #attemptNo}, preserving a full record of every try
 * for reconciliation and analytics.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "intent_id", nullable = false)
    private String intentId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "connector_id")
    private String connectorId;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "network_decline_code")
    private String networkDeclineCode;

    @Column(name = "mapped_decline_code")
    private String mappedDeclineCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getConnectorId() { return connectorId; }
    public void setConnectorId(String connectorId) { this.connectorId = connectorId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getNetworkDeclineCode() { return networkDeclineCode; }
    public void setNetworkDeclineCode(String networkDeclineCode) { this.networkDeclineCode = networkDeclineCode; }
    public String getMappedDeclineCode() { return mappedDeclineCode; }
    public void setMappedDeclineCode(String mappedDeclineCode) { this.mappedDeclineCode = mappedDeclineCode; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
