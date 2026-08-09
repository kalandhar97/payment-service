package com.paymentprocessor.paymentservice.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key (merchantId, key) for {@link IdempotencyKey}. */
public class IdempotencyKeyId implements Serializable {

    private String merchantId;
    private String key;

    public IdempotencyKeyId() { }

    public IdempotencyKeyId(String merchantId, String key) {
        this.merchantId = merchantId;
        this.key = key;
    }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKeyId that = (IdempotencyKeyId) o;
        return Objects.equals(merchantId, that.merchantId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantId, key);
    }
}
