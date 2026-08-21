package com.paymentprocessor.paymentservice.support;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;

/** Reusable status checks that avoid string comparisons scattered through handlers. */
@Component
public class PaymentStatusChecker {

    public boolean isInState(PaymentIntent intent, PaymentStatus... states) {
        return isOneOf(intent.getStatus(), states);
    }

    public boolean isInState(String status, PaymentStatus... states) {
        return isOneOf(status, states);
    }

    private boolean isOneOf(String status, PaymentStatus... options) {
        if (status == null) {
            return false;
        }
        for (PaymentStatus s : options) {
            if (s.name().equals(status)) {
                return true;
            }
        }
        return false;
    }
}
