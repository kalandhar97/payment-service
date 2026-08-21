# Observability

## Metrics

- Spring Actuator
- Micrometer
- Prometheus
- Grafana

```text
Spring Boot ↓ Micrometer ↓ /actuator/prometheus ↓ Prometheus ↓ Grafana
```

## Distributed Tracing

- OpenTelemetry
- Micrometer Tracing
- OpenTelemetry Collector
- Grafana Tempo

```text
Spring Boot ↓ Micrometer Tracing ↓ OpenTelemetry ↓ OTel Collector ↓ Tempo ↓ Grafana
```

## Logs

- Structured JSON logs
- Loki
- Grafana Alloy

```text
Spring Boot
    ↓
JSON Logs
    ↓
Grafana Alloy
    ↓
Loki
    ↓
Grafana
```

## Complete Observability

```text
                ┌──→ Prometheus ──→ Grafana 
                |
Spring Boot  ──→ OTel Collector ──→ Tempo ──→ Grafana 
                │ 
                └──→ Loki ────────→ Grafana
```