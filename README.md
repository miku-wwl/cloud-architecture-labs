# canary-release-orchestrator

这是一个用于学习的渐进式交付控制平面项目：通过传统的支付处理 API，演示从 `stable-v1` 到 `candidate-v2` 的确定性金丝雀发布。项目只使用 Java/Spring Boot、AWS SDK、Terraform、Docker Compose 和 LocalStack；不会连接真实 AWS，也不使用 Bedrock、LLM 或 Kubernetes。

## 问题背景

在不一次性切换全部流量的情况下，自动验证 Candidate 的错误率和延迟：指标正常时逐步扩大流量，出现回归时自动回滚。

## 架构

客户端请求进入 Spring Boot 控制平面。控制平面从 DynamoDB 读取路由状态，按照 `SHA-256(X-Session-Id) % 100` 选择 stable 或 candidate。代理请求结束后，控制平面向 CloudWatch `CanaryDemo/PaymentAPI` 写入 `RequestCount`、`ErrorCount` 和 `LatencyMs`。这里的 Spring Boot 只负责提供被测业务的薄入口，发布编排由 AWS 服务完成。

发布 API 写入发布状态后，发布 EventBridge 事件 `CanaryReleaseRequested`。EventBridge 规则将事件的 `detail` 交给 Standard 类型的 Step Functions；工作流调用 Python Lambda 设置流量权重、等待指标窗口、执行健康评估，再由 Choice 决定进入下一阶段还是回滚。

## 技术栈

- Java 21、Spring Boot 3.4、Maven 多模块项目
- AWS SDK for Java v2：DynamoDB、CloudWatch、EventBridge
- Python 3.11 Lambda 处理器
- Terraform AWS Provider，连接 `http://localhost:4566`
- Docker Compose 与 LocalStack
- JUnit 5；HTTP 和基础设施校验脚本

## 仓库结构

```text
apps/payment-service/          可复用的 stable/candidate Spring Boot 应用
apps/canary-control-plane/     薄网关、发布 API 和 CloudWatch 指标上报
lambda/                        set_weight、evaluate_health、finalize_release
infra/                         Terraform 资源和状态机定义
scripts/                       Windows PowerShell 与 Shell 冒烟/演示脚本
docs/                          架构、算法、故障场景和 ADR
docker-compose.yml             可选的三个应用容器拓扑
Makefile                       可重复执行的本地命令
```

## 快速开始

前置条件：Java 21、Maven、Terraform、AWS CLI，以及已经运行在 `http://localhost:4566` 的 LocalStack。只有使用可选的应用容器拓扑时才需要 Docker Desktop。下面使用的凭据均为本地模拟值：

```powershell
Invoke-RestMethod http://localhost:4566/_localstack/health | ConvertTo-Json
terraform -chdir=infra init
terraform -chdir=infra apply -auto-approve -var='localstack_endpoint=http://localhost:4566'
./mvnw package -DskipTests
.\scripts\start-apps.ps1
```

如果已安装 GNU Make，也可以执行：

```text
make up
make smoke
```

控制平面运行在 8080 端口，stable 服务运行在 8081 端口，candidate 服务运行在 8082 端口。可以使用 `scripts/demo.ps1` 启动演示流量并发起发布。`docker-compose.yml` 只是可选的应用容器拓扑，前提是你已经将独立运行的 LocalStack 暴露在主机的 4566 端口；本项目不会启动或停止 LocalStack 容器。

## 工作原理

网关对同一个会话的每次请求都使用同一个哈希桶。`0%` 和 `100%` 是明确的快速路径；中间比例使用确定性哈希桶。当前发布阈值通过 Lambda 环境变量配置：candidate 至少收到 10 个请求，错误率不超过 5%，平均延迟不超过 300 ms。

candidate 的故障模式只能通过演示用内部接口修改：`PUT /internal/fault-mode/HEALTHY`、`SLOW` 或 `ERROR`。`ERROR` 模式下 candidate 每 3 个请求返回一次 HTTP 500；`SLOW` 模式额外增加 700 ms 延迟。Stable 始终保持健康。

晋级由指标驱动：`Wait -> Evaluate -> Choice`。单纯等待不会使发布晋级。

## 健康发布演示

```powershell
.\scripts\demo.ps1 -Scenario HEALTHY
# 或：make demo-healthy
```

脚本会通过独立的 PowerShell 流量生成器启动有上限的演示流量，通过公共 API 发起发布，轮询发布记录，并期望最终状态为 `PROMOTED`、candidate 流量为 `100%`。

## 回滚演示

```powershell
.\scripts\demo.ps1 -Scenario ERROR
.\scripts\demo.ps1 -Scenario SLOW
# 或：make demo-error / make demo-slow
```

`ERROR` 场景预期以 `ROLLED_BACK` 结束，原因是 `ERROR_RATE_THRESHOLD_EXCEEDED`。`SLOW` 场景预期以 `ROLLED_BACK` 结束，原因是 `LATENCY_THRESHOLD_EXCEEDED`。两种场景最终都会将 candidate 流量降至 `0%`。

## 测试

```text
./mvnw test
terraform -chdir=infra fmt -check
terraform -chdir=infra validate
docker compose config
make smoke
```

Java 测试覆盖确定性故障注入和 stable 安全性。冒烟脚本会验证 LocalStack 资源、服务健康状态，以及真实的 `PutMetricData -> GetMetricStatistics` 往返调用。演示脚本会验证 EventBridge → Step Functions → Lambda 的端到端流程。

## 为什么选择 EventBridge

EventBridge 是发布域的事件边界。它接收 `CanaryReleaseRequested` 并启动工作流，但不负责等待、分支判断或发布状态管理。

## 为什么选择 Step Functions

Step Functions 负责长时间运行的编排，包括等待窗口、Lambda 活动、Choice 决策、重试、晋级和回滚。

## 为什么使用 Lambda

每个 Lambda 都是一个职责单一、契约明确的确定性活动，分别负责设置流量权重、评估 CloudWatch 健康指标或完成最终决策。

## 为什么选择 CloudWatch

CloudWatch 是发布健康度的主要证据来源。每个代理请求都会带有 `Version` 维度，并写入三个自定义指标。评估器使用 `GetMetricStatistics` 查询这些指标。

## 为什么采用确定性路由

对会话 ID 做哈希可以避免请求之间来回切换，也让测试和演示结果更容易解释。使用足够多的不同会话时，实际分布会近似收敛到配置的流量比例。

## LocalStack 限制

应用始终调用 LocalStack 端点，不会回退到真实 AWS。不同版本的 LocalStack 在 CloudWatch 统计聚合、Lambda Docker 执行或 IAM 强制执行方面可能存在差异。发布评估只使用 CloudWatch 的 `PutMetricData` 和 `GetMetricStatistics`；如果窗口内没有 `RequestCount` 数据点，评估结果为 `INSUFFICIENT_REQUESTS`，不会切换到第二套指标存储。这些边界已记录在 [docs/localstack-limitations.md](docs/localstack-limitations.md) 中。

## 重置与清理

```text
make reset       # 通过控制平面重置路由状态
make down        # 停止本机运行的 Spring Boot 演示进程
```

本地工作流不会自动提交或推送代码。LocalStack 需要由外部环境单独提供，并监听 `http://localhost:4566`；本项目不会创建或管理 LocalStack 容器。
