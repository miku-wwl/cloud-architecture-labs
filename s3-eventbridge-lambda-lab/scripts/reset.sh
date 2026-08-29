#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_ROOT"
./scripts/destroy.sh
rm -rf lambda/target
rm -f terraform/terraform.tfstate terraform/terraform.tfstate.backup terraform/.terraform.tfstate.lock.info
echo "RESET PASS"
