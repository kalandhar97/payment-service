package com.paymentprocessor.paymentservice.connector;

import com.paymentprocessor.paymentservice.connector.model.AuthorizeCommand;
import com.paymentprocessor.paymentservice.connector.model.AuthorizeResult;
import com.paymentprocessor.paymentservice.connector.model.CaptureCommand;
import com.paymentprocessor.paymentservice.connector.model.CaptureResult;
import com.paymentprocessor.paymentservice.connector.model.RefundCommand;
import com.paymentprocessor.paymentservice.connector.model.RefundResult;
import com.paymentprocessor.paymentservice.connector.model.VoidCommand;
import com.paymentprocessor.paymentservice.connector.model.VoidResult;

/**
 * Abstraction over an acquiring/card gateway. Implementations translate the
 * service's neutral command/result model to a specific provider API. Swapping
 * providers is a matter of adding an implementation and pointing configuration
 * at it - no orchestration code changes.
 */
public interface PaymentConnector {

    /** Stable identifier persisted on attempts/authorizations for reconciliation. */
    String connectorId();

    AuthorizeResult authorize(AuthorizeCommand command);

    CaptureResult capture(CaptureCommand command);

    VoidResult voidAuthorization(VoidCommand command);

    RefundResult refund(RefundCommand command);
}
