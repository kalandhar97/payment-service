package com.paymentprocessor.paymentservice.support;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.entity.PaymentAttempt;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.AttemptOutcome;
import com.paymentprocessor.paymentservice.repository.PaymentAttemptRepository;

/** Builds new {@link PaymentAttempt} entities with an incremented attempt number. */
@Component
public class PaymentAttemptFactory {

    private final IdGenerator idGenerator;
    private final PaymentAttemptRepository attemptRepo;

    public PaymentAttemptFactory(IdGenerator idGenerator, PaymentAttemptRepository attemptRepo) {
        this.idGenerator = idGenerator;
        this.attemptRepo = attemptRepo;
    }

    public PaymentAttempt newAttempt(PaymentIntent intent, String connectorId) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(idGenerator.newId());
        attempt.setIntentId(intent.getId());
        attempt.setAttemptNo((int) attemptRepo.countByIntentId(intent.getId()) + 1);
        attempt.setConnectorId(connectorId);
        attempt.setOutcome(AttemptOutcome.PENDING.name());
        return attempt;
    }
}
