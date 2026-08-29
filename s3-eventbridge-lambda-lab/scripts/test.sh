#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_ROOT"
mvn -f lambda/pom.xml test
echo "LAMBDA_UNIT_TEST PASS"
