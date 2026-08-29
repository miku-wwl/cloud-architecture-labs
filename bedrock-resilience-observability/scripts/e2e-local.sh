#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v pwsh >/dev/null 2>&1; then
  echo "FAIL: PowerShell 7 (pwsh) is required for the Windows LocalStack E2E." >&2
  exit 1
fi

pwsh -NoProfile -File "$LAB_ROOT/scripts/e2e-local.ps1"
