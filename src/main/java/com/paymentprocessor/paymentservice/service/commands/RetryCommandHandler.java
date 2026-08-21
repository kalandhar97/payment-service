package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;

/**
 * Retries a failed payment intent by moving it back to CREATED and re-running authorization.
 */
@Component
public class RetryCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final PaymentStateMachine stateMachine;
    private final AuthorizeCommandHandler authorizeHandler;

    public RetryCommandHandler(PaymentIntentRepository intentRepo,
                              PaymentStateMachine stateMachine,
                              AuthorizeCommandHandler authorizeHandler) {
        this.intentRepo = intentRepo;
        this.stateMachine = stateMachine;
        this.authorizeHandler = authorizeHandler;
    }

    @Transactional
    public PaymentIntent retry(PaymentIntent intent) {
        if (!PaymentStatus.FAILED.name().equals(intent.getStatus())) {
            throw new PaymentValidationException("Only FAILED payments can be retried");
        }
        stateMachine.transition(intent, PaymentStatus.CREATED, null);
        intentRepo.save(intent);
        return authorizeHandler.authorize(intent);
    }
}
