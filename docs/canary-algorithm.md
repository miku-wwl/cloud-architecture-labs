# Canary algorithm

1. Start with stable `100%`, candidate `0%`.
2. Set candidate to `5%` and record the stage start time.
3. Wait for the configured evidence window.
4. Query CloudWatch `GetMetricStatistics` for candidate `Version`.
5. Require at least `MINIMUM_REQUEST_COUNT` requests, error rate `<= MAX_ERROR_RATE`, and average latency `<= MAX_LATENCY_MS`.
6. On pass, move to 25%, then 50%, then promote to 100%.
7. On any failure, set candidate to 0% and finalize `ROLLED_BACK`.

The wait creates time to collect evidence; it is not the decision. The session bucket is `SHA-256(sessionId) % 100`, so all requests for a session have stable routing at a given percentage.
