package com.paymentprocessor.paymentservice.connector.model;

/** Request to place an authorization hold on a card instrument. */
public record AuthorizeCommand(
        String intentId,
        String instrumentToken,
        long amountMinor,
        String currency,
        String statementDescriptor,
        boolean requireThreeDs) {
}
