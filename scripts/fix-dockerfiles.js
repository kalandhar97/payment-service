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

const COPY_MODULES = `COPY gateway-service authentication-service user-service merchant-service tokenization-service \\
     limit-service authorization-service payment-service fraud-service clearing-service \\
     dispute-service settlement-service ledger-service reconciliation-service notification-service \\
     audit-service reporting-service configuration-repo ./`;

for (const [service, port] of SERVICES) {
  const lines = [
    `# Build from repository root:`,
    `#   docker build -f ${service}/Dockerfile -t ${service}:local .`,
    `#`,
    `# Shared alternative:`,
    `#   docker build -f docker/Dockerfile.service --build-arg SERVICE_NAME=${service} --build-arg SERVICE_PORT=${port} -t ${service}:local .`,
    ``,
    `FROM eclipse-temurin:21-jdk-jammy AS build`,
    `WORKDIR /workspace`,
    `COPY gradlew settings.gradle build.gradle ./`,
    `COPY gradle ./gradle`,
    COPY_MODULES,
    `RUN chmod +x gradlew \\`,
    `    && ./gradlew --no-daemon :${service}:bootJar -x test \\`,
    `    && cp $(ls ${service}/build/libs/*.jar | head -n 1) /workspace/app.jar`,
    ``,
    `FROM eclipse-temurin:21-jre-jammy`,
    `WORKDIR /app`,
    `RUN apt-get update \\`,
    `    && apt-get install -y --no-install-recommends curl \\`,
    `    && rm -rf /var/lib/apt/lists/* \\`,
    `    && groupadd --system app && useradd --system --gid app app`,
    `COPY --from=build /workspace/app.jar /app/app.jar`,
    `ENV SPRING_PROFILES_ACTIVE=prod \\`,
    `    SERVER_PORT=${port} \\`,
    `    JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \\`,
    `    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces \\`,
    `    TRACING_SAMPLING_PROBABILITY=0.1`,
    `EXPOSE ${port}`,
    `USER app`,
    `HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \\`,
    `  CMD curl -fsS http://127.0.0.1:${port}/actuator/health/liveness || exit 1`,
    `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]`,
    ``,
  ];
  fs.writeFileSync(path.join(ROOT, service, "Dockerfile"), lines.join("\n"), "utf8");
  console.log("fixed", service);
}
