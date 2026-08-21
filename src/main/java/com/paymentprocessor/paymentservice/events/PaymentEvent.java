package com.paymentprocessor.paymentservice.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paymentprocessor.paymentservice.entity.PaymentIntent;

/**
 * Domain event emitted by the payment lifecycle. Every event carries the aggregate
 * id, a stable event type and a JSON-serializable payload map. Keeping event
 * construction centralized makes outbox payloads consistent and easy to evolve.
 */
public sealed interface PaymentEvent {

    String aggregateId();

    String eventType();

    Map<String, Object> payload();

    static Map<String, Object> basePayload(PaymentIntent intent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", intent.getId());
        payload.put("merchantId", intent.getMerchantId());
        payload.put("status", intent.getStatus());
        payload.put("paymentMethod", intent.getPaymentMethod());
        payload.put("amountMinor", intent.getAmountMinor());
        payload.put("currency", intent.getCurrency());
        payload.put("authorizedMinor", intent.getAuthorizedMinor());
        payload.put("capturedMinor", intent.getCapturedMinor());
        payload.put("refundedMinor", intent.getRefundedMinor());
        payload.put("occurredAt", Instant.now().toString());
        return payload;
    }

    record PaymentCreatedEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentCreated"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentAuthorizedEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentAuthorized"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentCapturedEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentCaptured"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentPartiallyCapturedEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentPartiallyCaptured"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentFailedEvent(PaymentIntent intent, String declineCode) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentFailed"; }
        @Override
        public Map<String, Object> payload() {
            Map<String, Object> payload = basePayload(intent);
            payload.put("declineCode", declineCode);
            return payload;
        }
    }

    record PaymentCancelledEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentCancelled"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentConfirmedEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentConfirmed"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentRefundedEvent(PaymentIntent intent) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentRefunded"; }
        @Override
        public Map<String, Object> payload() { return basePayload(intent); }
    }

    record PaymentExpiredEvent(PaymentIntent intent, String reason) implements PaymentEvent {
        @Override
        public String aggregateId() { return intent.getId(); }
        @Override
        public String eventType() { return "PaymentExpired"; }
        @Override
        public Map<String, Object> payload() {
            Map<String, Object> payload = basePayload(intent);
            payload.put("reason", reason);
            return payload;
        }
    }
}
