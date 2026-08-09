# payment-service — API Testing Guide

## Dependencies — what to run before this service

**Other services:** `payment-service` calls out to three internal services during
authorize/capture (all enabled by default, each individually toggleable):

- **fraud-service** (`FRAUD_SERVICE_URL`, default `:8088`) — pre-auth risk check;
  disable with `FRAUD_ENABLED=false`
- **tokenization-service** (`TOKENIZATION_SERVICE_URL`, default `:8084`) — card/UPI
  vault; disable with `VAULT_ENABLED=false`
- **limit-service** (`LIMIT_SERVICE_URL`, default `:8085`) — transaction-limit
  reservation; disable with `LIMIT_ENABLED=false`

It also calls external card/UPI processor mocks (`CARD_BASE_URL`, `UPI_BASE_URL`,
default `localhost:9101`/`9102`) — not part of this repo, stub or point elsewhere
if you don't have them running. `payment-service` itself starts fine without any
of the above; the calls just fail (or you can disable them) at request time.

**Infrastructure:** Postgres (`paymentservicedb`, seeded via `data.sql`, no
Flyway). Config Server is optional.

Base URL: `http://localhost:8087` (`server.port` in `application.yml`, override with `SERVER_PORT`)

Every endpoint requires a JWT bearer token by default — see [Authentication](#authentication)
below — **except** the two provider callback endpoints under `/v1/callbacks`
(`/v1/callbacks/upi`, `/v1/callbacks/3ds`), which stay public because the UPI rail and the 3-D
Secure directory server cannot present a platform JWT. Those two still accept unsigned, unverified
requests — the card/UPI provider's callback signature must be verified before the payload is
trusted; the controller comments call this out explicitly as not implemented, so restrict them at
the network edge.

Schema note: this service has **no Flyway migrations**. Hibernate creates the schema
(`spring.jpa.hibernate.ddl-auto=update`), then `src/main/resources/data.sql` seeds it
(`spring.sql.init.mode=always`, `defer-datasource-initialization=true` so seeding runs after
schema creation). Unlike merchant-service/notification-service, this seed file runs in **every**
profile including production-shaped configs — there is no `local`-only gating for this service.
The seed data was written to be harmless in any environment: obviously-fake ids, tokens and
network references.

`data.sql` seeds:

- The `intent_transitions` state-machine table (required for any state transition to work at all).
- Three sample `payment_intents` rows + one `authorizations` row:

| Entity                                          | Fixed id                                | State                                         |
|-------------------------------------------------|-----------------------------------------|-----------------------------------------------|
| PaymentIntent (CARD, USD 2,500.00)              | `pi_11111111111111111111111111111111`   | `CAPTURED` (fully captured)                   |
| Authorization for the above                     | `auth_11111111111111111111111111111111` | `APPROVED`                                    |
| PaymentIntent (CARD, USD 99.99, manual capture) | `pi_22222222222222222222222222222222`   | `AUTHORIZED` (awaiting capture)               |
| PaymentIntent (UPI, INR 500.00)                 | `pi_33333333333333333333333333333333`   | `CREATED` (awaiting UPI collect confirmation) |

All three use `merchantId = 11111111-1111-1111-1111-111111111111`, the same id seeded by
merchant-service's `V2__seed_sample_data.sql`, for cross-service consistency.

## Authentication

This service is now an OAuth2 **resource server**: every endpoint below requires
`Authorization: Bearer <accessToken>` by default. Tokens are RS256 JWTs issued by
`authentication-service` (port 8081) and validated locally against its JWKS at
`http://localhost:8081/.well-known/jwks.json` — signature, issuer, expiry, plus the `purpose`
claim, which must be `access` (refresh / step-up tokens are rejected). Claims map to authorities
as `scope` (space-delimited) -> `SCOPE_*`, and `principal_type` (`USER`, `MERCHANT`, `ADMIN`,
`SERVICE`) -> one `ROLE_*`. See `config/SecurityConfig.java`.

**Getting a token.** Log in against `authentication-service` on port 8081 — password login
(`POST http://localhost:8081/api/v1/auth/login`) or social login (Google / GitHub / Microsoft).
The token comes back as `tokens.accessToken`. See `authentication-service/API_TESTING.md` for the
full password/MFA and OAuth2 social-login flows.

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"<password>"}' \
  | jq -r '.tokens.accessToken')
