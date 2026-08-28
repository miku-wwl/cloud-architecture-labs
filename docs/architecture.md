# Architecture

## Runtime responsibilities

| Component | Responsibility |
|---|---|
| Spring Boot payment-service | One reusable Payment API artifact, configured as `stable-v1` or `candidate-v2` |
| Spring Boot control plane | Gateway, release API, dashboard API, traffic generator, CloudWatch publishing |
| DynamoDB | Authoritative routing state, release state, and deterministic metrics-window fallback |
| CloudWatch | Primary release-health evidence: RequestCount, ErrorCount, LatencyMs |
| EventBridge | Release event boundary and workflow target |
| Step Functions | Standard long-running orchestration, waits, choices, retries, promotion and rollback |
| Lambda | Small deterministic activities called by the state machine |
| Terraform | Creates all LocalStack resources and production-style IAM documents |

## Release path

`POST /api/releases` configures the candidate mode, creates `CREATED`, then publishes `CanaryReleaseRequested` to `canary-release-bus`. The rule matches `source=demo.canary` and `detail-type=CanaryReleaseRequested`, passes `$.detail` to `CanaryReleaseWorkflow`, and the workflow invokes Lambda ARNs.

The active-release lock is an atomic DynamoDB conditional update on the routing item. A second release receives HTTP 409. The workflow's initialize Lambda separately claims the release using `workflowExecutionId`; duplicate event delivery becomes a successful no-op workflow branch.

## Request path

The gateway reads routing state through a 1-second cache, hashes `X-Session-Id`, calls the selected container, and publishes metrics in a `finally` block. It records status and upstream duration even when the backend returns 500 or is unavailable.

## Failure containment

Every rollback branch executes `set_weight` with percentage 0 before `finalize_release`. Finalization removes the active lock and leaves the authoritative route at 0% candidate. Candidate fault injection is deterministic and isolated from stable-v1.
