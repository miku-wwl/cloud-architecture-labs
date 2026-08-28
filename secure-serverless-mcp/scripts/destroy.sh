#!/usr/bin/env bash
set -euo pipefail
terraform -chdir=infra/terraform destroy -auto-approve "$@"