```

**Testing without a token.** `security.jwt.enabled` (env `SECURITY_JWT_ENABLED`) defaults to
`true`. The `local` profile document in `application.yml` sets it to `false`, which swaps in a
permit-all chain, so with `SPRING_PROFILES_ACTIVE=local` — the profile the seeded-data examples
below already assume — the plain `curl` commands in this guide work as-is.
Never set it to `false` outside a developer machine or an ephemeral CI container.

**Always public** (no token, in either mode): `/actuator/health/**`, `/actuator/info`,
`/actuator/prometheus`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`.

**Additionally public in this service:** `/v1/callbacks/upi` and `/v1/callbacks/3ds`. The UPI rail
and the 3-D Secure directory server cannot present a platform JWT, so those two receivers stay
unauthenticated. Callback signature verification is **not implemented yet** — restrict them at the
network edge.

**The same call, both ways:**

```bash
# with the `local` profile (security.jwt.enabled=false) — works as written
curl http://localhost:8087/v1/payments/pi_11111111111111111111111111111111

# with the toggle on (the default) — token required
curl http://localhost:8087/v1/payments/pi_11111111111111111111111111111111 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Payments — `PaymentController` (`/v1/payments`)

### POST /v1/payments

Creates a new payment intent. Supports an optional `Idempotency-Key` header — replaying the same
key with the same request returns the original result instead of creating a duplicate intent.

For `CARD`, supply `instrumentToken`. For `UPI`, supply either `payerVpa` (COLLECT flow) or set
`upiFlow=INTENT` to receive a scannable UPI link instead.

Request body (CARD):

```json
{
  "merchantId": "11111111-1111-1111-1111-111111111111",
  "customerId": "cust_1004",
  "paymentMethod": "CARD",
  "instrumentToken": "tok_test_card_4242",
  "amountMinor": 150000,
  "currency": "USD",
  "captureMethod": "AUTOMATIC",
  "statementDescriptor": "ACME RETAIL",
  "description": "Order ORD-90220",
  "metadata": {
    "orderId": "ORD-90220"
  }
}
```

Response (201 Created):

```json
{
  "id": "pi_9f2a1c3e4b5d6f708192a3b4c5d6e7f8",
  "merchantId": "11111111-1111-1111-1111-111111111111",
  "customerId": "cust_1004",
  "paymentMethod": "CARD",
  "status": "CREATED",
  "subStatus": null,
  "amountMinor": 150000,
  "currency": "USD",
  "captureMethod": "AUTOMATIC",
  "connectorId": null,
  "authorizedMinor": 0,
  "capturedMinor": 0,
  "refundedMinor": 0,
  "statementDescriptor": "ACME RETAIL",
  "description": "Order ORD-90220",
  "acsUrl": null,
  "upiIntentUri": null,
  "createdAt": "2026-07-25T10:00:00Z",
  "updatedAt": "2026-07-25T10:00:00Z"
}
```

curl:

```bash
curl -X POST http://localhost:8087/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 7c9a1e2b-3d4f-4a5b-8c6d-1e2f3a4b5c6d" \
  -d '{"merchantId":"11111111-1111-1111-1111-111111111111","customerId":"cust_1004","paymentMethod":"CARD","instrumentToken":"tok_test_card_4242","amountMinor":150000,"currency":"USD","captureMethod":"AUTOMATIC","statementDescriptor":"ACME RETAIL","description":"Order ORD-90220","metadata":{"orderId":"ORD-90220"}}'
```

Request body (UPI collect):

```json
{
  "merchantId": "11111111-1111-1111-1111-111111111111",
  "customerId": "cust_1005",
  "paymentMethod": "UPI",
  "payerVpa": "jane.doe@acmeupi",
  "amountMinor": 50000,
  "currency": "INR",
  "captureMethod": "AUTOMATIC",
  "description": "Order ORD-90221"
}
```

### POST /v1/payments/{id}/authorize

No body. Moves `CREATED` -> `AUTHORIZED` (or `FAILED`) by calling the routed connector.

```bash
curl -X POST http://localhost:8087/v1/payments/pi_22222222222222222222222222222222/authorize
```

### POST /v1/payments/{id}/capture

Body optional; a null/absent `amountMinor` captures the full remaining authorized amount.

Request body (partial capture):

```json
{
  "amountMinor": 5000,
  "finalCapture": false
}
```

curl (full capture of the seeded manual-capture intent):

```bash
curl -X POST http://localhost:8087/v1/payments/pi_22222222222222222222222222222222/capture \
  -H "Content-Type: application/json" \
  -d '{"finalCapture": true}'
```

### POST /v1/payments/{id}/void

No body. Releases an `AUTHORIZED` hold.

```bash
curl -X POST http://localhost:8087/v1/payments/pi_22222222222222222222222222222222/void
```

### POST /v1/payments/{id}/refund

Body optional; a null `amountMinor` refunds the full refundable amount.

Request body:

```json
{
  "amountMinor": 100000,
  "reason": "Customer requested partial refund"
}
```

curl (refund the seeded captured payment):

```bash
curl -X POST http://localhost:8087/v1/payments/pi_11111111111111111111111111111111/refund \
  -H "Content-Type: application/json" \
  -d '{"amountMinor": 100000, "reason": "Customer requested partial refund"}'
```

### POST /v1/payments/{id}/confirm

No body. Confirms a captured payment (e.g. `CAPTURED` -> `CONFIRMED`).

```bash
curl -X POST http://localhost:8087/v1/payments/pi_11111111111111111111111111111111/confirm
```

### POST /v1/payments/{id}/retry

No body. Retries a `FAILED` intent (`FAILED` -> `CREATED` per the state machine).

```bash
curl -X POST http://localhost:8087/v1/payments/pi_22222222222222222222222222222222/retry
```

### POST /v1/payments/{id}/upi-intent

No body. Generates a UPI deep link/QR for a `UPI` intent using the `INTENT` flow.

```bash
curl -X POST http://localhost:8087/v1/payments/pi_33333333333333333333333333333333/upi-intent
```

Response (200):

```json
{
  "intentId": "pi_33333333333333333333333333333333",
  "providerRef": "upi_seed_ref_0001",
  "intentUri": "upi://pay?pa=acme@psp&pn=Acme%20Payments&am=500.00&cu=INR&tr=pi_33333333333333333333333333333333",
  "qrPayload": "upi://pay?pa=acme@psp&pn=Acme%20Payments&am=500.00&cu=INR&tr=pi_33333333333333333333333333333333",
  "expirySeconds": 300
}
```

### GET /v1/payments/{id}

```bash
curl http://localhost:8087/v1/payments/pi_11111111111111111111111111111111
```

Response (200):

```json
{
  "id": "pi_11111111111111111111111111111111",
  "merchantId": "11111111-1111-1111-1111-111111111111",
  "customerId": "cust_1001",
  "paymentMethod": "CARD",
  "status": "CAPTURED",
  "subStatus": null,
  "amountMinor": 250000,
  "currency": "USD",
  "captureMethod": "AUTOMATIC",
  "connectorId": "card-connector-sim",
  "authorizedMinor": 250000,
  "capturedMinor": 250000,
  "refundedMinor": 0,
  "statementDescriptor": "ACME RETAIL",
  "description": "Order ORD-90210",
  "acsUrl": null,
  "upiIntentUri": null,
  "createdAt": "2026-07-25T08:00:00Z",
  "updatedAt": "2026-07-25T09:00:00Z"
}
```

### GET /v1/payments/{id}/timeline

```bash
curl http://localhost:8087/v1/payments/pi_11111111111111111111111111111111/timeline
```

Response (200):

```json
[
  {
    "eventType": "CREATED",
    "occurredAt": "2026-07-25T08:00:00Z",
    "payload": "{\"status\":\"CREATED\"}"
  },
  {
    "eventType": "AUTHORIZED",
    "occurredAt": "2026-07-25T08:00:05Z",
    "payload": "{\"authCode\":\"A1B2C3\"}"
  },
  {
    "eventType": "CAPTURED",
    "occurredAt": "2026-07-25T09:00:00Z",
    "payload": "{\"capturedMinor\":250000}"
  }
]
```

### GET /v1/payments

Search/list with pagination. Query params (all optional, AND-combined): `merchantId`,
`customerId`, `status` (repeatable, e.g. `status=CAPTURED&status=AUTHORIZED`), `currency`,
`paymentMethod`, `minAmountMinor`, `maxAmountMinor`, `createdFrom` / `createdTo` (ISO-8601
date-time), plus standard Spring `Pageable` params (`page`, `size`, `sort`).

```bash
curl "http://localhost:8087/v1/payments?merchantId=11111111-1111-1111-1111-111111111111&status=CAPTURED&currency=USD&page=0&size=20"
```

Response (200, Spring `Page` envelope):

```json
{
  "content": [
    {
      "id": "pi_11111111111111111111111111111111",
      "status": "CAPTURED",
      "amountMinor": 250000,
      "currency": "USD"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

## Provider Callbacks — `CallbackController` (`/v1/callbacks`)

Inbound asynchronous provider callbacks. UPI debit results and 3-D Secure authentication outcomes
arrive here and drive the intent to its terminal state. **No signature verification is implemented
for either endpoint** — anyone who can reach this route can forge a callback. Restrict network
access to these paths (e.g. to the PSP's known IP ranges / a VPN) until signature verification is
added.

### POST /v1/callbacks/upi

Request body:

```json
{
  "intentId": "pi_33333333333333333333333333333333",
  "providerRef": "upi_seed_ref_0001",
  "status": "SUCCESS",
  "npciTxnId": "NPCI-TXN-000123456789",
  "declineCode": null
}
```

curl:

```bash
curl -X POST http://localhost:8087/v1/callbacks/upi \
  -H "Content-Type: application/json" \
  -d '{"intentId":"pi_33333333333333333333333333333333","providerRef":"upi_seed_ref_0001","status":"SUCCESS","npciTxnId":"NPCI-TXN-000123456789"}'
```

### POST /v1/callbacks/3ds

Request body:

```json
{
  "intentId": "pi_22222222222222222222222222222222",
  "status": "AUTHENTICATED",
  "eci": "05",
  "cavvRef": "cavv_seed_ref_0001",
  "liabilityShift": true
}
```

curl:

```bash
curl -X POST http://localhost:8087/v1/callbacks/3ds \
  -H "Content-Type: application/json" \
  -d '{"intentId":"pi_22222222222222222222222222222222","status":"AUTHENTICATED","eci":"05","cavvRef":"cavv_seed_ref_0001","liabilityShift":true}'
```
