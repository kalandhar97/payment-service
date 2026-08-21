package com.paymentprocessor.paymentservice.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusCheckerTest {

    private PaymentStatusChecker checker;
    private PaymentIntent intent;

    @BeforeEach
    void setUp() {
        checker = new PaymentStatusChecker();
        intent = new PaymentIntent();
    }

    @Test
    void isInState_matchesSingleState_returnsTrue() {
        intent.setStatus(PaymentStatus.AUTHORIZED.name());
        assertThat(checker.isInState(intent, PaymentStatus.AUTHORIZED)).isTrue();
    }

    @Test
    void isInState_matchesAnyState_returnsTrue() {
        intent.setStatus(PaymentStatus.CAPTURED.name());
        assertThat(checker.isInState(intent, PaymentStatus.CAPTURED, PaymentStatus.CONFIRMED)).isTrue();
    }

    @Test
    void isInState_noMatch_returnsFalse() {
        intent.setStatus(PaymentStatus.CREATED.name());
        assertThat(checker.isInState(intent, PaymentStatus.AUTHORIZED)).isFalse();
    }

    @Test
    void isInState_nullStatus_returnsFalse() {
        intent.setStatus(null);
        assertThat(checker.isInState(intent, PaymentStatus.CREATED)).isFalse();
    }
}
