# Build from repository root:
#   docker build -f payment-service/Dockerfile -t payment-service:local .
#
# Shared alternative:
#   docker build -f docker/Dockerfile.service --build-arg SERVICE_NAME=payment-service --build-arg SERVICE_PORT=8087 -t payment-service:local .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gateway-service authentication-service user-service merchant-service tokenization-service \
     limit-service authorization-service payment-service fraud-service clearing-service \
     dispute-service settlement-service ledger-service reconciliation-service notification-service \
     audit-service reporting-service configuration-repo ./
RUN chmod +x gradlew \
    && ./gradlew --no-daemon :payment-service:bootJar -x test \
    && cp $(ls payment-service/build/libs/*.jar | head -n 1) /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8087 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces \
    TRACING_SAMPLING_PROBABILITY=0.1
EXPOSE 8087
USER app
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8087/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
