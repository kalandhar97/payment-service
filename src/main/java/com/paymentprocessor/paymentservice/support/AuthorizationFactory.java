package com.paymentprocessor.paymentservice.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.paymentprocessor.paymentservice.connector.model.AuthorizeResult;
import com.paymentprocessor.paymentservice.entity.Authorization;
import com.paymentprocessor.paymentservice.entity.PaymentIntent;
import com.paymentprocessor.paymentservice.enums.PaymentStatus;

/** Creates {@link Authorization} entities for card and UPI flows. */
@Component
public class AuthorizationFactory {

    private final IdGenerator idGenerator;
    private final long cardExpirySeconds;
    private final long upiExpirySeconds;

    public AuthorizationFactory(IdGenerator idGenerator,
                                @Value("${authorization.expiry.card-seconds:604800}") long cardExpirySeconds,
                                @Value("${authorization.expiry.upi-seconds:300}") long upiExpirySeconds) {
        this.idGenerator = idGenerator;
        this.cardExpirySeconds = cardExpirySeconds;
        this.upiExpirySeconds = upiExpirySeconds;
    }

    public Authorization createFromCardResult(PaymentIntent intent, String attemptId,
                                              AuthorizeResult result) {
        Authorization auth = baseAuthorization(intent);
        auth.setAttemptId(attemptId);
        auth.setStatus(PaymentStatus.AUTHORIZED.name());
        auth.setAuthCode(result.authCode());
        auth.setNetworkTxnId(result.connectorRef());
        auth.setRrn(result.rrn());
        auth.setArn(result.arn());
        auth.setEci(result.eci());
        auth.setAvsResult(result.avsResult());
        auth.setCvvResult(result.cvvResult());
        auth.setExpiresAt(result.expiresAt() != null
                ? result.expiresAt()
                : Instant.now().plus(cardExpirySeconds, ChronoUnit.SECONDS));
        return auth;
    }

    public Authorization createPendingForUpi(PaymentIntent intent, String providerRef) {
        Authorization auth = baseAuthorization(intent);
        auth.setStatus("PENDING");
        auth.setNetworkTxnId(providerRef);
        auth.setExpiresAt(Instant.now().plus(upiExpirySeconds, ChronoUnit.SECONDS));
        return auth;
    }

    public Authorization createAuthorizedForUpi(PaymentIntent intent, Authorization existing,
                                              String providerRef) {
        Authorization auth = existing != null ? existing : baseAuthorization(intent);
        auth.setStatus(PaymentStatus.AUTHORIZED.name());
        if (providerRef != null) {
            auth.setNetworkTxnId(providerRef);
        }
        auth.setExpiresAt(Instant.now().plus(upiExpirySeconds, ChronoUnit.SECONDS));
        return auth;
    }

    public long upiExpirySeconds() {
        return upiExpirySeconds;
    }

    public long cardExpirySeconds() {
        return cardExpirySeconds;
    }

    private Authorization baseAuthorization(PaymentIntent intent) {
        Authorization auth = new Authorization();
        auth.setId(idGenerator.newId());
        auth.setIntentId(intent.getId());
        auth.setAmountMinor(intent.getAmountMinor());
        auth.setCurrency(intent.getCurrency());
        return auth;
    }
}
