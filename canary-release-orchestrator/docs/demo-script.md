# 演示脚本

1. 确认外部 LocalStack 正在 `http://localhost:4566` 提供服务。
2. 执行 `make up`，启动两个业务实例和一个薄控制平面。
3. 执行 `make smoke`，确认 EventBridge、Step Functions、3 个 Lambda 和 CloudWatch 可用。
4. 执行 `make demo-healthy`，观察 `5% -> 25% -> 50% -> 100%` 和最终状态 `PROMOTED`。
5. 执行 `make reset`，再执行 `make demo-error`，观察最终状态 `ROLLED_BACK` 和 candidate `0%`。
6. 执行 `make reset`，再执行 `make demo-slow`，观察延迟阈值触发回滚。

演示现在不依赖 Dashboard。可以直接执行 `scripts/demo.ps1 -Scenario HEALTHY`、`ERROR` 或 `SLOW`。脚本会调用外部流量生成器，通过 API 轮询由 DynamoDB 支持的发布状态，并检查预期的终态。
