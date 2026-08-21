package com.paymentprocessor.paymentservice.service.commands;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;
import com.paymentprocessor.paymentservice.events.PaymentEvent;
import com.paymentprocessor.paymentservice.events.PaymentEventPublisher;
import com.paymentprocessor.paymentservice.repository.PaymentIntentRepository;
import com.paymentprocessor.paymentservice.statemachine.PaymentStateMachine;

/**
 * Confirms a payment intent, typically after partial captures are complete and the
 * merchant wants to finalize the payment.
 */
@Component
public class ConfirmCommandHandler {

    private final PaymentIntentRepository intentRepo;
    private final PaymentStateMachine stateMachine;
    private final PaymentEventPublisher eventPublisher;

    public ConfirmCommandHandler(PaymentIntentRepository intentRepo,
                                PaymentStateMachine stateMachine,
                                PaymentEventPublisher eventPublisher) {
        this.intentRepo = intentRepo;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentIntent confirm(PaymentIntent intent) {
        stateMachine.transition(intent, PaymentStatus.CONFIRMED, null);
        intentRepo.save(intent);
        eventPublisher.publish(new PaymentEvent.PaymentConfirmedEvent(intent));
        return intent;
    }
}
