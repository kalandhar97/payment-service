-- Seed of the payment-intent state machine. A row (from_status, to_status) means
-- the transition is permitted; the PaymentStateMachine rejects anything absent here.
-- Idempotent so it can run on every boot (Postgres ON CONFLICT).
INSERT INTO intent_transitions (from_status, to_status) VALUES
  ('CREATED', 'AUTHORIZED'),
  ('CREATED', 'FAILED'),
  ('CREATED', 'EXPIRED'),
  ('FAILED', 'CREATED'),
  ('AUTHORIZED', 'CAPTURED'),
  ('AUTHORIZED', 'PARTIALLY_CAPTURED'),
  ('AUTHORIZED', 'CANCELLED'),
  ('AUTHORIZED', 'EXPIRED'),
  ('AUTHORIZED', 'FAILED'),
  ('PARTIALLY_CAPTURED', 'PARTIALLY_CAPTURED'),
  ('PARTIALLY_CAPTURED', 'CAPTURED'),
  ('PARTIALLY_CAPTURED', 'CONFIRMED'),
  ('PARTIALLY_CAPTURED', 'EXPIRED'),
  ('CAPTURED', 'CONFIRMED'),
  ('CAPTURED', 'PARTIALLY_REFUNDED'),
  ('CAPTURED', 'REFUNDED'),
  ('CONFIRMED', 'SETTLED'),
  ('CONFIRMED', 'PARTIALLY_REFUNDED'),
  ('CONFIRMED', 'REFUNDED'),
  ('SETTLED', 'PARTIALLY_REFUNDED'),
  ('SETTLED', 'REFUNDED'),
  ('PARTIALLY_REFUNDED', 'PARTIALLY_REFUNDED'),
  ('PARTIALLY_REFUNDED', 'REFUNDED')
ON CONFLICT (from_status, to_status) DO NOTHING;
