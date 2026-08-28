# 架构

## 运行时职责

| 组件 | 职责 |
|---|---|
| `library-service`（Spring Boot） | 可复用的图书借阅 API，可配置为 `stable-v1` 或 `candidate-v2` |
| 控制平面（Spring Boot） | 薄网关、发布 API 和 CloudWatch 指标发布 |
| DynamoDB | 保存路由状态和发布状态 |
| CloudWatch | 发布健康度的主要证据：`RequestCount`、`ErrorCount`、`LatencyMs` |
| EventBridge | 发布事件边界和工作流目标 |
| Step Functions | 负责标准工作流的长时间编排、等待、分支、重试、晋级和回滚 |
| Lambda | 由状态机调用的 3 个职责单一的确定性活动 |
| Terraform | 创建 LocalStack 资源以及符合生产形态的 IAM 文档 |

## 发布路径

`POST /api/releases` 创建 `CREATED` 发布记录，然后向 `canary-release-bus` 发布 `CanaryReleaseRequested`。规则匹配 `source=demo.canary` 和 `detail-type=CanaryReleaseRequested`，将 `$.detail` 传递给 `CanaryReleaseWorkflow`，再由工作流调用 3 个 Lambda ARN。

发布记录由 API 创建，后续状态由 Lambda 根据状态机阶段更新。这个 demo 假设同一时间只演示一个发布，不实现并发发布锁和重复事件去重。

## 请求路径

网关每次从 DynamoDB 读取路由状态，对 `X-Session-Id` 做哈希，调用选中的后端实例，并在 `finally` 代码块中发布 CloudWatch 指标。即使后端返回 500 或不可用，网关也会记录状态码和上游耗时。

## 故障隔离

每条回滚分支都会先执行 `set_weight`，将 candidate 流量设置为 0%，然后执行 `finalize_release`。最终处理会让权威路由保持 candidate 0%。Candidate 的故障注入是确定性的，并且与 `stable-v1` 隔离。
