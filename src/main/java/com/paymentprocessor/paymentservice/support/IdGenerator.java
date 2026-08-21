package com.paymentprocessor.paymentservice.support;

import java.util.UUID;

import org.springframework.stereotype.Component;

/** Generates domain identifiers as random UUID strings. */
@Component
public class IdGenerator {

    public String newId() {
        return UUID.randomUUID().toString();
    }
}
