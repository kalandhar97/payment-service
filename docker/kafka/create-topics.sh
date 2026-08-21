#!/usr/bin/env bash
# Creates one Kafka topic per platform service: {servicename}topic
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:19092}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-3}"
REPLICATION="${KAFKA_TOPIC_REPLICATION:-1}"
KAFKA_TOPICS="${KAFKA_TOPICS_BIN:-/opt/kafka/bin/kafka-topics.sh}"

TOPICS=(
  gatewayservicetopic
  authenticationservicetopic
  userservicetopic
  merchantservicetopic
  tokenizationservicetopic
  limitservicetopic
  authorizationservicetopic
  paymentservicetopic
  fraudservicetopic
  clearingservicetopic
  disputeservicetopic
  settlementservicetopic
  ledgerservicetopic
  reconciliationservicetopic
  notificationservicetopic
  auditservicetopic
  reportingservicetopic
  auditservicetopic.DLT
)

echo "Waiting for Kafka at ${BOOTSTRAP}..."
for i in $(seq 1 30); do
  if "${KAFKA_TOPICS}" --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

for topic in "${TOPICS[@]}"; do
  echo "Ensuring topic: ${topic}"
  "${KAFKA_TOPICS}" --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION}"
done

echo "Kafka topics ready."
