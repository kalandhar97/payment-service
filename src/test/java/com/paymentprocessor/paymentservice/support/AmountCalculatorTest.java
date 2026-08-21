package com.paymentprocessor.paymentservice.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paymentprocessor.paymentservice.dto.CaptureRequest;
import com.paymentprocessor.paymentservice.dto.RefundRequest;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountCalculatorTest {

    private AmountCalculator calculator;
    private PaymentIntent intent;

    @BeforeEach
    void setUp() {
        calculator = new AmountCalculator();
        intent = new PaymentIntent();
        intent.setAuthorizedMinor(1000L);
        intent.setCapturedMinor(200L);
        intent.setRefundedMinor(50L);
    }

    @Test
    void calculateCaptureAmount_withNullRequest_returnsRemaining() {
        long amount = calculator.calculateCaptureAmount(intent, null);
        assertThat(amount).isEqualTo(800L);
    }

    @Test
    void calculateCaptureAmount_withRequestedAmount_returnsRequestedAmount() {
        long amount = calculator.calculateCaptureAmount(intent, new CaptureRequest(500L, false));
        assertThat(amount).isEqualTo(500L);
    }

    @Test
    void calculateCaptureAmount_withZeroAmount_throws() {
        assertThatThrownBy(() -> calculator.calculateCaptureAmount(intent, new CaptureRequest(0L, false)))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("must be > 0");
    }

    @Test
    void calculateCaptureAmount_withAmountExceedingRemaining_throws() {
        assertThatThrownBy(() -> calculator.calculateCaptureAmount(intent, new CaptureRequest(1001L, false)))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("<= remaining 800");
    }

    @Test
    void isFinalCapture_whenFinalFlagTrue_returnsTrue() {
        assertThat(calculator.isFinalCapture(intent, new CaptureRequest(100L, true), 100L)).isTrue();
    }

    @Test
    void isFinalCapture_whenAmountEqualsRemaining_returnsTrue() {
        assertThat(calculator.isFinalCapture(intent, new CaptureRequest(800L, false), 800L)).isTrue();
    }

    @Test
    void calculateRefundAmount_withNullRequest_returnsRefundable() {
        long amount = calculator.calculateRefundAmount(intent, null);
        assertThat(amount).isEqualTo(150L);
    }

    @Test
    void calculateRefundAmount_withRequestedAmount_returnsRequestedAmount() {
        long amount = calculator.calculateRefundAmount(intent, new RefundRequest(100L, "reason"));
        assertThat(amount).isEqualTo(100L);
    }

    @Test
    void calculateRefundAmount_withAmountExceedingRefundable_throws() {
        assertThatThrownBy(() -> calculator.calculateRefundAmount(intent, new RefundRequest(200L, null)))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("<= refundable 150");
    }

    @Test
    void remainingCaptureAmount_handlesNullTotals() {
        intent.setAuthorizedMinor(null);
        intent.setCapturedMinor(null);
        assertThat(calculator.remainingCaptureAmount(intent)).isZero();
    }
}
