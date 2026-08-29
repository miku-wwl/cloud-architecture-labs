#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_ROOT"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
LOCALSTACK_ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
LAMBDA_ENDPOINT="${LAMBDA_ENDPOINT:-http://host.docker.internal:4566}"

pwsh -NoProfile -File ./scripts/check-localstack.ps1 -LocalStackEndpoint "$LOCALSTACK_ENDPOINT"
mvn -f lambda/pom.xml -q test package
terraform -chdir=terraform fmt -check -recursive
pwsh -NoProfile -File ./scripts/verify-local.ps1 \
  -LocalStackEndpoint "$LOCALSTACK_ENDPOINT" \
  -LambdaEndpoint "$LAMBDA_ENDPOINT"
echo "VERIFY PASS: S3 -> EventBridge -> Lambda -> S3"
