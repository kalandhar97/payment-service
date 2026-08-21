# Observability (LGTM + OpenTelemetry)

Platform observability uses the Grafana LGTM stack with an OpenTelemetry Collector
as the single trace export target from apps.

```
Spring Boot services
  │
  ├── Metrics  Micrometer → /actuator/prometheus ──scrape──► Prometheus ──┐
  │                                                                       │
  ├── Traces   Micrometer Tracing → OTel bridge → OTLP ──► Collector ──► Tempo ──┤── Grafana
  │                              (localhost:4318)              │          │
  │                                                            └─► (swap backends here)
  └── Logs     JSON stdout + logs/<service>.json.log ──► Alloy ──► Loki ──┘
```

## Why this split

| Signal  | Path                                          | Why                                                           |
|---------|-----------------------------------------------|---------------------------------------------------------------|
| Metrics | Micrometer → Prometheus scrape                | Already native via Actuator; simple and cheap                 |
| Traces  | Micrometer Tracing → OTel → Collector → Tempo | Grafana-native; change Tempo/Jaeger without touching services |
| Logs    | Structured JSON → Alloy → Loki                | Stdout-friendly; correlates to traces via `traceId`           |

## Start the stack

```bash
# Kafka + observability
docker compose --profile observability up -d

# Observability only
docker compose --profile observability up -d otel-collector prometheus loki tempo grafana alloy
```

| Component  | URL                   | Notes                   |
|------------|-----------------------|-------------------------|
| Grafana    | http://localhost:3000 | `admin` / `admin`       |
| Prometheus | http://localhost:9090 | Targets → scrape status |
| Loki       | http://localhost:3100 | Queried via Grafana     |
| Tempo      | http://localhost:3200 | Queried via Grafana     |
| OTLP HTTP  | http://localhost:4318 | App trace export        |
| OTLP gRPC  | localhost:4317        | Collector / Tempo       |

Datasources (Prometheus, Loki, Tempo) and an overview dashboard are provisioned
automatically. Trace↔log links use the `traceId` MDC field in JSON logs.

## App configuration (already applied to all 17 services)

**Dependencies** (each `build.gradle`):

- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`
- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-otlp`
- `logstash-logback-encoder`

**Config** (`application.yml` + `configuration-repo/application.yml`):

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  tracing.sampling.probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  otlp.tracing.endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

**Logging**: shared `logback-spring.xml` writes JSON to stdout and `logs/<service>.json.log`.
Alloy tails `./logs`. Use Spring profile `plain-logs` for human-readable console output
(file stays JSON for Loki).

## Verify

1. Start observability stack (above).
2. Run any service, e.g. `./gradlew :payment-service:bootRun`.
3. Hit an endpoint, then check:
    - Metrics: `http://localhost:8087/actuator/prometheus`
    - Prometheus UI → Status → Targets (service should go UP once scraped)
    - Grafana → Explore → Tempo (search by service name)
    - Grafana → Explore → Loki → `{service="payment-service"}`
4. From a Loki log line with `traceId`, use the derived **View Trace** link.

## Environment knobs

| Variable                       | Default                           | Purpose                        |
|--------------------------------|-----------------------------------|--------------------------------|
| `OTEL_EXPORTER_OTLP_ENDPOINT`  | `http://localhost:4318/v1/traces` | Collector OTLP HTTP traces URL |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` (local)                     | Use `0.1` in prod              |

## Changing backends

Only edit `docker/observability/otel-collector/otel-collector-config.yml` exporters.
Apps keep sending OTLP to the Collector.
