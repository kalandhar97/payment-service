#!/usr/bin/env bash
# Push image to GitHub Container Registry.
# Usage: ./scripts/docker-push.sh [tag]
# Requires: docker login ghcr.io
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:?tag required, e.g. ghcr.io/org/payment-service:1.0.0}"

cd "$ROOT"
docker build -f Dockerfile -t "$TAG" .
docker push "$TAG"
echo "Pushed $TAG"
