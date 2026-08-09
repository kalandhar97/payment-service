package com.paymentprocessor.paymentservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Records the result of a mutating request so that a retried request carrying the
 * same {@code Idempotency-Key} (scoped to the merchant) returns the original
 * response instead of executing again. {@link #requestHash} lets us detect a key
 * being reused with a different payload.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyId.class)
public class IdempotencyKey {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String key;

    @Column(name = "request_hash")
    private byte[] requestHash;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", length = 8000)
    private String responseBody;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public byte[] getRequestHash() { return requestHash; }
    public void setRequestHash(byte[] requestHash) { this.requestHash = requestHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
