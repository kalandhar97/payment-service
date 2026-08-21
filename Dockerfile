# Build from this service directory:
#   docker build -t ghcr.io/<org>/payment-service:local .
#
# Shared monorepo alternative (from PaymentProcessorApp root):
#   docker build -f docker/Dockerfile.service \
#     --build-arg SERVICE_NAME=payment-service --build-arg SERVICE_PORT=8087 \
#     -t payment-service:local .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew \
    && ./gradlew --no-daemon bootJar -x test \
    && cp $(ls build/libs/*.jar | grep -v plain | head -n 1) /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8087 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC" \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces \
    TRACING_SAMPLING_PROBABILITY=0.1
EXPOSE 8087
USER app
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8087/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
