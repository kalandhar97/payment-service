#!/bin/sh
# Seed KV v2 secrets for every payment-processor service (dev only).
set -eu

SERVICES="
gateway-service
authentication-service
user-service
merchant-service
tokenization-service
limit-service
authorization-service
payment-service
fraud-service
clearing-service
dispute-service
settlement-service
ledger-service
reconciliation-service
notification-service
audit-service
reporting-service
"

echo "Enabling KV v2 at secret/ ..."
vault secrets enable -path=secret kv-v2 2>/dev/null || true

for svc in $SERVICES; do
  echo "Writing secret/paymentprocessor/${svc}"
  vault kv put "secret/paymentprocessor/${svc}" \
    DB_PASSWORD="Mysql@2022" \
    spring.datasource.password="Mysql@2022" \
    REDIS_PASSWORD="" \
    TLS_KEYSTORE_PASSWORD="change-me"
done

echo "Vault seed complete."
vault kv list secret/paymentprocessor || true
