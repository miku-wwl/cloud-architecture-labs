# 架构说明

## 组件职责

| 组件 | 职责 |
|---|---|
| Java MCP Client | 模拟 MCP 客户端，获取令牌并执行真实的初始化、工具发现和工具调用。 |
| Cognito | 签发 LocalStack access token，提供 issuer、JWKS 和 `mcp-api/read` scope。 |
| API Gateway HTTP API | 暴露唯一外部入口 `POST /mcp`，使用 JWT Authorizer 和 scope 做入口授权。 |
| Java Lambda | 使用 Java 21 和 Spring Boot 处理 API Gateway v2 事件。 |
| MCP SDK | 提供官方 MCP Streamable HTTP server/client transport，不自行实现 JSON-RPC 分发。 |
| MCP Tools | 只根据已验证主体读取 profile、orders 和 preferences。 |
| DynamoDB | 以 `principalId` 为分区键保存无真实敏感信息的演示数据。 |

## 两段运行路径

服务端内部的 MCP transport 注册在 `/mcp`，但最终演示不会直接访问 Lambda。端到端路径是：

```text
Client CLI
  → Cognito token endpoint
  → API Gateway HTTP API POST /mcp
  → JWT Authorizer
  → Lambda Java handler
  → Spring Boot servlet
  → Official MCP Streamable HTTP server
  → Tool handler
  → DynamoDB
```

API Gateway 使用 payload format `2.0` 和 `AWS_PROXY`。没有创建 Lambda Function URL，因此 Lambda 没有第二个公共入口。

## 本地端点安全

Terraform 默认面向 `http://localhost:4566`，凭据固定为本地测试值。Lambda 运行时的端点可单独设置：Compose 网络使用 `http://localstack:4566`；当前外部 LocalStack 容器在 Windows 上使用 `http://host.docker.internal:4566`。Java 启动时会检查端点主机名，只允许本地名称和 `*.localstack.cloud`，拒绝真实 AWS 主机。
