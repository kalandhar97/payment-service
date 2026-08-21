#!/usr/bin/env bash
# Start the local LGTM + OpenTelemetry observability stack.
# Usage: ./scripts/observability-up.sh [--down]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p logs

if [[ "${1:-}" == "--down" ]]; then
  docker compose --profile observability down
  echo "Observability stack stopped."
  exit 0
fi

docker compose --profile observability up -d
echo ""
echo "Observability stack is up:"
echo "  Grafana     http://localhost:3000  (admin / admin)"
echo "  Prometheus  http://localhost:9090"
echo "  Loki        http://localhost:3100"
echo "  Tempo       http://localhost:3200"
echo "  OTLP HTTP   http://localhost:4318"
echo ""
echo "Point payment-service at the collector:"
echo "  OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces"
