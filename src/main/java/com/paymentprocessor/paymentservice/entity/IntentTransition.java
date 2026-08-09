package com.paymentprocessor.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The static allow-list of valid state-machine edges. A row {@code (fromStatus,
 * toStatus)} means the transition is permitted; the absence of a row means it is
 * rejected. Seeded at startup (see data.sql) and consulted by the state machine.
 */
@Entity
@Table(name = "intent_transitions")
@IdClass(IntentTransitionId.class)
public class IntentTransition {

    @Id
    @Column(name = "from_status", nullable = false)
    private String fromStatus;

    @Id
    @Column(name = "to_status", nullable = false)
    private String toStatus;

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
}
