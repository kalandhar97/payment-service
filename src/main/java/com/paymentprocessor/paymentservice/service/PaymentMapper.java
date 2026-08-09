package com.paymentprocessor.paymentservice.service;

import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.dto.PaymentResponse;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.entity.ThreeDsSession;
import com.paymentprocessor.paymentservice.repository.ThreeDsSessionRepository;

/** Maps the {@link PaymentIntent} aggregate to its public API representation. */
@Component
public class PaymentMapper {

    private final ThreeDsSessionRepository threeDsRepo;

    public PaymentMapper(ThreeDsSessionRepository threeDsRepo) {
        this.threeDsRepo = threeDsRepo;
    }

    public PaymentResponse toResponse(PaymentIntent i) {
        return toResponse(i, null);
    }

    public PaymentResponse toResponse(PaymentIntent i, String upiIntentUri) {
        String acsUrl = null;
        if ("3DS_PENDING".equals(i.getSubStatus())) {
            acsUrl = threeDsRepo.findByIntentId(i.getId()).stream()
                    .reduce((a, b) -> b) // latest
                    .map(ThreeDsSession::getAcsUrl)
                    .orElse(null);
        }
        return new PaymentResponse(
                i.getId(), i.getMerchantId(), i.getCustomerId(), i.getPaymentMethod(),
                i.getStatus(), i.getSubStatus(), i.getAmountMinor(), i.getCurrency(),
                i.getCaptureMethod(), i.getConnectorId(),
                i.getAuthorizedMinor(), i.getCapturedMinor(), i.getRefundedMinor(),
                i.getStatementDescriptor(), i.getDescription(),
                acsUrl, upiIntentUri, i.getCreatedAt(), i.getUpdatedAt());
    }
}
