package com.paymentprocessor.paymentservice.support;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.dto.CaptureRequest;
import com.paymentprocessor.paymentservice.dto.RefundRequest;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.exception.PaymentValidationException;

/**
 * Encapsulates monetary calculations for captures and refunds so the command handlers
 * do not duplicate arithmetic or error messages.
 */
@Component
public class AmountCalculator {

    public long calculateCaptureAmount(PaymentIntent intent, CaptureRequest req) {
        long remaining = remainingCaptureAmount(intent);
        long amount = req != null && req.amountMinor() != null ? req.amountMinor() : remaining;
        if (amount <= 0 || amount > remaining) {
            throw new PaymentValidationException(
                    "Capture amount " + amount + " must be > 0 and <= remaining " + remaining);
        }
        return amount;
    }

    public boolean isFinalCapture(PaymentIntent intent, CaptureRequest req, long captureAmount) {
        return (req != null && Boolean.TRUE.equals(req.finalCapture()))
                || captureAmount >= remainingCaptureAmount(intent);
    }

    public long calculateRefundAmount(PaymentIntent intent, RefundRequest req) {
        long refundable = refundableAmount(intent);
        long amount = req != null && req.amountMinor() != null ? req.amountMinor() : refundable;
        if (amount <= 0 || amount > refundable) {
            throw new PaymentValidationException(
                    "Refund amount " + amount + " must be > 0 and <= refundable " + refundable);
        }
        return amount;
    }

    public long remainingCaptureAmount(PaymentIntent intent) {
        return Math.max(0, nullToZero(intent.getAuthorizedMinor()) - nullToZero(intent.getCapturedMinor()));
    }

    public long refundableAmount(PaymentIntent intent) {
        return Math.max(0, nullToZero(intent.getCapturedMinor()) - nullToZero(intent.getRefundedMinor()));
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
