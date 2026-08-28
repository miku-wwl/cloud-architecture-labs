# MCP 请求流程

## 正向流程

`mcp-test.ps1` 先启动官方 Java MCP Client CLI 的 `full-demo`：

1. 从 LocalStack Cognito 获取 access token。
2. 通过官方 Streamable HTTP client transport 发送 `initialize`。
3. 发送 `tools/list`，确认存在 `get_my_profile`、`list_my_orders`、`get_my_preferences`。
4. 调用三个工具，并确认返回当前主体绑定的数据。

脚本随后用 HTTP 请求验证两个负向 MCP 场景：`list_my_orders(limit=1000)` 和未知工具 `delete_everything` 都必须返回错误，不执行有效业务结果。

## 工具设计

```text
get_my_profile()                 → 当前主体的 profile
list_my_orders(limit = 5)        → 当前主体的订单，1 <= limit <= 20
get_my_preferences()             → 当前主体的偏好
```

这里的“my”不是客户端传入的字符串，而是从已验证 JWT 中解析出的主体。MCP 只负责工具协议和调用生命周期；数据归属检查仍由服务端完成。

## 有意缩小的范围

本实验只实现无状态、简单请求响应和 JSON 响应模式。没有实现长连接 SSE、resume、subscription、server push、elicitation、sampling 或复杂会话恢复。这些能力不影响本实验学习 Remote MCP 的基础认证和工具调用链路。
