# 架构说明

## 1. LocalStack E2E

```mermaid
flowchart LR
    U[调用方] -->|POST /api/chat| A[Spring Boot 宿主机进程]
    A -->|AWS SDK Converse + test/test 签名| B[LocalStack Bedrock :4566]
    A -->|Micrometer PutMetricData| C[LocalStack CloudWatch]
    A -->|OTLP| O[宿主机 OTel Collector]
    O -->|PutTraceSegments| X[LocalStack X-Ray]
```

LocalStack 由用户独立提供。项目只连接 `localhost:4566`，不会创建、关闭或重配 LocalStack 容器。

## 2. 确定性故障注入

```mermaid
flowchart LR
    T[JUnit MockMvc] --> S[BedrockService]
    S --> SDK[真实 BedrockRuntimeClient]
    SDK --> W[JVM 内嵌 WireMock]
    W -->|500/429/200| SDK
    SDK -. MetricCollection .-> P[SDK MetricPublisher]
```

LocalStack 负责真实功能 E2E，但不适合稳定制造“恰好两次 429 后成功”。因此重试教学证据继续使用进程内 WireMock；测试结束后服务器自动关闭，不创建 Docker 容器。

## 3. 应用代码结构

```text
com.example.bedrocklab
├── BedrockResilienceObservabilityApplication.java
├── ChatController.java
├── ApiExceptionHandler.java
├── BedrockConfiguration.java
├── LocalStackConfiguration.java
├── BedrockService.java
└── BedrockTelemetry.java
```

DTO 使用嵌套 record；Bedrock 调用和错误分类集中在一个 Service；SDK MetricPublisher、调用上下文和 Micrometer 指标集中在一个 Telemetry 类。实验只有一个模型后端，因此不增加 Gateway 接口和多组异常子类。

## 4. Standard Retry 时序

```mermaid
sequenceDiagram
    participant App as Spring Boot
    participant SDK as AWS SDK Standard Retry
    participant Downstream as WireMock/Bedrock
    App->>SDK: converse()，应用只调用一次
    SDK->>Downstream: attempt 1
    Downstream-->>SDK: 429 或可重试 5xx
    SDK->>SDK: 分类错误 + 检查重试配额/断路器
    SDK->>SDK: 带抖动指数退避
    SDK->>Downstream: attempt 2
    Downstream-->>SDK: 429 或可重试 5xx
    SDK->>Downstream: attempt 3（上限）
    alt 服务恢复
        Downstream-->>SDK: 200
        SDK-->>App: ConverseResponse
    else 持续失败
        Downstream-->>SDK: 429/5xx
        SDK-->>App: 异常，不再尝试
    end
```

## 5. LocalStack 浮点兼容边界

```text
LocalStack Converse HTTP 200
    metrics.latencyMs = 76266.0
        ↓ local-only ExecutionInterceptor
    metrics.latencyMs = 76266
        ↓
AWS Java SDK 正常反序列化
```

拦截器只改目标 JSON 字段的整数浮点形式，不改 token、answer、HTTP status或错误响应。AWS profile不安装此拦截器。

## 6. LocalStack Terraform

```mermaid
flowchart TD
    TF[terraform apply] --> IAM[IAM Role + 内联策略]
    TF --> SSM[SSM 模型参数]
    TF --> DB[CloudWatch Dashboard]
    TF --> A[2 个 CloudWatch Alarm]
    IAM --> STS[STS AssumeRole 临时凭据]
    STS --> APP[宿主机 Spring Boot]
    SSM -->|启动时读取默认 modelId| APP
    APP --> BR[LocalStack Bedrock Converse]
    APP --> CW[CloudWatch 指标]
    APP --> XR[X-Ray Trace]
    BR --> DESTROY[terraform apply -destroy]
```

Terraform provider 显式覆盖 IAM、STS、SSM、CloudWatch 和 X-Ray endpoint。编排脚本使用 Terraform CLI真正执行 `apply`，并在基础设施存活期间完成应用 E2E；无论应用验证成功还是失败，`finally`清理都会执行 `apply -destroy`。Spring Boot仍是宿主机进程，Bedrock是托管 Runtime，而不是 Terraform创建的计算实例。

当前 LocalStack能创建 X-Ray Sampling Rule，但 AWS provider随即读取标签时会调用 LocalStack尚未实现的 `ListTagsForResource` 并收到 501。因此本实验不保留无法正常 apply/destroy的采样规则；X-Ray运行证据由精确 trace ID查询提供。

## 7. 延迟归因

```text
请求总耗时
 ├─ 应用自身耗时
 ├─ aws.sdk.backoff_delay_ms
 ├─ AWS SDK HTTP/服务调用耗时
 └─ bedrock.model_latency_ms
```

如果模型延迟高而 backoff 为 0，问题更可能在模型；如果 RetryCount 和 backoff 同时升高，则 SDK退避对总延迟有实质贡献。
