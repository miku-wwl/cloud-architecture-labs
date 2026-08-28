#!/usr/bin/env bash
set -euo pipefail
endpoint="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
for _ in $(seq 1 30); do
  health="$(curl -fsS "$endpoint/_localstack/health" 2>/dev/null || true)"
  if grep -Eq '"dynamodb"[[:space:]]*:[[:space:]]*"running"' <<<"$health" && \
     grep -Eq '"lambda"[[:space:]]*:[[:space:]]*"running"' <<<"$health"; then
    echo "LocalStack ready: $endpoint"
    exit 0
  fi
  sleep 2
done
echo "LocalStack not ready: $endpoint" >&2
exit 1
