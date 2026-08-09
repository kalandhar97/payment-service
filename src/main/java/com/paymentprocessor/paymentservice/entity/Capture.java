package com.paymentprocessor.paymentservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A capture (full or partial) against an {@link Authorization}. Multiple captures
 * may exist per authorization; {@link #isFinal} marks the capture that releases
 * any remaining uncaptured balance.
 */
@Entity
@Table(name = "captures")
public class Capture {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "authorization_id", nullable = false)
    private String authorizationId;

    @Column(name = "intent_id", nullable = false)
    private String intentId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "is_final")
    private Boolean isFinal;

    @Column(name = "ledger_journal_id")
    private String ledgerJournalId;

    @Column(name = "network_ref")
    private String networkRef;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @PrePersist
    void onCreate() { if (capturedAt == null) capturedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(String authorizationId) { this.authorizationId = authorizationId; }
    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsFinal() { return isFinal; }
    public void setIsFinal(Boolean isFinal) { this.isFinal = isFinal; }
    public String getLedgerJournalId() { return ledgerJournalId; }
    public void setLedgerJournalId(String ledgerJournalId) { this.ledgerJournalId = ledgerJournalId; }
    public String getNetworkRef() { return networkRef; }
    public void setNetworkRef(String networkRef) { this.networkRef = networkRef; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
}
