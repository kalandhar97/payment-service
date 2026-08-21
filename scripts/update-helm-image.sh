#!/usr/bin/env bash
# Update Helm values image repository/tag for GitOps (Argo CD).
# Usage: ./scripts/update-helm-image.sh <repository> <tag>
set -euo pipefail

REPO="${1:?image repository required}"
TAG="${2:?image tag required}"
VALUES_FILE="${3:-helm/payment-service/values.yaml}"

if [[ ! -f "$VALUES_FILE" ]]; then
  echo "values file not found: $VALUES_FILE" >&2
  exit 1
fi

# Portable in-place edit (GNU/BSD sed)
tmp="$(mktemp)"
awk -v repo="$REPO" -v tag="$TAG" '
  BEGIN { in_image=0 }
  /^image:/ { in_image=1; print; next }
  in_image && /^[[:space:]]+repository:/ {
    sub(/repository:.*/, "repository: " repo)
    print
    next
  }
  in_image && /^[[:space:]]+tag:/ {
    sub(/tag:.*/, "tag: \"" tag "\"")
    print
    in_image=0
    next
  }
  /^[^[:space:]]/ { in_image=0 }
  { print }
' "$VALUES_FILE" > "$tmp"
mv "$tmp" "$VALUES_FILE"

echo "Updated $VALUES_FILE → ${REPO}:${TAG}"
