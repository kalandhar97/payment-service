package com.paymentprocessor.paymentservice.exception;

public class ConnectorException extends ApiException {
    public ConnectorException(String message) {
        super(502, "CONNECTOR_ERROR", message);
    }
}
