# Payment Service

## Overview

The Payment Service is the core transactional engine of the platform. It is responsible for executing the complete
payment lifecycle — from creation and authorization through capture, confirmation, and settlement. Every payment flows
through a deterministic state machine that ensures consistency, auditability, and compliance with card network and
regulatory rules.

This service handles both simple one-step payments and complex multi-step flows (e.g., authorize-then-capture for
pre-orders, subscriptions, or hotel reservations). It is designed to be idempotent, resilient to network failures, and
fully traceable through a detailed event timeline.

---

## Table of Contents

- [Responsibilities](#responsibilities)
- [Payment Lifecycle State Machine](#payment-lifecycle-state-machine)
- [Core Functionalities](#core-functionalities)
    - [Payment Creation](#payment-creation)
    - [Payment Authorization](#payment-authorization)
    - [Payment Capture](#payment-capture)
    - [Partial Capture](#partial-capture)
    - [Cancel Authorization](#cancel-authorization)
    - [Payment Confirmation](#payment-confirmation)
    - [Payment Status Management](#payment-status-management)
    - [Payment Expiry](#payment-expiry)
    - [Payment Retry](#payment-retry)
    - [Payment Metadata](#payment-metadata)
    - [Payment Search](#payment-search)
    - [Payment Timeline](#payment-timeline)
    - [Payment Receipt](#payment-receipt)
    - [Payment Webhooks](#payment-webhooks)
- [Owned Resources](#owned-resources)
- [Domain Events](#domain-events)
- [Integration Notes](#integration-notes)

---

## Responsibilities

| Concern                       | Description                                                                                               |
|-------------------------------|-----------------------------------------------------------------------------------------------------------|
| **Payment Orchestration**     | Execute the end-to-end payment flow across creation, authorization, capture, and confirmation.            |
| **State Machine Enforcement** | Ensure every payment transitions through valid states with proper guards and preconditions.               |
| **Idempotency**               | Guarantee that duplicate requests (network retries, client retries) do not create duplicate payments.     |
| **Financial Integrity**       | Prevent double-charging, over-capturing, and unauthorized captures through strict balance tracking.       |
| **Resilience**                | Handle gateway timeouts, network failures, and asynchronous provider responses gracefully.                |
| **Auditability**              | Maintain a complete, immutable timeline of every payment event for reconciliation and dispute resolution. |

---

## Payment Lifecycle State Machine

```
CREATED
   │
   ▼
AUTHORIZED ◄──────────┐
   │                  │
   ├──► CAPTURED ────┤ (partial capture possible)
   │       │           │
   │       ▼           │
   │   CONFIRMED       │
   │       │           │
   │       ▼           │
   │   SETTLED         │
   │                   │
   ├──► CANCELLED      │
   │                   │
   └──► FAILED ◄───────┘ (retry may transition back to CREATED)
   │
   └──► EXPIRED
```

**State Definitions:**

| State          | Description                                                |
|----------------|------------------------------------------------------------|
| **CREATED**    | Payment record initialized, awaiting authorization.        |
| **AUTHORIZED** | Funds held by the issuer; ready for capture.               |
| **CAPTURED**   | Funds captured from the authorization hold.                |
| **CONFIRMED**  | Capture acknowledged by the payment provider.              |
| **SETTLED**    | Funds transferred to the merchant settlement account.      |
| **CANCELLED**  | Authorization voided; funds released back to the customer. |
| **FAILED**     | Authorization or capture rejected by the provider.         |
| **EXPIRED**    | Authorization hold timed out before capture.               |

---

## Core Functionalities

### Payment Creation

Initializes a new payment record with all required transaction details.

**Input Fields:**

- `merchantId` — The merchant processing the payment
- `amount` — Transaction amount with currency
- `customerId` — Platform customer reference
- `paymentMethod` — Card, bank transfer, wallet, etc.
- `paymentMethodId` — Tokenized payment instrument reference
- `idempotencyKey` — Client-provided key for duplicate prevention
- `metadata` — Key-value pairs for merchant-specific data
- `description` — Human-readable transaction description

**Behavior:**

- Validates merchant status (must be `ACTIVE`).
- Checks idempotency key; returns existing payment if duplicate.
- Applies merchant fee configuration.
- Generates a unique `paymentId` and `authorizationReference`.
- Publishes `PaymentCreated` event.

---

### Payment Authorization

Requests the payment provider (acquirer / card network) to hold funds on the customer's account.

**Authorization Flow:**

1. Validate payment is in `CREATED` state.
2. Route to appropriate payment provider based on method and merchant configuration.
3. Perform risk checks (fraud screening, velocity limits, AVS, 3DS if required).
4. Send authorization request to the acquirer.
5. **Synchronous Response:** Provider responds immediately (approve/decline).
6. **Asynchronous Response:** Provider responds later (e.g., 3DS challenge, bank transfer pending).

**Outcomes:**

- **Approved** → State: `AUTHORIZED`, publish `PaymentAuthorized`
- **Declined** → State: `FAILED`, publish `PaymentFailed`
- **Pending** → State remains `CREATED` until async callback received

---

### Payment Capture

Finalizes the transaction by requesting the provider to transfer the authorized funds to the merchant.

**Capture Rules:**

- Only payments in `AUTHORIZED` state can be captured.
- Capture amount must be ≤ authorized amount.
- Capture must occur before authorization expiry (typically 7 days for cards, varies by provider).

**Capture Flow:**

1. Validate payment is `AUTHORIZED` and not expired.
2. Send capture request to the provider.
3. On success → State: `CAPTURED`, publish `PaymentCaptured`
4. On failure → State: `FAILED`, publish `PaymentFailed`

---

### Partial Capture

Allows merchants to capture less than the full authorized amount, releasing the remainder.

**Use Cases:**

- Customer orders multiple items; only some are in stock.
- Hotel pre-authorization for incidentals; actual stay cost is lower.
- Subscription with variable usage billing.

**Rules:**

- Total captured amount across all partial captures ≤ authorized amount.
- Remaining uncaptured amount is automatically released after authorization expiry.
- Each partial capture generates its own `CaptureReference`.

**Example:**

```
Authorization: $500.00
Partial Capture 1: $200.00 → Remaining: $300.00
Partial Capture 2: $150.00 → Remaining: $150.00
Authorization Expires → $150.00 released
```

---

### Cancel Authorization

Voids an authorization before capture, releasing the held funds immediately.

**Rules:**

- Only `AUTHORIZED` payments can be cancelled.
- Cannot cancel after any capture has occurred (use refund instead).
- Full cancellation only; partial cancellation is not supported at the authorization level.

**Behavior:**

- Send void request to the provider.
- On success → State: `CANCELLED`
- On failure (e.g., already captured) → State transition blocked, error returned

---

### Payment Confirmation

Acknowledges that the capture has been confirmed by the payment provider and the transaction is irreversible.

**Trigger:**

- Synchronous capture response from provider.
- Asynchronous capture confirmation webhook.

**Behavior:**

- State transitions from `CAPTURED` → `CONFIRMED`.
- Marks the payment as financially committed.
- Triggers settlement scheduling (typically batched for end-of-day settlement).

---

### Payment Status Management

Provides real-time visibility into the current state of any payment.

**Status Query:**

- `GET /payments/{paymentId}/status`
- Returns current state, sub-state (e.g., `3DS_PENDING`, `PROVIDER_TIMEOUT`), and last updated timestamp.

**Status Polling vs. Webhooks:**

- **Polling:** Clients may poll for status (rate-limited).
- **Webhooks:** Preferred; merchant receives push notifications on state changes.

---

### Payment Expiry

Automatically transitions payments whose authorizations have timed out.

**Expiry Rules:**

- Card authorizations typically expire after 7 days (varies by issuer and network).
- Bank transfer authorizations may expire after 24–48 hours.
- Scheduled job or event-driven process monitors expiry times.

**Behavior:**

- `AUTHORIZED` → `EXPIRED` (if not captured before deadline).
- `CREATED` → `EXPIRED` (if never authorized within platform timeout).
- Publish `PaymentExpired` event.
- Release any held funds back to the customer.

---

### Payment Retry

Handles failed payments by allowing re-attempts under controlled conditions.

**Retry Policies:**

| Scenario                                                      | Retry Behavior                                                           |
|---------------------------------------------------------------|--------------------------------------------------------------------------|
| **Soft Decline** (insufficient funds, temporary issuer error) | Automatic retry with exponential backoff (max 3 attempts over 72 hours). |
| **Hard Decline** (stolen card, invalid CVV, expired card)     | No retry; immediate failure.                                             |
| **Network Timeout**                                           | Immediate retry once, then exponential backoff.                          |
| **3DS Failure**                                               | Retry with step-up authentication if supported.                          |

**Retry Constraints:**

- Maximum retry count per payment.
- Minimum interval between retries.
- Retry only allowed for payments in `FAILED` state originating from `CREATED`.
- Each retry generates a new `authorizationReference`.

---

### Payment Metadata

Supports arbitrary key-value pairs attached to a payment for merchant-specific or integration-specific needs.

**Use Cases:**

- Order ID from the merchant's e-commerce system.
- Customer session ID for analytics.
- Marketing campaign attribution.
- Internal reference numbers for reconciliation.

**Rules:**

- Metadata is stored opaquely; the platform does not interpret values.
- Max 50 key-value pairs per payment.
- Keys: max 40 characters. Values: max 500 characters.
- Metadata is immutable after payment reaches `CONFIRMED` or `SETTLED`.

---

### Payment Search

Enables querying and filtering of payments across the platform.

**Search Filters:**

- `paymentId` — Exact match
- `merchantId` — All payments for a merchant
- `customerId` — All payments for a customer
- `status` — One or more states
- `amountRange` — Min and max amount
- `currency` — ISO 4217 currency code
- `createdAt` — Date range
- `paymentMethod` — Card, bank transfer, wallet, etc.
- `authorizationReference` — Provider-side reference

**Features:**

- Pagination (cursor-based and offset-based)
- Sorting by `createdAt`, `amount`, `status`
- Result aggregation (count, total volume, total value)

---

### Payment Timeline

Provides a complete, immutable audit trail of every event that occurred during a payment's lifecycle.

**Timeline Events:**

| Event                     | Description                                 |
|---------------------------|---------------------------------------------|
| `PAYMENT_CREATED`         | Payment record initialized.                 |
| `AUTHORIZATION_REQUESTED` | Authorization sent to provider.             |
| `AUTHORIZATION_SUCCEEDED` | Funds held successfully.                    |
| `AUTHORIZATION_DECLINED`  | Provider rejected authorization.            |
| `AUTHORIZATION_CANCELLED` | Authorization voided by merchant or system. |
| `CAPTURE_REQUESTED`       | Capture sent to provider.                   |
| `CAPTURE_SUCCEEDED`       | Funds captured successfully.                |
| `CAPTURE_FAILED`          | Provider rejected capture.                  |
| `PARTIAL_CAPTURE`         | Partial amount captured.                    |
| `PAYMENT_EXPIRED`         | Authorization timed out.                    |
| `RETRY_ATTEMPTED`         | Retry initiated for failed payment.         |
| `STATUS_CHANGED`          | Any state transition.                       |

**Features:**

- Immutable append-only log.
- Each entry includes timestamp, actor (system / user / merchant), and reason code.
- Exportable for reconciliation and dispute evidence.

---

### Payment Receipt

Generates a human-readable and machine-parseable record of a completed payment.

**Receipt Content:**

- Merchant name and branding
- Payment ID and authorization reference
- Date and time of transaction
- Amount, currency, and fee breakdown
- Payment method (masked card number, wallet type)
- Customer reference
- Status and confirmation code
- QR code for verification (optional)

**Formats:**

- JSON (API response)
- PDF (downloadable)
- HTML (email embed)

---

### Payment Webhooks

Delivers real-time notifications to merchants when payment states change.

**Webhook Events:**

| Event                     | Trigger State      |
|---------------------------|--------------------|
| `payment.created`         | `CREATED`          |
| `payment.authorized`      | `AUTHORIZED`       |
| `payment.captured`        | `CAPTURED`         |
| `payment.confirmed`       | `CONFIRMED`        |
| `payment.failed`          | `FAILED`           |
| `payment.cancelled`       | `CANCELLED`        |
| `payment.expired`         | `EXPIRED`          |
| `payment.partial_capture` | Partial `CAPTURED` |

**Delivery Guarantees:**

- At-least-once delivery with idempotency key.
- Exponential backoff retry (max 24 hours or 10 attempts).
- Dead letter queue for permanently failed deliveries.
- HMAC-SHA256 signature for payload integrity verification.

---

## Owned Resources

The Payment Service is the authoritative owner of the following data:

| Resource                    | Description                                                                                                          |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------|
| **Payment**                 | Core payment record including amount, currency, status, and lifecycle state.                                         |
| **Authorization Reference** | Unique reference returned by the payment provider for the authorization hold.                                        |
| **Capture Reference**       | Unique reference returned by the payment provider for each capture attempt (supports multiple for partial captures). |

> **Note:** Payment method tokens are owned by the Tokenization Service. Merchant configuration is owned by the Merchant
> Service. Settlement and payout execution is owned by the Settlement Service.

---

## Domain Events

The Payment Service publishes the following events for downstream consumers:

| Event               | Trigger                                                                              |
|---------------------|--------------------------------------------------------------------------------------|
| `PaymentCreated`    | A new payment record is initialized.                                                 |
| `PaymentAuthorized` | The payment provider approves the authorization request.                             |
| `PaymentCaptured`   | Funds are successfully captured from the authorization hold.                         |
| `PaymentFailed`     | Authorization or capture is declined by the provider or fails due to a system error. |
| `PaymentExpired`    | The authorization hold times out before capture.                                     |

---

## Integration Notes

- **Merchant Service**: Validates merchant status, fee configuration, and payment method eligibility before processing.
- **User / Customer Service**: Resolves customer references and billing details.
- **Authentication / Authorization Service**: Verifies that the caller has permission to create or query payments for
  the specified merchant.
- **Tokenization Service**: Retrieves and detokenizes payment instrument details securely.
- **Payment Provider / Acquirer**: Sends authorization, capture, and void requests; handles provider-specific response
  codes and async callbacks.
- **Fraud / Risk Service**: Evaluates transaction risk before authorization; may trigger 3DS or block transactions.
- **Settlement Service**: Receives `PaymentCaptured` events to schedule fund transfers to merchant accounts.
- **Notification Service**: Consumes payment events to send email/SMS receipts and alerts.
- **Audit Service**: Subscribes to all payment events for regulatory compliance, reconciliation, and dispute resolution.
- **Webhook Service**: Delivers merchant-configured webhooks for real-time payment status updates.

---

## UPI Payments

The service supports UPI (Unified Payments Interface) alongside cards. UPI differs fundamentally from card processing:
it has no separate authorize-then-capture step. A successful UPI transaction is a single-message debit that moves funds
immediately, so the intent collapses `AUTHORIZED → CAPTURED → CONFIRMED` in one asynchronous callback. UPI intents are
therefore always created with `captureMethod = AUTOMATIC`, and the manual `capture` / `void` operations are rejected for
UPI (refunds are supported).

Two initiation flows are supported:

| Flow        | How it works                                                                                                                                        | Request                                                                                 |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| **Collect** | A collect request is pushed to the payer's VPA (e.g. `alice@okbank`). The payer approves it in their UPI app, and the PSP notifies us via callback. | Create with `paymentMethod = UPI` and a `payerVpa`, then `POST /authorize`.             |
| **Intent**  | A `upi://pay?...` deep link (and QR payload) is returned for the payer to scan or open. The PSP notifies us via callback once the payer pays.       | Create with `paymentMethod = UPI` and `upiFlow = INTENT`, then `POST /{id}/upi-intent`. |

**Lifecycle:** on creation the intent is `CREATED`. Initiating a collect or intent moves it to a pending sub-state (
`UPI_COLLECT_PENDING` / `UPI_INTENT_PENDING`) and records a pending authorization carrying the collect-expiry deadline (
default 5 minutes). The terminal debit result arrives at `POST /v1/callbacks/upi`; success drives the intent to
`CONFIRMED` and emits `PaymentAuthorized`, `PaymentCaptured`, and `PaymentConfirmed`, while a failure or a lapsed
deadline moves it to `FAILED` / `EXPIRED`.

**Owned UPI fields:** `payerVpa` on the intent, and the PSP/NPCI transaction reference persisted on the authorization
and capture (`networkTxnId` / `networkRef`) for reconciliation.

---

## Architecture & Modules

The service is a Spring Boot 3 (Java 17) application layered as follows:

| Package                 | Responsibility                                                                                                                                                            |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `controller`            | REST API (`PaymentController`) and inbound provider callbacks (`CallbackController`).                                                                                     |
| `dto`                   | Request/response records with bean-validation constraints.                                                                                                                |
| `service`               | `PaymentOrchestrationService` (the workflow engine), `IdempotencyService`, `OutboxWriter`, `PaymentMapper`.                                                               |
| `statemachine`          | `PaymentStateMachine` — validates every transition against the `intent_transitions` allow-list.                                                                           |
| `connector`             | Neutral `PaymentConnector` / `UpiConnector` abstractions with `RestClient`-based HTTP implementations, plus `FraudClient`, `VaultClient` and `ConnectorRegistry` routing. |
| `entity` / `repository` | JPA aggregates (intent, attempt, authorization, capture, refund, void, 3DS session, idempotency key, outbox) and Spring Data repositories.                                |
| `scheduler`             | `OutboxPublisher` (relays domain events) and `ExpiryScheduler` (expires stale holds).                                                                                     |
| `config`                | Externalised connector configuration and HTTP client factory.                                                                                                             |

**Design guarantees.** Every mutating operation runs in a single database transaction that updates the intent's running
totals, its status (guarded by the state machine), the supporting ledger rows, and the outbox event together — so state
and events can never diverge. Optimistic locking (`@Version`) on the intent protects against concurrent modification,
idempotency keys make create-payment safe to retry, and the transactional outbox gives at-least-once event delivery
without dual-write inconsistency.

---

## API Reference

Base path `/v1`.

| Method & Path                    | Purpose                                                                                                                      |
|----------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `POST /payments`                 | Create a payment intent. Send an `Idempotency-Key` header to make retries safe.                                              |
| `POST /payments/{id}/authorize`  | Authorize a card hold, or push a UPI collect request.                                                                        |
| `POST /payments/{id}/capture`    | Capture (full or partial — omit `amountMinor` for full). Card, manual capture only.                                          |
| `POST /payments/{id}/void`       | Void an uncaptured card authorization.                                                                                       |
| `POST /payments/{id}/refund`     | Refund a captured amount (full or partial).                                                                                  |
| `POST /payments/{id}/confirm`    | Mark a captured payment confirmed.                                                                                           |
| `POST /payments/{id}/retry`      | Retry a `FAILED` payment (re-authorizes with a new attempt).                                                                 |
| `POST /payments/{id}/upi-intent` | Generate a UPI intent link + QR for the payer.                                                                               |
| `GET /payments/{id}`             | Fetch current status and running totals.                                                                                     |
| `GET /payments/{id}/timeline`    | Immutable event timeline (from the outbox).                                                                                  |
| `GET /payments`                  | Search/filter with pagination (`merchantId`, `customerId`, `status`, `currency`, `paymentMethod`, amount range, date range). |
| `POST /callbacks/upi`            | Asynchronous UPI debit result from the PSP.                                                                                  |
| `POST /callbacks/3ds`            | Asynchronous 3-D Secure authentication result.                                                                               |

All amounts are in **minor units** (e.g. paise / cents) as integers to avoid floating-point rounding.

---

## Running Locally

Requires JDK 17 and a PostgreSQL database.

```bash
# 1. Start Postgres and create the database
createdb payments

# 2. Provide connection + connector settings via environment (see below), then run
./gradlew bootRun
```

The service is at `http://localhost:8080`; health at `/actuator/health`. On first boot Hibernate creates the schema and
`data.sql` seeds the state-machine transitions.

### Configuration (environment variables)

| Variable                                                         | Meaning                                                    |
|------------------------------------------------------------------|------------------------------------------------------------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD`                               | PostgreSQL connection.                                     |
| `CARD_BASE_URL`, `CARD_API_KEY`                                  | Card acquirer/gateway endpoint and credential.             |
| `UPI_BASE_URL`, `UPI_API_KEY`, `UPI_PAYEE_VPA`, `UPI_PAYEE_NAME` | UPI PSP endpoint, credential, and merchant payee identity. |
| `FRAUD_BASE_URL`, `FRAUD_ENABLED`                                | Fraud/risk service (fails open if unreachable).            |
| `VAULT_BASE_URL`, `VAULT_ENABLED`                                | Tokenization/vault service for masked instrument metadata. |
| `OUTBOX_SINK_URL`                                                | Optional event-bus HTTP sink; events are logged if unset.  |
| `CARD_AUTH_EXPIRY`, `UPI_AUTH_EXPIRY`                            | Authorization-hold lifetimes in seconds.                   |

The connector base URLs are placeholders by default; point them at real gateway/PSP sandboxes to process live
transactions. The connector interfaces isolate all provider-specific wiring, so swapping or adding a provider requires
no changes to the orchestration layer.
