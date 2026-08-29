#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_ROOT"

LOCALSTACK_ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
curl -fsS "$LOCALSTACK_ENDPOINT/_localstack/health" >/dev/null

./mvnw test
terraform -chdir=terraform fmt -check -recursive
pwsh -NoProfile -File ./scripts/terraform-e2e-local.ps1 -LocalStackEndpoint "$LOCALSTACK_ENDPOINT"

echo "VERIFY PASS"
