# 故障场景

| 场景 | 证据 | 预期决策 | 最终路由 |
|---|---|---|---|
| `HEALTHY` | 错误率 <= 5%、延迟 <= 300 ms，且请求数足够 | `PROMOTED` | candidate 100% |
| `ERROR` | candidate 每 3 个请求中有 1 个返回 500 | `ROLLED_BACK` / `ERROR_RATE_THRESHOLD_EXCEEDED` | candidate 0% |
| `SLOW` | candidate 额外增加 700 ms 延迟 | `ROLLED_BACK` / `LATENCY_THRESHOLD_EXCEEDED` | candidate 0% |
| 流量不足 | 请求数低于最小值 | `ROLLED_BACK` / `INSUFFICIENT_REQUESTS` | candidate 0% |
| CloudWatch 无数据 | 评估窗口内没有 `RequestCount` 数据点 | `ROLLED_BACK` / `INSUFFICIENT_REQUESTS` | candidate 0% |

`stable-v1` 会忽略将故障模式设置为非 `HEALTHY` 的请求。
