#!/usr/bin/env bash
set -euo pipefail
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
AWSCLI=(aws --endpoint-url http://localhost:4566)
"${AWSCLI[@]}" dynamodb list-tables >/dev/null
for table in canary-routing-state canary-releases canary-metrics-window; do "${AWSCLI[@]}" dynamodb describe-table --table-name "$table" >/dev/null; done
"${AWSCLI[@]}" events list-event-buses --query "EventBuses[?Name=='canary-release-bus']" --output text | grep -q canary-release-bus
"${AWSCLI[@]}" events list-rules --event-bus-name canary-release-bus --query "Rules[?Name=='canary-release-requested']" --output text | grep -q canary-release-requested
"${AWSCLI[@]}" stepfunctions list-state-machines --query "stateMachines[?name=='CanaryReleaseWorkflow']" --output text | grep -q CanaryReleaseWorkflow
"${AWSCLI[@]}" lambda list-functions --query "Functions[?FunctionName=='canary-evaluate_health']" --output text | grep -q canary-evaluate_health
curl -fsS http://localhost:8081/actuator/health >/dev/null
curl -fsS http://localhost:8082/actuator/health >/dev/null
curl -fsS http://localhost:8080/actuator/health >/dev/null
echo 'SMOKE PASS: core LocalStack resources and services are available.'
