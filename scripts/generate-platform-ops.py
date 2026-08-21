#!/usr/bin/env python3
"""Generate per-service Dockerfiles and Kubernetes manifests."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SERVICES = [
    ("gateway-service", 8443),
    ("authentication-service", 8081),
    ("user-service", 8082),
    ("merchant-service", 8083),
    ("tokenization-service", 8084),
    ("limit-service", 8085),
    ("authorization-service", 8086),
    ("payment-service", 8087),
    ("fraud-service", 8088),
    ("clearing-service", 8089),
    ("dispute-service", 8090),
    ("settlement-service", 8091),
    ("ledger-service", 8092),
    ("reconciliation-service", 8093),
    ("notification-service", 8094),
    ("audit-service", 8095),
    ("reporting-service", 8096),
]

DOCKERFILE = """\
# {service} — thin wrapper around shared multi-stage build
# Prefer building from repo root:
#   docker build -f docker/Dockerfile.service --build-arg SERVICE_NAME={service} --build-arg SERVICE_PORT={port} -t {service}:local .
FROM eclipse-temurin:21-jre-jammy
# Placeholder: CI/CD uses docker/Dockerfile.service. This file documents the service port.
LABEL org.opencontainers.image.title="{service}"
LABEL com.paymentprocessor.service="{service}"
LABEL com.paymentprocessor.port="{port}"
"""

DEPLOYMENT = """\
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {service}
  namespace: payment-processor
  labels:
    app: {service}
    app.kubernetes.io/part-of: payment-processor
spec:
  replicas: 1
  selector:
    matchLabels:
      app: {service}
  template:
    metadata:
      labels:
        app: {service}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "{port}"
        prometheus.io/path: /actuator/prometheus
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "paymentprocessor"
        vault.hashicorp.com/agent-inject-secret-db: "secret/data/paymentprocessor/{service}"
        vault.hashicorp.com/agent-inject-template-db: |
          {{{{- with secret "secret/data/paymentprocessor/{service}" -}}}}
          export DB_PASSWORD="{{{{ .Data.data.DB_PASSWORD }}}}"
          export SPRING_DATASOURCE_PASSWORD="{{{{ .Data.data.DB_PASSWORD }}}}"
          {{{{- end -}}}}
    spec:
      serviceAccountName: paymentprocessor
      containers:
        - name: {service}
          image: ${{ECR_REGISTRY}}/{service}:${{IMAGE_TAG}}
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: {port}
          envFrom:
            - configMapRef:
                name: {service}-config
            - secretRef:
                name: {service}-secret
                optional: true
          env:
            - name: SERVER_PORT
              value: "{port}"
            - name: SPRING_PROFILES_ACTIVE
              value: prod,vault
            - name: VAULT_ENABLED
              value: "true"
            - name: VAULT_URI
              valueFrom:
                configMapKeyRef:
                  name: platform-config
                  key: VAULT_URI
            - name: OTEL_EXPORTER_OTLP_ENDPOINT
              valueFrom:
                configMapKeyRef:
                  name: platform-config
                  key: OTEL_EXPORTER_OTLP_ENDPOINT
            - name: KAFKA_BOOTSTRAP
              valueFrom:
                configMapKeyRef:
                  name: platform-config
                  key: KAFKA_BOOTSTRAP
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            initialDelaySeconds: 40
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 60
            periodSeconds: 20
          resources:
            requests:
              cpu: 100m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
"""

SERVICE_YAML = """\
apiVersion: v1
kind: Service
metadata:
  name: {service}
  namespace: payment-processor
  labels:
    app: {service}
spec:
  selector:
    app: {service}
  ports:
    - name: http
      port: {port}
      targetPort: http
  type: ClusterIP
"""

CONFIGMAP = """\
apiVersion: v1
kind: ConfigMap
metadata:
  name: {service}-config
  namespace: payment-processor
data:
  SPRING_APPLICATION_NAME: "{service}"
  SERVER_PORT: "{port}"
  TRACING_SAMPLING_PROBABILITY: "0.1"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics,prometheus"
  DB_URL: "jdbc:postgresql://postgres:5432/{db}"
  DB_USERNAME: "postgres"
  DB_USER: "postgres"
"""

SECRET = """\
# Placeholder Secret — prefer HashiCorp Vault (Agent Injector / Spring Cloud Vault).
# Apply only for local/dev bootstrap. Never commit real passwords.
apiVersion: v1
kind: Secret
metadata:
  name: {service}-secret
  namespace: payment-processor
type: Opaque
stringData:
  DB_PASSWORD: "change-me"
  SPRING_DATASOURCE_PASSWORD: "change-me"
"""

KUSTOMIZATION = """\
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: payment-processor
resources:
  - deployment.yaml
  - service.yaml
  - configmap.yaml
  - secret.yaml
images:
  - name: ${{ECR_REGISTRY}}/{service}
    newName: paymentprocessor/{service}
    newTag: latest
"""

APP_VAULT = """\
# Optional local profile copy — prefer configuration-repo/application-vault.yml via Config Server.
# Activate: SPRING_PROFILES_ACTIVE=local,vault  (or prod,vault)
spring:
  cloud:
    vault:
      enabled: ${{VAULT_ENABLED:false}}
      uri: ${{VAULT_URI:http://localhost:8200}}
      authentication: ${{VAULT_AUTHENTICATION:TOKEN}}
      token: ${{VAULT_TOKEN:root}}
      fail-fast: false
      kv:
        enabled: true
        backend: secret
        default-context: paymentprocessor/{service}
  config:
    import: "optional:vault://"
"""


def db_name(service: str) -> str:
    return service.replace("-", "") + "db"


def main() -> None:
    for service, port in SERVICES:
        svc_dir = ROOT / service
        # Dockerfile (documents port; CI uses shared docker/Dockerfile.service)
        (svc_dir / "Dockerfile").write_text(
            DOCKERFILE.format(service=service, port=port), encoding="utf-8"
        )
        # application-vault.yml in each service for bootRun without config server
        resources = svc_dir / "src" / "main" / "resources"
        if resources.is_dir():
            (resources / "application-vault.yml").write_text(
                APP_VAULT.format(service=service), encoding="utf-8"
            )

        k8s_dir = ROOT / "k8s" / "services" / service
        k8s_dir.mkdir(parents=True, exist_ok=True)
        (k8s_dir / "deployment.yaml").write_text(
            DEPLOYMENT.format(service=service, port=port), encoding="utf-8"
        )
        (k8s_dir / "service.yaml").write_text(
            SERVICE_YAML.format(service=service, port=port), encoding="utf-8"
        )
        (k8s_dir / "configmap.yaml").write_text(
            CONFIGMAP.format(service=service, port=port, db=db_name(service)),
            encoding="utf-8",
        )
        (k8s_dir / "secret.yaml").write_text(
            SECRET.format(service=service), encoding="utf-8"
        )
        (k8s_dir / "kustomization.yaml").write_text(
            KUSTOMIZATION.format(service=service), encoding="utf-8"
        )
        print(f"generated {service}")


if __name__ == "__main__":
    main()
