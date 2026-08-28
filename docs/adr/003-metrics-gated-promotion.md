# ADR 003: metrics-gated promotion

Status: accepted

Promotion requires a minimum sample and threshold checks for error rate and average latency. A timer only opens the observation window; health evaluation and a Step Functions Choice make the decision. CloudWatch is the primary evidence source, with an explicit DynamoDB mirror fallback for emulator metric-statistic limitations.
