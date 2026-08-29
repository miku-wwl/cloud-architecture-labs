# Q76 需求映射

| 需求 | 实现 | 验证 | 当前证据 |
|---|---|---|---|
| 无真实 AWS credentials 的本地 E2E | local profile → `localhost:4566`，静态 `test/test` | `e2e-local.ps1` | PASS |
| 真实 Bedrock Converse 路径 | Spring Boot → AWS Java SDK → LocalStack Bedrock | LocalStack `ollama.smollm2:360m` | 60 tokens，RetryCount=0 |
| 显式 Standard Retry | `BedrockConfiguration` | 源码 + WireMock集成测试 | maxAttempts=3 |
| 无第二层重试 | `BedrockService` 只调用一次 `client.converse()` | 源码扫描 | 无 Retryable/Resilience4j/重试循环 |
| 瞬时 5xx 恢复 | JVM WireMock 500、500、200 | JUnit | 3 attempts，RetryCount=2 |
| 限流后恢复 | JVM WireMock 429、429、200 | JUnit | 3 attempts，Backoff > 0 |
| 持续限流有界失败 | JVM WireMock 始终 429 | JUnit | 3 attempts 后 HTTP 429 |
| RetryCount 来自 SDK | `BedrockTelemetry` 读取 `CoreMetric.RETRY_COUNT` | retry stats断言 | PASS |
| Backoff 来自 SDK | 读取 `BACKOFF_DELAY_DURATION` | 非零断言 | PASS |
| HTTP status来自 SDK | 读取 `HttpMetric.HTTP_STATUS_CODE` | 429列表断言 | PASS |
| LocalStack 响应兼容 | local-only latencyMs整数化拦截器 | 独立单元测试 + E2E | PASS，AWS profile不启用 |
| 模型级 trace | `bedrock.converse` CLIENT span | LocalStack X-Ray精确 trace ID查询 | span 与 model ID同时存在 |
| LocalStack CloudWatch | Micrometer CloudWatch2 registry | `list-metrics` | `GenAI/BedrockLab` 18 metrics |
| Actuator指标 | Prometheus endpoint | E2E文本检查 | 模型维度存在，prompt不存在 |
| 模型延迟归因 | model latency + model_id | 快/慢模型测试 | 25 ms 对 900 ms |
| token 用量 | Converse usage → response/span/metrics | JUnit + LocalStack E2E | PASS |
| X-Ray annotation | `aws.xray.annotations` | Collector配置与 X-Ray trace | model_id/operation/error_type/throttled |
| 真实 LocalStack Terraform | provider → `localhost:4566` | 显式 `terraform apply` → 应用 E2E → `terraform apply -destroy` | 创建/使用/销毁 6 个资源，PASS |
| Terraform配置进入应用 | SSM保存模型 ID，STS临时凭据启动应用 | 请求省略 `modelId`，响应仍返回 Terraform模型 | PASS |
| 最小权限结构 | IAM Role + 内联策略 | AssumeRole + 策略动作扫描 | SSM/Bedrock/CloudWatch/X-Ray |
| 无容器 Terraform | IAM/SSM/Dashboard/Alarm | Terraform资源和脚本扫描 | 无 ECS/ALB/VPC/ECR，无 Docker命令 |

## 证据边界

- LocalStack E2E 是本机模拟 AWS 服务的运行证据，不等于真实 AWS。
- WireMock 是 AWS SDK重试算法的确定性证据，不等于 LocalStack服务证据。
- Terraform CLI的显式 apply/destroy 是真实 LocalStack 资源生命周期证据，不等于真实 AWS部署。
