package com.paymentprocessor.paymentservice.connector;

import com.paymentprocessor.paymentservice.connector.model.RefundCommand;
import com.paymentprocessor.paymentservice.connector.model.RefundResult;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiCollectResult;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentCommand;
import com.paymentprocessor.paymentservice.connector.model.UpiIntentResult;

/**
 * Abstraction over a UPI PSP. Unlike cards, UPI has no separate authorize/capture
 * step - a successful collect/intent debits the payer immediately - so the
 * connector exposes collect and intent initiation plus refund. Terminal results
 * arrive asynchronously and are handled by the callback endpoint.
 */
public interface UpiConnector {

    String connectorId();

    UpiCollectResult collect(UpiCollectCommand command);

    UpiIntentResult intent(UpiIntentCommand command);

    RefundResult refund(RefundCommand command);
}
