package com.paymentprocessor.paymentservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A successful hold of funds returned by the connector. Carries network
 * artefacts (auth code, RRN, ARN, ECI, 3DS session) needed for capture,
 * reconciliation and dispute evidence, plus the {@link #expiresAt} deadline
 * used by the expiry job.
 */
@Entity
@Table(name = "authorizations")
public class Authorization {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "intent_id", nullable = false)
    private String intentId;

    @Column(name = "attempt_id")
    private String attemptId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "auth_code")
    private String authCode;

    @Column(name = "network_txn_id")
    private String networkTxnId;

    @Column(name = "rrn")
    private String rrn;

    @Column(name = "arn")
    private String arn;

    @Column(name = "avs_result")
    private String avsResult;

    @Column(name = "cvv_result")
    private String cvvResult;

    @Column(name = "eci")
    private String eci;

    @Column(name = "three_ds_session_id")
    private String threeDsSessionId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
    public String getNetworkTxnId() { return networkTxnId; }
    public void setNetworkTxnId(String networkTxnId) { this.networkTxnId = networkTxnId; }
    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public String getArn() { return arn; }
    public void setArn(String arn) { this.arn = arn; }
    public String getAvsResult() { return avsResult; }
    public void setAvsResult(String avsResult) { this.avsResult = avsResult; }
    public String getCvvResult() { return cvvResult; }
    public void setCvvResult(String cvvResult) { this.cvvResult = cvvResult; }
    public String getEci() { return eci; }
    public void setEci(String eci) { this.eci = eci; }
    public String getThreeDsSessionId() { return threeDsSessionId; }
    public void setThreeDsSessionId(String threeDsSessionId) { this.threeDsSessionId = threeDsSessionId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
