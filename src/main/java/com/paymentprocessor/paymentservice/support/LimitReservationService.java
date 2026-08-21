package com.paymentprocessor.paymentservice.support;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.connector.LimitClient;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;

/** Wraps limit-service calls with a guard for empty reservation ids. */
@Component
public class LimitReservationService {

    private final LimitClient limitClient;

    public LimitReservationService(LimitClient limitClient) {
        this.limitClient = limitClient;
    }

    public void release(PaymentIntent intent, String reason) {
        String reservationId = intent.getLimitReservationId();
        if (reservationId == null || reservationId.isBlank()) {
            return;
        }
        limitClient.release(reservationId, reason);
    }

    public void commit(PaymentIntent intent) {
        String reservationId = intent.getLimitReservationId();
        if (reservationId == null || reservationId.isBlank()) {
            return;
        }
        limitClient.commit(reservationId, intent.getCapturedMinor());
    }
}
