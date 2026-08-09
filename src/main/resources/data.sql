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

-- ---------------------------------------------------------------------------
-- Sample payment data for local/dev/test use. This file runs in every profile
-- (spring.sql.init.mode=always, no profile gating for this service — see
-- application.yml), so keep these rows harmless: fixed, clearly-fake ids,
-- fake card/UPI references, no real PII. Reuses the merchant id seeded by
-- merchant-service's V2__seed_sample_data.sql for cross-service consistency.
--
-- 1) A completed CARD payment: CREATED -> AUTHORIZED -> CAPTURED, with its
--    matching authorization row.
-- 2) A CARD payment still awaiting capture: CREATED -> AUTHORIZED.
-- 3) A UPI collect payment still in progress: CREATED.
-- ---------------------------------------------------------------------------

INSERT INTO payment_intents (
  id, merchant_id, customer_id, payment_method, instrument_token, payer_vpa,
  amount_minor, currency, status, sub_status, capture_method, connector_id,
  limit_reservation_id, authorized_minor, captured_minor, refunded_minor,
  statement_descriptor, description, metadata, version, created_at, updated_at
) VALUES (
  'pi_11111111111111111111111111111111',
  '11111111-1111-1111-1111-111111111111',
  'cust_1001',
  'CARD',
  'tok_seed_card_0001',
  NULL,
  250000,
  'USD',
  'CAPTURED',
  NULL,
  'AUTOMATIC',
  'card-connector-sim',
  NULL,
  250000,
  250000,
  0,
  'ACME RETAIL',
  'Order ORD-90210',
  '{"orderId":"ORD-90210"}',
  0,
  now() - interval '2 hours',
  now() - interval '1 hour'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO authorizations (
  id, intent_id, attempt_id, amount_minor, currency, status, auth_code, network_txn_id,
  rrn, arn, avs_result, cvv_result, eci, three_ds_session_id, expires_at, created_at
) VALUES (
  'auth_11111111111111111111111111111111',
  'pi_11111111111111111111111111111111',
  NULL,
  250000,
  'USD',
  'APPROVED',
  'A1B2C3',
  'NTXN-SEED-0001',
  'RRN000000000001',
  'ARN00000000000000000001',
  'Y',
  'M',
  '05',
  NULL,
  now() + interval '7 days',
  now() - interval '2 hours'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payment_intents (
  id, merchant_id, customer_id, payment_method, instrument_token, payer_vpa,
  amount_minor, currency, status, sub_status, capture_method, connector_id,
  limit_reservation_id, authorized_minor, captured_minor, refunded_minor,
  statement_descriptor, description, metadata, version, created_at, updated_at
) VALUES (
  'pi_22222222222222222222222222222222',
  '11111111-1111-1111-1111-111111111111',
  'cust_1002',
  'CARD',
  'tok_seed_card_0002',
  NULL,
  9999,
  'USD',
  'AUTHORIZED',
  NULL,
  'MANUAL',
  'card-connector-sim',
  NULL,
  9999,
  0,
  0,
  'ACME RETAIL',
  'Order ORD-90211',
  '{"orderId":"ORD-90211"}',
  0,
  now() - interval '10 minutes',
  now() - interval '9 minutes'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payment_intents (
  id, merchant_id, customer_id, payment_method, instrument_token, payer_vpa,
  amount_minor, currency, status, sub_status, capture_method, connector_id,
  limit_reservation_id, authorized_minor, captured_minor, refunded_minor,
  statement_descriptor, description, metadata, version, created_at, updated_at
) VALUES (
  'pi_33333333333333333333333333333333',
  '11111111-1111-1111-1111-111111111111',
  'cust_1003',
  'UPI',
  NULL,
  'jane.doe@acmeupi',
  50000,
  'INR',
  'CREATED',
  NULL,
  'AUTOMATIC',
  NULL,
  NULL,
  0,
  0,
  0,
  'ACME RETAIL',
  'Order ORD-90212',
  '{"orderId":"ORD-90212"}',
  0,
  now() - interval '1 minute',
  now() - interval '1 minute'
) ON CONFLICT (id) DO NOTHING;
