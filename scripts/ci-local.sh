#!/usr/bin/env bash
# Local CI parity: compile, test, jacoco, optional sonar + dependency-check.
# Usage: ./scripts/ci-local.sh [--sonar] [--owasp]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
chmod +x gradlew

./gradlew --no-daemon clean classes test jacocoTestReport

if [[ "$*" == *"--owasp"* ]]; then
  ./gradlew --no-daemon dependencyCheckAnalyze
fi

if [[ "$*" == *"--sonar"* ]]; then
  if [[ -z "${SONAR_TOKEN:-}" || -z "${SONAR_HOST_URL:-}" ]]; then
    echo "Set SONAR_TOKEN and SONAR_HOST_URL for SonarQube." >&2
    exit 1
  fi
  ./gradlew --no-daemon sonar \
    -Dsonar.host.url="$SONAR_HOST_URL" \
    -Dsonar.token="$SONAR_TOKEN"
fi

echo "Local CI finished."
