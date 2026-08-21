const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");

const SERVICES = [
  ["gateway-service", 8443],
  ["authentication-service", 8081],
  ["user-service", 8082],
  ["merchant-service", 8083],
  ["tokenization-service", 8084],
  ["limit-service", 8085],
  ["authorization-service", 8086],
  ["payment-service", 8087],
  ["fraud-service", 8088],
  ["clearing-service", 8089],
  ["dispute-service", 8090],
  ["settlement-service", 8091],
  ["ledger-service", 8092],
  ["reconciliation-service", 8093],
  ["notification-service", 8094],
  ["audit-service", 8095],
  ["reporting-service", 8096],
];

function dbName(service) {
  return service.replace(/-/g, "") + "db";
}

function write(file, content) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content, "utf8");
}

for (const [service, port] of SERVICES) {
  write(
    path.join(ROOT, service, "Dockerfile"),
    `# Build from repository root:
#   docker build -f ${service}/Dockerfile -t ${service}:local .
#
# Or use the shared file:
#   docker build -f docker/Dockerfile.service --build-arg SERVICE_NAME=${service} --build-arg SERVICE_PORT=${port} -t ${service}:local .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY ${service} ./${service}
RUN chmod +x gradlew && ./gradlew --no-daemon :${service}:bootJar -x test \\
    && cp \$(ls ${service}/build/libs/*.jar | head -n 1) /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \\
    && apt-get install -y --no-install-recommends curl \\
    && rm -rf /var/lib/apt/lists/* \\
    && groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \\
    SERVER_PORT=${port} \\
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \\
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces
EXPOSE ${port}
USER app
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \\
  CMD curl -fsS http://127.0.0.1:${port}/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "java \\$JAVA_OPTS -jar /app/app.jar"]
`
  );

  const resources = path.join(ROOT, service, "src", "main", "resources");
  if (fs.existsSync(resources)) {
    write(
      path.join(resources, "application-vault.yml"),
      `# Activate: SPRING_PROFILES_ACTIVE=...,vault
spring:
  cloud:
    vault:
      enabled: \${VAULT_ENABLED:false}
      uri: \${VAULT_URI:http://localhost:8200}
      authentication: \${VAULT_AUTHENTICATION:TOKEN}
      token: \${VAULT_TOKEN:root}
      fail-fast: false
      kv:
        enabled: true
        backend: secret
        default-context: paymentprocessor/${service}
  config:
    import: "optional:vault://"
`
    );
  }

  const k8s = path.join(ROOT, "k8s", "services", service);

  write(
    path.join(k8s, "deployment.yaml"),
    `apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service}
  namespace: payment-processor
  labels:
    app: ${service}
    app.kubernetes.io/part-of: payment-processor
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${service}
  template:
    metadata:
      labels:
        app: ${service}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "${port}"
        prometheus.io/path: /actuator/prometheus
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "paymentprocessor"
        vault.hashicorp.com/agent-inject-secret-db: "secret/data/paymentprocessor/${service}"
    spec:
      serviceAccountName: paymentprocessor
      containers:
        - name: ${service}
          image: paymentprocessor/${service}:latest
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: ${port}
          envFrom:
            - configMapRef:
                name: ${service}-config
            - secretRef:
                name: ${service}-secret
                optional: true
          env:
            - name: SERVER_PORT
              value: "${port}"
            - name: SPRING_PROFILES_ACTIVE
              value: prod,vault
            - name: VAULT_ENABLED
              value: "true"
            - name: VAULT_URI
              valueFrom:
                configMapKeyRef:
                  name: platform-config
                  key: VAULT_URI
            - name: VAULT_AUTHENTICATION
              value: KUBERNETES
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
            - name: KAFKA_BOOTSTRAP_SERVERS
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
`
  );

  write(
    path.join(k8s, "service.yaml"),
    `apiVersion: v1
kind: Service
metadata:
  name: ${service}
  namespace: payment-processor
  labels:
    app: ${service}
spec:
  selector:
    app: ${service}
  ports:
    - name: http
      port: ${port}
      targetPort: http
  type: ClusterIP
`
  );

  write(
    path.join(k8s, "configmap.yaml"),
    `apiVersion: v1
kind: ConfigMap
metadata:
  name: ${service}-config
  namespace: payment-processor
data:
  SPRING_APPLICATION_NAME: "${service}"
  SERVER_PORT: "${port}"
  TRACING_SAMPLING_PROBABILITY: "0.1"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics,prometheus"
  DB_URL: "jdbc:postgresql://postgres:5432/${dbName(service)}"
  DB_USERNAME: "postgres"
  DB_USER: "postgres"
`
  );

  write(
    path.join(k8s, "secret.yaml"),
    `# Placeholder only — prefer HashiCorp Vault for DB passwords.
# Do not commit real credentials.
apiVersion: v1
kind: Secret
metadata:
  name: ${service}-secret
  namespace: payment-processor
type: Opaque
stringData:
  DB_PASSWORD: "change-me"
  SPRING_DATASOURCE_PASSWORD: "change-me"
`
  );

  write(
    path.join(k8s, "kustomization.yaml"),
    `apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: payment-processor
resources:
  - deployment.yaml
  - service.yaml
  - configmap.yaml
  - secret.yaml
images:
  - name: paymentprocessor/${service}
    newName: paymentprocessor/${service}
    newTag: latest
`
  );

  console.log("generated", service);
}

console.log("done");
