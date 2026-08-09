package com.paymentprocessor.paymentservice.connector;

/**
 * Non-sensitive instrument metadata returned by tokenization-service for
 * receipts/display. tokenization-service never stores or returns a raw PAN/CVV
 * (its {@code Instrument} entity only carries an opaque token, kind and a
 * one-way fingerprint), so there is no field here that could leak one either.
 */
public record InstrumentDetails(String id, String token, String kind, String scopeMerchantId) {
}
