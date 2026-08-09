package com.paymentprocessor.paymentservice.connector.model;

/** Request to void an authorization hold before capture. */
public record VoidCommand(String authorizationRef, long amountMinor) {
}
