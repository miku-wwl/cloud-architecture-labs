# ADR 002: event-driven release orchestration

Status: accepted

The API publishes a release request to EventBridge. EventBridge starts Step Functions, while Step Functions owns the multi-stage workflow. This separates event routing from long-running orchestration and makes the LocalStack resource graph inspectable.
