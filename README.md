# payment-service

Orchestrates the card and UPI payment lifecycle — create, authorize, capture, void, refund — coordinating limit
reservations, fraud checks, and connector calls under a DB-backed state machine.

## Role in the platform

- Owns the `PaymentIntent` lifecycle end to end: `CREATED → AUTHORIZED → (PARTIALLY_)CAPTURED → CONFIRMED → SETTLED`,
  plus `CANCELLED`, `FAILED`, `EXPIRED`, `(PARTIALLY_)REFUNDED` branches.
- Is the hot-path service that stitches together three downstream calls per card authorization: reserve funds in
  limit-service, screen the transaction in fraud-service, then submit to the card network via an external connector.
- Enforces every state transition against a DB-backed allow-list (`intent_transitions`) rather than in-code `if`/
  `switch` logic, so the legal transition graph is data, not code.
- Publishes domain events (`PaymentCreated`, `PaymentAuthorized`, `PaymentCaptured`, `PaymentConfirmed`,
  `PaymentCancelled`, `PaymentExpired`, `PaymentRefunded`, etc.) via a transactional outbox, not Kafka.
- Handles idempotent payment creation (`Idempotency-Key` header) and provides a polling-based expiry scheduler for stale
  authorizations.
- Supports both CARD (auth/capture/void/refund, optional 3DS) and UPI (intent/collect, PSP callback-driven) payment
  methods with materially different flows.

## Tech stack

- Spring Boot (Java 21), Gradle multi-module
- Default port: **8087** (`server.port: ${SERVER_PORT:8087}` in `application.yml`)
- Datastore: PostgreSQL (`jdbc:postgresql://localhost:5432/payments`), schema managed via Hibernate `ddl-auto: update` —
  **no Flyway** in this service (schema is not migration-versioned; a `data.sql` seed file loads the
  `intent_transitions` allow-list on every startup via `spring.sql.init.mode: always`)
- HTTP: Spring WebClient for all outbound connector/service calls
- No Kafka — events flow through a transactional outbox table polled by a scheduler
- Optimistic locking (`@Version`) on `PaymentIntent`

## API surface

**`PaymentController`** — base path `/v1/payments`:

- `POST /v1/payments` — create a payment intent (optional `Idempotency-Key` header)
- `POST /v1/payments/{id}/authorize` — run limit reserve → fraud check → connector authorize (card), or UPI collect
- `POST /v1/payments/{id}/upi-intent` — non-obvious: required first step for UPI when no `payerVpa` was supplied at
  creation; generates a PSP intent / deep link
- `POST /v1/payments/{id}/capture` — full or partial capture (card only)
- `POST /v1/payments/{id}/void` — cancel an authorization with zero captured amount (card only)
- `POST /v1/payments/{id}/refund` — full or partial refund, routed to card or UPI connector
- `POST /v1/payments/{id}/confirm` — direct transition to `CONFIRMED`
- `POST /v1/payments/{id}/retry` — non-obvious: only valid from `FAILED`; resets to `CREATED` and re-runs the entire
  authorize flow (reserve → fraud → connector) from scratch
- `GET /v1/payments/{id}`, `GET /v1/payments/{id}/timeline`, `GET /v1/payments` (search/filter/paged)

**`CallbackController`** — base path `/v1/callbacks`:

- `POST /v1/callbacks/upi` — PSP-initiated UPI debit callback; on success collapses authorize+capture+confirm into a
  single transition sequence
- `POST /v1/callbacks/3ds` — 3DS ACS callback continuing a `PENDING` card authorization

Note: there is no endpoint to flag/clear a "disputed" state — dispute-service's `WebPaymentClient` looks for one and
currently just logs when it can't find it.

## Data model

Key entities (`entity/` package): `PaymentIntent` (aggregate root — amounts, status, subStatus, connectorId,
limitReservationId), `Authorization`, `Capture`, `Refund`, `PaymentVoid`, `PaymentAttempt` (per-connector-call
outcome/decline-code/latency log), `ThreeDsSession`, `IdempotencyKey`, `Outbox`.

**State machine / transition allow-list**: `IntentTransition` (table `intent_transitions`, composite PK `fromStatus`+
`toStatus`) is a pure data table of legal `(from, to)` status pairs, seeded by `data.sql` with 23 rows (e.g.
`CREATED→AUTHORIZED`, `AUTHORIZED→CAPTURED/PARTIALLY_CAPTURED/CANCELLED/EXPIRED/FAILED`,
`CAPTURED→CONFIRMED/PARTIALLY_REFUNDED/REFUNDED`, `FAILED→CREATED` for retry, plus self-loops for the partial states).
`PaymentStateMachine.assertCanTransition` checks `IntentTransitionRepository.existsByFromStatusAndToStatus(from, to)`
before every status change and throws `InvalidStateTransitionException` if the pair isn't in the table. This means the
legal transition graph can be inspected or amended as data without touching orchestration code.

