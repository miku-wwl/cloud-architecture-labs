# 架构

## 运行时职责

| 组件 | 职责 |
|---|---|
| `payment-service`（Spring Boot） | 可复用的支付 API 应用，可配置为 `stable-v1` 或 `candidate-v2` |
| 控制平面（Spring Boot） | 网关、发布 API、仪表盘 API、流量生成器和 CloudWatch 指标发布 |
| DynamoDB | 保存权威路由状态、发布状态，并提供确定性的指标窗口回退 |
| CloudWatch | 发布健康度的主要证据：`RequestCount`、`ErrorCount`、`LatencyMs` |
| EventBridge | 发布事件边界和工作流目标 |
| Step Functions | 负责标准工作流的长时间编排、等待、分支、重试、晋级和回滚 |
| Lambda | 由状态机调用的职责单一的确定性活动 |
| Terraform | 创建 LocalStack 资源以及符合生产形态的 IAM 文档 |

## 发布路径

`POST /api/releases` 配置 candidate 模式，将发布状态创建为 `CREATED`，然后向 `canary-release-bus` 发布 `CanaryReleaseRequested`。规则匹配 `source=demo.canary` 和 `detail-type=CanaryReleaseRequested`，将 `$.detail` 传递给 `CanaryReleaseWorkflow`，再由工作流调用 Lambda ARN。

活动发布锁通过 DynamoDB 路由项上的原子条件更新实现。第二个发布请求会收到 HTTP 409。工作流中的初始化 Lambda 会使用 `workflowExecutionId` 单独抢占发布；重复投递的事件会进入成功但不产生副作用的工作流分支。

## 请求路径

网关通过 1 秒缓存读取路由状态，对 `X-Session-Id` 做哈希，调用选中的容器，并在 `finally` 代码块中发布指标。即使后端返回 500 或不可用，网关也会记录状态码和上游耗时。

## 故障隔离

每条回滚分支都会先执行 `set_weight`，将 candidate 流量设置为 0%，然后执行 `finalize_release`。最终处理会移除活动发布锁，并让权威路由保持 candidate 0%。Candidate 的故障注入是确定性的，并且与 `stable-v1` 隔离。
