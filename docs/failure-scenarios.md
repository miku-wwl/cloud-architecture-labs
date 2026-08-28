# 故障场景

| 场景 | 证据 | 预期决策 | 最终路由 |
|---|---|---|---|
| `HEALTHY` | 错误率 <= 5%、延迟 <= 300 ms，且请求数足够 | `PROMOTED` | candidate 100% |
| `ERROR` | candidate 每 3 个请求中有 1 个返回 500 | `ROLLED_BACK` / `ERROR_RATE_THRESHOLD_EXCEEDED` | candidate 0% |
| `SLOW` | candidate 额外增加 700 ms 延迟 | `ROLLED_BACK` / `LATENCY_THRESHOLD_EXCEEDED` | candidate 0% |
| 流量不足 | 请求数低于最小值 | `ROLLED_BACK` / `INSUFFICIENT_REQUESTS` | candidate 0% |
| 并发发布 | 路由锁条件更新失败 | HTTP 409 | 已有发布不变 |
| 重复事件 | 初始化抢占条件更新失败 | 进入忽略分支 | 仍只有一个发布持有者 |

`stable-v1` 会忽略将故障模式设置为非 `HEALTHY` 的请求。
