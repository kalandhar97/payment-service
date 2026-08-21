package com.paymentprocessor.paymentservice.support;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paymentprocessor.paymentservice.dto.CreatePaymentRequest;
import com.paymentprocessor.paymentservice.enums.CaptureMethod;
import com.paymentprocessor.paymentservice.enums.PaymentMethod;
import com.paymentprocessor.paymentservice.enums.UpiFlow;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMethodResolverTest {

    private PaymentMethodResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PaymentMethodResolver();
    }

    @Test
    void resolveMethod_cardUpperCase_returnsCard() {
        assertThat(resolver.resolveMethod("CARD")).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    void resolveMethod_upiLowerCase_returnsUpi() {
        assertThat(resolver.resolveMethod("upi")).isEqualTo(PaymentMethod.UPI);
    }

    @Test
    void resolveMethod_unknown_throws() {
        assertThatThrownBy(() -> resolver.resolveMethod("CASH"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Unsupported paymentMethod");
    }

    @Test
    void resolveCaptureMethod_forUpi_returnsAutomatic() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "merchant", null, "UPI", null, null, null,
                100L, "INR", "MANUAL", null, null, null);
        assertThat(resolver.resolveCaptureMethod(req, PaymentMethod.UPI)).isEqualTo(CaptureMethod.AUTOMATIC);
    }

    @Test
    void resolveCaptureMethod_forCardWithBlankDefaultsToAutomatic() {
        CreatePaymentRequest req = cardRequest(null);
        assertThat(resolver.resolveCaptureMethod(req, PaymentMethod.CARD)).isEqualTo(CaptureMethod.AUTOMATIC);
    }

    @Test
    void resolveCaptureMethod_forCardManual_returnsManual() {
        CreatePaymentRequest req = cardRequest("manual");
        assertThat(resolver.resolveCaptureMethod(req, PaymentMethod.CARD)).isEqualTo(CaptureMethod.MANUAL);
    }

    @Test
    void resolveCaptureMethod_unknown_throws() {
        CreatePaymentRequest req = cardRequest("LATER");
        assertThatThrownBy(() -> resolver.resolveCaptureMethod(req, PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Unknown captureMethod");
    }

    @Test
    void resolveUpiFlow_nullDefaultsToCollect() {
        assertThat(resolver.resolveUpiFlow(null)).isEqualTo(UpiFlow.COLLECT);
    }

    @Test
    void resolveUpiFlow_intent_returnsIntent() {
        assertThat(resolver.resolveUpiFlow("INTENT")).isEqualTo(UpiFlow.INTENT);
    }

    @Test
    void resolveUpiFlow_unknown_throws() {
        assertThatThrownBy(() -> resolver.resolveUpiFlow("SMS"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Unknown upiFlow");
    }

    private CreatePaymentRequest cardRequest(String captureMethod) {
        return new CreatePaymentRequest(
                "merchant", null, "CARD", "token", null, null,
                100L, "USD", captureMethod, null, null, Map.of());
    }
}
