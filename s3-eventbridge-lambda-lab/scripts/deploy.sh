#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_ROOT"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
LOCALSTACK_ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
LAMBDA_ENDPOINT="${LAMBDA_ENDPOINT:-http://host.docker.internal:4566}"

./scripts/build.sh
terraform -chdir=terraform init -reconfigure -input=false -no-color
terraform -chdir=terraform validate -no-color
terraform -chdir=terraform apply -auto-approve -input=false -no-color \
  -var="localstack_endpoint=$LOCALSTACK_ENDPOINT" \
  -var="lambda_endpoint=$LAMBDA_ENDPOINT"
