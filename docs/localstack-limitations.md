# LocalStack limitations and evidence policy

All Java SDK clients use an explicit endpoint override, and every Lambda receives `AWS_ENDPOINT_URL`; credentials are the fake `test/test` pair in `us-east-1`. There is no real AWS fallback.

The primary observability path is CloudWatch `PutMetricData` from the gateway. The evaluator calls CloudWatch `GetMetricStatistics` with the candidate `Version` dimension. Some LocalStack versions have incomplete or delayed metric-statistic aggregation. When GetMetricStatistics raises an error or returns no RequestCount datapoints, the evaluator prints `cloudwatch_evaluation_fallback` and queries the per-request `canary-metrics-window` DynamoDB table. That table is a deterministic evidence mirror, not a replacement for metric publication. The returned `metricSource` makes the source visible in the workflow result/logs.

Run `scripts/smoke-test.ps1` after provisioning to test `PutMetricData -> GetMetricStatistics` against the current emulator. If this smoke check fails, the release path remains reproducible through the explicit DynamoDB fallback and the limitation must be reported as part of the run evidence.

LocalStack Lambda execution needs the Docker socket and `LAMBDA_DOCKER_NETWORK=canary-network`, which are configured in Compose. IAM roles/policies are still defined in Terraform for production-shaped infrastructure, although LocalStack may not enforce every IAM decision exactly as AWS does.
