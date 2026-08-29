#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LAB_ROOT"
mvn -f lambda/pom.xml -q package
test -f lambda/target/order-processor.jar
echo "LAMBDA_BUILD PASS: lambda/target/order-processor.jar"
