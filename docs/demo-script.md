# 演示脚本

1. 执行 `make up`。
2. 执行 `make smoke`。
3. 打开 `http://localhost:8080`。
4. 将流量启动为 30 RPS。
5. 点击 **Start Healthy Release**，观察流量从 5% -> 25% -> 50% -> 100%，最终状态变为 `PROMOTED`。
6. 重置演示环境。
7. 再次启动流量，点击 **Start Error Release**，观察 candidate 先接收 5% 流量，随后状态变为 `ROLLED_BACK`，流量回到 0%。
8. 重置环境，再使用 **Start Slow Release** 重复演示。

不使用界面时，可以执行 `scripts/demo.ps1 -Scenario HEALTHY`、`ERROR` 或 `SLOW`。每个脚本都会通过 API 轮询由 DynamoDB 支持的发布状态，并检查预期的终态。
