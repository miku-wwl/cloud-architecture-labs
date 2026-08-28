# 金丝雀发布算法

1. 初始状态为 stable `100%`、candidate `0%`。
2. 将 candidate 调整为 `5%`，并记录该阶段的开始时间。
3. 等待配置的证据采集窗口结束。
4. 按 candidate 的 `Version` 维度查询 CloudWatch `GetMetricStatistics`。
5. 必须同时满足：请求数至少为 `MINIMUM_REQUEST_COUNT`、错误率 `<= MAX_ERROR_RATE`，以及平均延迟 `<= MAX_LATENCY_MS`。
6. 评估通过后，依次将 candidate 提升到 25%、50%，最后提升到 100%。
7. 任一阶段失败时，将 candidate 调整为 0%，并以 `ROLLED_BACK` 完成发布。

等待的作用是留出时间收集证据，等待本身不代表决策。会话桶的计算方式是 `SHA-256(sessionId) % 100`，因此在给定流量比例下，同一个会话的所有请求都会稳定地路由到同一版本。
