package com.paymentprocessor.paymentservice.enums;

/**
 * UPI initiation flows.
 *
 * <ul>
 *   <li>{@code COLLECT} - a collect request is pushed to the payer's VPA; the
 *       payer approves in their UPI app and the PSP notifies us asynchronously.</li>
 *   <li>{@code INTENT} - a UPI deep link / QR is returned to the client; the
 *       payer scans/opens it, and the PSP notifies us asynchronously.</li>
 * </ul>
 */
public enum UpiFlow {
    COLLECT,
    INTENT
}
