# Bedrock 弹性与可观测性实验

这是一个 Java 21 / Spring Boot 学习项目。本地主路径直接使用你已经运行的 LocalStack Ultimate `http://localhost:4566`，不需要真实 AWS credentials，也不会由项目创建 Docker 容器。

实验重点是：AWS SDK Standard Retry、Bedrock Converse、SDK 原生重试指标、OpenTelemetry，以及真实 LocalStack Bedrock、X-Ray、CloudWatch 和 Terraform 集成。

## 两类验证必须分清

| 验证 | 下游 | 用途 |
|---|---|---|
| LocalStack E2E | 现有 LocalStack Bedrock/CloudWatch/X-Ray | 证明应用能在无真实凭据时完整运行 |
| Terraform E2E | 现有 LocalStack IAM/STS/SSM/CloudWatch | 真正 apply、应用使用并自动销毁本地资源 |
| JUnit 故障注入 | JVM 内嵌 WireMock | 确定性制造 500/429，证明 AWS SDK 的重试次数和退避 |

WireMock 不是本地应用运行后端，只存在于测试进程内。Terraform 直接连接 LocalStack，也不会访问真实 AWS。

## 本地架构

```text
POST /api/chat
    ↓
Spring Boot（宿主机 :18080）
    ├── AWS SDK Bedrock Converse ─────→ LocalStack Bedrock :4566
    ├── Micrometer CloudWatch registry → LocalStack CloudWatch :4566
    └── OpenTelemetry Java Agent ──────→ 宿主机 OTel Collector
                                              ↓
                                      LocalStack X-Ray :4566
```

应用内调用层次保持简单：

```text
ChatController
    ↓
BedrockService
    ↓
BedrockRuntimeClient.converse()

BedrockTelemetry 同时采集 SDK retry/backoff，并写入指标与 trace。
```

生产代码只有 7 个 Java 文件。DTO 使用嵌套 record，错误统一由一个异常类型表达；因为本实验只有 Bedrock 一个实现，所以没有额外 Gateway 接口层。

完整设计见 [架构说明](docs/architecture.md)，需求与证据对照见 [Q76 映射](docs/q76-mapping.md)。

## 前置条件

- Java 21。
- AWS CLI v2。
- PowerShell 7。
- Terraform（真实 LocalStack apply 测试需要）。
- 已由你自行运行的 LocalStack Ultimate，端口为 `4566`。
- LocalStack 具备 Bedrock、CloudWatch 和 X-Ray 能力。

项目不会执行 `docker run`、`docker compose up` 或创建 LocalStack 容器。首次执行 E2E 会把 OpenTelemetry Java Agent 和 Windows Collector 下载到被 Git 忽略的 `.tools/`。

## 配置

[application-local.yml](src/main/resources/application-local.yml) 默认值：

```yaml
endpoint: http://localhost:4566
model: ollama.smollm2:360m
credentials: test/test
region: us-east-1
```

这些是假凭据，只用于 AWS SDK 请求签名，不会写入 `~/.aws/credentials`。可通过环境变量覆盖：

```powershell
$env:BEDROCK_ENDPOINT = "http://localhost:4566"
$env:BEDROCK_MODEL_ID = "ollama.smollm2:360m"
$env:BEDROCK_ALLOWED_MODEL_IDS = "ollama.smollm2:360m"
```

## 运行验证

只验证确定性重试：

```powershell
.\mvnw.cmd test
```

执行真正的 LocalStack E2E：

```powershell
.\scripts\e2e-local.ps1
```

Git Bash 入口：

```bash
./scripts/e2e-local.sh
```

一键运行 JUnit、Terraform 和 LocalStack E2E：

```bash
./scripts/verify.sh
```

E2E 会自行启动并清理宿主机上的应用与 Collector进程，不会停止用户已有的 LocalStack。

## API

脚本使用 18080，避免与其他本地服务的 8080 冲突：

```powershell
Invoke-RestMethod http://localhost:18080/actuator/health

$body = @{
  message = "用一句话解释指数退避"
  modelId = "ollama.smollm2:360m"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:18080/api/chat `
  -ContentType application/json `
  -Body $body
```

成功响应包含 answer、token 用量、模型延迟，以及直接来自 AWS SDK MetricPublisher 的 retry/backoff 证据。

## Standard Retry

代码显式使用 `StandardRetryStrategy.builder().maxAttempts(3)`。3 表示包含首次请求在内最多 3 次 HTTP attempt，不是“额外重试 3 次”。

应用没有：

- `@Retryable`
- Resilience4j Retry
- Bedrock 外层循环
- 自定义 sleep/backoff

因此只有 AWS SDK 一层重试。Standard 是通用策略；Adaptive 对客户端按受限资源隔离有更强要求，共享客户端时还可能影响其他模型的首请求延迟，所以本实验不选 Adaptive。

## LocalStack 兼容处理

LocalStack `2026.8.0.dev194` 的 Converse 原始响应把 `metrics.latencyMs` 返回为 `76266.0` 一类浮点数，而 AWS Java SDK Smithy模型要求整数。CLI会宽松转换，Java SDK会把 HTTP 200 判为反序列化失败并错误重试。

[LocalStackConfiguration.java](src/main/java/com/example/bedrocklab/LocalStackConfiguration.java) 中的响应拦截器只在“假凭据 + endpoint override”的 local 模式启用，只把整数形式的 `latencyMs: N.0` 归一化为 `N`。AWS profile完全不加载该兼容层。

## 可观测性

应用记录：

- `gen_ai.request.model`
- `bedrock.model_latency_ms`
- `aws.sdk.retry_count`
- `aws.sdk.backoff_delay_ms`
- input/output/total tokens
- `error_type` 与 `throttled`

LocalStack 路径：

- Actuator：`/actuator/prometheus`
- Micrometer CloudWatch registry：直接写入 LocalStack `GenAI/BedrockLab`
- OTel Java Agent → Collector → LocalStack X-Ray

模型 ID 受 allow-list 控制；prompt、request ID、trace ID均不作为指标维度。

## LocalStack Terraform

[`terraform/providers.tf`](terraform/providers.tf) 显式使用 `test/test`，并把 IAM、STS、SSM、CloudWatch 和 X-Ray endpoint 指向 `http://localhost:4566`。

Terraform 管理不会启动容器、且由应用实际使用的本地资源：

- 1 个 IAM Role。
- 1 个 IAM内联策略：允许读取指定 SSM参数、调用指定 Bedrock模型，以及写入 CloudWatch/X-Ray遥测。
- 1 个 SSM Parameter：保存应用默认模型 ID。
- 1 个 CloudWatch Dashboard。
- 2 个 CloudWatch Alarm。

真实集成测试（PowerShell）：

```powershell
.\scripts\terraform-e2e-local.ps1
```

脚本会显式执行 `terraform apply`，通过 AWS CLI确认 6 个资源确实存在，然后 AssumeRole取得临时凭据。Spring Boot在请求未提供 `modelId` 的情况下读取 Terraform 创建的 SSM参数，再调用 LocalStack Bedrock。最后脚本用 `terraform apply -destroy` 清理资源；Terraform状态仅临时保存在 `.tools/terraform-localstack-e2e.tfstate`，成功销毁后删除。没有 ECS、ALB、VPC或ECR资源，因此不会创建 Docker容器。当前证据见 [验证报告](docs/validation-evidence.md)。

Bedrock基础模型是托管服务，本实验不会伪造一个 Terraform “Bedrock实例”。`ollama.smollm2:360m` 由现有 LocalStack Bedrock Runtime提供，Terraform负责管理应用的调用权限、模型配置和监控资源。
