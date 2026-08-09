package com.paymentprocessor.paymentservice.exception;

public class InvalidStateTransitionException extends ApiException {
    public InvalidStateTransitionException(String from, String to) {
        super(409, "INVALID_STATE_TRANSITION",
                "Transition from " + from + " to " + to + " is not permitted");
    }
}
