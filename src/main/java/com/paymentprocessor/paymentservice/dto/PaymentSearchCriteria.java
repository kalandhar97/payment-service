package com.paymentprocessor.paymentservice.dto;

import java.time.Instant;
import java.util.List;

/** Filter criteria for payment search. All fields are optional (AND-combined). */
public record PaymentSearchCriteria(
        String merchantId,
        String customerId,
        List<String> status,
        String currency,
        String paymentMethod,
        Long minAmountMinor,
        Long maxAmountMinor,
        Instant createdFrom,
        Instant createdTo) {
}
