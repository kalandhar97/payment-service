package com.paymentprocessor.paymentservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * State of a 3-D Secure authentication challenge for a card intent. Captures the
 * ACS URL the shopper is redirected to and the resulting liability-shift /
 * exemption outcome that feeds into authorization.
 */
@Entity
@Table(name = "three_ds_sessions")
public class ThreeDsSession {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "intent_id", nullable = false)
    private String intentId;

    @Column(name = "version")
    private String version;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "acs_url")
    private String acsUrl;

    @Column(name = "ds_trans_id")
    private String dsTransId;

    @Column(name = "cavv_ref")
    private String cavvRef;

    @Column(name = "liability_shift")
    private Boolean liabilityShift;

    @Column(name = "exemption_applied")
    private Boolean exemptionApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIntentId() { return intentId; }
    public void setIntentId(String intentId) { this.intentId = intentId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAcsUrl() { return acsUrl; }
    public void setAcsUrl(String acsUrl) { this.acsUrl = acsUrl; }
    public String getDsTransId() { return dsTransId; }
    public void setDsTransId(String dsTransId) { this.dsTransId = dsTransId; }
    public String getCavvRef() { return cavvRef; }
    public void setCavvRef(String cavvRef) { this.cavvRef = cavvRef; }
    public Boolean getLiabilityShift() { return liabilityShift; }
    public void setLiabilityShift(Boolean liabilityShift) { this.liabilityShift = liabilityShift; }
    public Boolean getExemptionApplied() { return exemptionApplied; }
    public void setExemptionApplied(Boolean exemptionApplied) { this.exemptionApplied = exemptionApplied; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
