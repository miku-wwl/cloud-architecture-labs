# 架构索引

## 实验 01：Canary Release Orchestrator

该实验用简单的图书借阅业务承载发布编排。Spring Boot 只提供薄业务入口和发布 API，重点放在 AWS 发布控制链路：

```text
Spring Boot
    ↓
EventBridge
    ↓
Step Functions
    ↓
Lambda
    ↓
CloudWatch
    ↓
晋级 / 回滚
```

详细内容见 [canary-release-orchestrator/README.md](../canary-release-orchestrator/README.md)。

## 实验 02：Secure Serverless MCP

该实验使用两个 Cognito 客户端模拟两个服务主体。客户端通过 OAuth Client Credentials 获取真实的 LocalStack Cognito access token，再经 API Gateway HTTP API 的 JWT Authorizer 进入 Java 21 Lambda 中的官方 MCP Java SDK 服务端：

```text
Java MCP Client
    ↓
Cognito OAuth
    ↓
JWT / JWKS
    ↓
API Gateway HTTP API JWT Authorizer
    ↓
Java Lambda
    ↓
MCP Streamable HTTP
    ↓
MCP Tools
    ↓
DynamoDB
```

详细内容见 [secure-serverless-mcp/README.md](../secure-serverless-mcp/README.md)。
