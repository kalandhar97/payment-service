package com.paymentprocessor.paymentservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The aggregate root of the service. A {@code PaymentIntent} tracks the running
 * financial totals (authorized / captured / refunded, all in minor units) and the
 * current lifecycle {@link com.paymentprocessor.paymentservice.enums.PaymentStatus}.
 * Optimistic locking via {@link #version} guards against concurrent state changes.
 */
@Entity
@Table(name = "payment_intents")
public class PaymentIntent {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "instrument_token")
    private String instrumentToken;

    @Column(name = "payer_vpa")
    private String payerVpa;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "sub_status")
    private String subStatus;

    @Column(name = "capture_method", nullable = false)
    private String captureMethod;

    @Column(name = "connector_id")
    private String connectorId;

    @Column(name = "limit_reservation_id")
    private String limitReservationId;

    @Column(name = "authorized_minor")
    private Long authorizedMinor = 0L;

    @Column(name = "captured_minor")
    private Long capturedMinor = 0L;

    @Column(name = "refunded_minor")
    private Long refundedMinor = 0L;

    @Column(name = "statement_descriptor")
    private String statementDescriptor;

    @Column(name = "description")
    private String description;

    @Column(name = "metadata", length = 4000)
    private String metadata;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (authorizedMinor == null) authorizedMinor = 0L;
        if (capturedMinor == null) capturedMinor = 0L;
        if (refundedMinor == null) refundedMinor = 0L;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getInstrumentToken() { return instrumentToken; }
    public void setInstrumentToken(String instrumentToken) { this.instrumentToken = instrumentToken; }
    public String getPayerVpa() { return payerVpa; }
    public void setPayerVpa(String payerVpa) { this.payerVpa = payerVpa; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubStatus() { return subStatus; }
    public void setSubStatus(String subStatus) { this.subStatus = subStatus; }
    public String getCaptureMethod() { return captureMethod; }
    public void setCaptureMethod(String captureMethod) { this.captureMethod = captureMethod; }
    public String getConnectorId() { return connectorId; }
    public void setConnectorId(String connectorId) { this.connectorId = connectorId; }
    public String getLimitReservationId() { return limitReservationId; }
    public void setLimitReservationId(String limitReservationId) { this.limitReservationId = limitReservationId; }
    public Long getAuthorizedMinor() { return authorizedMinor; }
    public void setAuthorizedMinor(Long authorizedMinor) { this.authorizedMinor = authorizedMinor; }
    public Long getCapturedMinor() { return capturedMinor; }
    public void setCapturedMinor(Long capturedMinor) { this.capturedMinor = capturedMinor; }
    public Long getRefundedMinor() { return refundedMinor; }
    public void setRefundedMinor(Long refundedMinor) { this.refundedMinor = refundedMinor; }
    public String getStatementDescriptor() { return statementDescriptor; }
    public void setStatementDescriptor(String statementDescriptor) { this.statementDescriptor = statementDescriptor; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
