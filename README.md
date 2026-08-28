# canary-release-orchestrator

学习型 Progressive Delivery control plane：用一个传统 Payment Processing API 演示 stable-v1 到 candidate-v2 的 deterministic canary release。项目只使用 Java/Spring Boot、AWS SDK、Terraform、Docker Compose 与 LocalStack；不会连接真实 AWS，也不使用 Bedrock、LLM 或 Kubernetes。

## Problem

在不一次性切换全部流量的情况下，自动验证 Candidate 的错误率和延迟，并在健康时逐步扩大流量，在回归时自动回滚。

## Architecture

客户端请求进入 Spring Boot control plane。它从 DynamoDB 读取 1 秒缓存的 routing state，并按 `SHA-256(X-Session-Id) % 100` 选择 stable 或 candidate。代理结束后向 CloudWatch `CanaryDemo/PaymentAPI` 写入 RequestCount、ErrorCount、LatencyMs，并把同一条 evidence 写入 DynamoDB window，供 LocalStack CloudWatch 统计异常时的显式 deterministic fallback 使用。

Release API 写入 release state 后发布 EventBridge `CanaryReleaseRequested`。EventBridge rule 将事件 detail 交给 Standard Step Functions；workflow 调用 Python Lambda 设置权重、等待 metrics window、调用 health evaluation，再由 Choice 决定下一阶段或 rollback。

## Technology Stack

- Java 21, Spring Boot 3.4, Maven multi-module
- AWS SDK for Java v2：DynamoDB、CloudWatch、EventBridge
- Python 3.11 Lambda handlers
- Terraform AWS provider against `http://localhost:4566`
- Docker Compose and LocalStack
- JUnit 5; HTTP/infrastructure validation scripts

## Repository Structure

```text
apps/payment-service/          reusable stable/candidate Spring Boot artifact
apps/canary-control-plane/     gateway, release API, dashboard, metrics, traffic
lambda/                        initialize, set_weight, evaluate_health, finalize
infra/                         Terraform resources and state machine definition
scripts/                       Windows PowerShell and shell smoke/demo scripts
docs/                          architecture, algorithm, failure scenarios, ADRs
docker-compose.yml             optional three-application container topology
Makefile                       reproducible local commands
```

## Quick Start

Prerequisites: Java 21, Maven, Terraform, Docker Desktop and AWS CLI. All credentials are fake values:

```powershell
Invoke-RestMethod http://localhost:4566/_localstack/health | ConvertTo-Json
terraform -chdir=infra init
terraform -chdir=infra apply -auto-approve -var='localstack_endpoint=http://localhost:4566'
./mvnw package -DskipTests
.\scripts\start-apps.ps1
```

Or, with GNU Make available:

```text
make up
make smoke
```

Open <http://localhost:8080>. The dashboard has traffic and release buttons. The stable service is on 8081 and candidate is on 8082. `docker-compose.yml` is an optional app-container topology that assumes the separately deployed LocalStack is already exposed on host port 4566; this project workflow does not start or stop a LocalStack container.

## How It Works

The gateway uses the same session bucket for every request. `0%` and `100%` are explicit fast paths; intermediate percentages use the deterministic bucket. The current release thresholds are configurable environment variables: minimum 10 candidate requests, error rate at most 5%, and average latency at most 300 ms.

The candidate fault mode is changed only through the demo internal endpoint: `PUT /internal/fault-mode/HEALTHY`, `SLOW`, or `ERROR`. ERROR returns HTTP 500 on every third candidate request; SLOW adds 700 ms. Stable is always healthy.

Promotion is metric-driven: `Wait -> Evaluate -> Choice`. A wait by itself never promotes a release.

## Healthy Demo

```powershell
.\scripts\demo.ps1 -Scenario HEALTHY
# or: make demo-healthy
```

The script starts bounded demo traffic, starts the release through the public API, polls the release record, and expects `PROMOTED` with candidate traffic `100%`.

## Rollback Demo

```powershell
.\scripts\demo.ps1 -Scenario ERROR
.\scripts\demo.ps1 -Scenario SLOW
# or: make demo-error / make demo-slow
```

ERROR is expected to end as `ROLLED_BACK` with `ERROR_RATE_THRESHOLD_EXCEEDED`. SLOW is expected to end as `ROLLED_BACK` with `LATENCY_THRESHOLD_EXCEEDED`. Both end at candidate traffic `0%`.

## Testing

```text
./mvnw test
terraform -chdir=infra fmt -check
terraform -chdir=infra validate
docker compose config
make smoke
```

The Java tests cover deterministic fault injection and stable safety. The smoke script proves the LocalStack resources, service health, and a real `PutMetricData -> GetMetricStatistics` round trip. Demo scripts prove the event-driven end-to-end path when Docker is available.

## Why EventBridge

EventBridge is the release-domain event boundary. It receives `CanaryReleaseRequested` and starts the workflow; it does not own waits, branching, or release state.

## Why Step Functions

Step Functions owns the long-running orchestration: wait windows, Lambda activities, Choice decisions, retry behavior, promotion and rollback.

## Why Lambda

Each Lambda is a small deterministic activity with a narrow contract: claim a release, set a weight, evaluate health evidence, or finalize a decision.

## Why CloudWatch

CloudWatch is the primary release-health evidence source. Every proxied request emits the three custom metrics with a `Version` dimension. The evaluator uses `GetMetricStatistics`.

## Why deterministic routing

Hashing a session ID avoids request-to-request flapping and makes a test or demo explainable. A large set of distinct sessions converges approximately on the configured percentage.

## LocalStack limitations

The application always calls the LocalStack endpoint and never falls through to real AWS. LocalStack versions can differ in CloudWatch statistic aggregation, Lambda Docker execution, or IAM enforcement. The primary path remains CloudWatch `PutMetricData` and the evaluator first calls `GetMetricStatistics`; if that call errors or returns no request datapoints, it emits a `cloudwatch_evaluation_fallback` log and reads the exact request evidence from `canary-metrics-window`. This behavior is explicit and documented in [docs/localstack-limitations.md](docs/localstack-limitations.md), not a silent CloudWatch bypass.

## Reset and cleanup

```text
make reset       # resets routing state through the control plane
make down        # stops the native Spring Boot demo processes
```

The local workflow does not automatically commit or push changes. LocalStack is expected to be provided separately at `http://localhost:4566`; the project does not create or manage a LocalStack container.
