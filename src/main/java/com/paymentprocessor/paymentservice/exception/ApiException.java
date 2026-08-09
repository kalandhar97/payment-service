package com.paymentprocessor.paymentservice.exception;

/** Base for exceptions that map to a specific HTTP status and machine-readable code. */
public abstract class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    protected ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
