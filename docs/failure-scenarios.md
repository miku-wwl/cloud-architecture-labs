# Failure scenarios

| Scenario | Evidence | Expected decision | Final route |
|---|---|---|---|
| HEALTHY | error rate <= 5%, latency <= 300 ms, enough requests | PROMOTED | candidate 100% |
| ERROR | every third candidate request returns 500 | ROLLED_BACK / `ERROR_RATE_THRESHOLD_EXCEEDED` | candidate 0% |
| SLOW | candidate adds 700 ms | ROLLED_BACK / `LATENCY_THRESHOLD_EXCEEDED` | candidate 0% |
| Too little traffic | request count below minimum | ROLLED_BACK / `INSUFFICIENT_REQUESTS` | candidate 0% |
| Concurrent release | routing lock condition fails | HTTP 409 | existing release unchanged |
| Duplicate event | initialize claim condition fails | ignored workflow branch | one owner remains |

Stable-v1 ignores attempts to set a non-HEALTHY fault mode.