## Inter-service integration

- **LimitClient** → limit-service (`http://localhost:8085`), real endpoints `POST /api/v1/reservations`,
  `POST /api/v1/reservations/{id}/commit`, `POST /api/v1/reservations/{id}/release`. Used only in the **card** authorize
  flow, saga-style:
    - **reserve** — first step of card authorization, before the fraud check; a decline here (or an unreachable
      limit-service, which fails closed) aborts before fraud/connector are ever called.
    - **release** — on fraud block, on connector auth decline, and on void.
    - **commit** — only once a card authorization reaches a full capture.
    - Not called at all for UPI, refunds, or the expiry scheduler (expired authorizations do not currently release their
      limit reservation — a gap worth knowing about).
- **FraudClient** → fraud-service (`http://localhost:8088`), `POST /api/fraud/evaluate`. Runs after a successful limit
  reserve, before the connector call (card only). Fails **open** (auto-approves) on timeout/error, unlike LimitClient
  which fails closed.
- **VaultClient** → tokenization-service (`http://localhost:8084`), `GET /api/instruments/{id}`, for masked-instrument
  lookups. Wired but currently unused by `PaymentOrchestrationService` — dormant infra, not part of the live
  authorize/capture path.
- **CardHttpConnector** (`http://localhost:9101`) and **UpiHttpConnector** (`http://localhost:9102`) — these are *
  *external PSP/bank simulators**, not other services in this platform. They stand in for the real card network / UPI
  PSP that payment-service would talk to in production and are intentionally outside the scope of this repo's service
  mesh.
- **Inbound**: gateway-service routes `/api/v1/payments/**` and `/api/v1/charges/**` to payment-service (note: gateway's
  default route URI is `http://payment-service:8080`, a Docker/Eureka-resolved hostname that does not match this
  service's local default port 8087 — reconcile via environment override when running outside containers). No other
  in-repo service calls payment-service's HTTP API directly.
- **Events**: no Kafka. `OutboxWriter` appends events in the same DB transaction as each state change; `OutboxPublisher`
  polls unpublished rows every `payment.outbox.poll-ms` (default 5000ms) and POSTs them to `payment.outbox.sink-url` (
  empty by default, so events are just logged, not delivered).

## Running locally

```
./gradlew :payment-service:bootRun
```

Key env vars (all optional, defaults shown):

- `SERVER_PORT` (8087), `DB_URL`/`DB_USER`/`DB_PASSWORD` (postgres db `payments`)
- `LIMIT_SERVICE_URL` (http://localhost:8085), `LIMIT_ENABLED` (true)
- `FRAUD_SERVICE_URL` (http://localhost:8088), `FRAUD_ENABLED` (true)
- `TOKENIZATION_SERVICE_URL` (http://localhost:8084), `VAULT_ENABLED` (true)
- `CARD_BASE_URL` (http://localhost:9101), `UPI_BASE_URL` (http://localhost:9102) — external simulators
- `CARD_AUTH_EXPIRY` (604800s), `UPI_AUTH_EXPIRY` (300s)
- `OUTBOX_SINK_URL` (empty = log-only)

Requires a running PostgreSQL instance with a `payments` database; schema is created automatically on boot (
`ddl-auto: update`).

## Design notes

- **Reserve/fraud/commit as a saga, not a distributed transaction.** payment-service never uses 2PC with limit-service;
  instead it reserves funds first (cheap to reverse), only calls fraud after a successful reserve, and only commits the
  reservation once capture fully lands — with explicit compensating `release` calls on every failure branch (fraud
  block, decline, void). This bounds the blast radius of a failed downstream call to "release a reservation" rather than
  requiring rollback of committed state.
- **State transitions as data, not code.** The `intent_transitions` table plus `PaymentStateMachine.assertCanTransition`
  means the legal status graph is auditable and changeable via a data seed rather than buried in conditional logic —
  useful for reasoning about correctness and for future admin tooling.
- **Asymmetric failure modes for downstream calls are deliberate, not accidental**: LimitClient fails closed (an
  unreachable limit-service blocks the payment) because getting limit enforcement wrong risks real money; FraudClient
  fails open (an unreachable fraud-service lets the payment proceed) because blocking all traffic on a fraud-service
  outage is worse than accepting slightly elevated risk temporarily.
- **UPI bypasses limit/fraud entirely.** Only the CARD path calls LimitClient/FraudClient; UPI goes straight to the PSP
  connector. This is a real asymmetry in the current implementation, not a documentation gap — worth flagging if UPI
  risk controls are expected to match card.
