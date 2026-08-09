package com.paymentprocessor.paymentservice.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key (fromStatus, toStatus) for {@link IntentTransition}. */
public class IntentTransitionId implements Serializable {

    private String fromStatus;
    private String toStatus;

    public IntentTransitionId() { }

    public IntentTransitionId(String fromStatus, String toStatus) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntentTransitionId that = (IntentTransitionId) o;
        return Objects.equals(fromStatus, that.fromStatus) && Objects.equals(toStatus, that.toStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromStatus, toStatus);
    }
}
