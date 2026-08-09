package com.paymentprocessor.paymentservice.statemachine;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.exception.InvalidStateTransitionException;
import com.paymentprocessor.paymentservice.repository.IntentTransitionRepository;

/**
 * Enforces the payment lifecycle. Every state change is validated against the
 * {@code intent_transitions} allow-list so an illegal transition (e.g. capturing
 * a failed payment) is impossible regardless of which code path requests it.
 */
@Component
public class PaymentStateMachine {

    private final IntentTransitionRepository transitions;

    public PaymentStateMachine(IntentTransitionRepository transitions) {
        this.transitions = transitions;
    }

    /** Throws {@link InvalidStateTransitionException} if the edge is not allowed. */
    public void assertCanTransition(String from, PaymentStatus to) {
        if (from == null || !transitions.existsByFromStatusAndToStatus(from, to.name())) {
            throw new InvalidStateTransitionException(from, to.name());
        }
    }

    /** Validates and applies a transition, mutating the intent's status/sub-status. */
    public void transition(PaymentIntent intent, PaymentStatus to, String subStatus) {
        assertCanTransition(intent.getStatus(), to);
        intent.setStatus(to.name());
        intent.setSubStatus(subStatus);
    }

    public void transition(PaymentIntent intent, PaymentStatus to) {
        transition(intent, to, null);
    }
}
