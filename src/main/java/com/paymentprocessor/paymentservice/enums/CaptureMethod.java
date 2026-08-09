package com.paymentprocessor.paymentservice.enums;

/**
 * Determines whether funds are captured automatically on a successful
 * authorization ({@code AUTOMATIC}) or require an explicit capture call
 * ({@code MANUAL}). UPI collect/intent flows are always {@code AUTOMATIC}.
 */
public enum CaptureMethod {
    AUTOMATIC,
    MANUAL
}
