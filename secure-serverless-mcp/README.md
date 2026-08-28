# Secure Serverless MCP

这是一个用于学习安全远程 MCP 发布链路的 Java 端到端实验：Java MCP Client 通过 LocalStack Cognito 获取 OAuth Client Credentials 令牌，经 API Gateway HTTP API 的 JWT Authorizer 访问 Java 21 Lambda；Lambda 内运行 Spring Boot 和官方 MCP Java SDK，工具只读取当前 JWT 主体对应的 DynamoDB 数据。

本实验的业务故意保持简单，重点是 `Cognito → API Gateway → Lambda → MCP → DynamoDB`，不是 LLM、RAG、Agent 或普通 REST Demo。

## 架构

```text
Java MCP Client
    │ OAuth Client Credentials
    ▼
LocalStack Cognito User Pool
    │ access token / JWT
    ▼
API Gateway V2 HTTP API
    │ JWT Authorizer + mcp-api/read
    ▼
AWS Lambda Java 21
    │ aws-serverless-java-container
    ▼
Spring Boot + Official MCP Java SDK
    │ Streamable HTTP: initialize / tools/list / tools/call
    ▼
Principal-bound MCP Tools
    ▼
DynamoDB: mcp-user-data
```

运行时只配置 `POST /mcp`，采用无状态、简单请求响应和 JSON 响应模式；不把长连接 SSE、恢复、订阅和服务端推送放入本实验范围。

## 目录

- `mcp-server-lambda/`：Spring Boot MCP 服务端、Lambda 适配器、DynamoDB 仓储和单元测试。
- `mcp-client-cli/`：使用官方 MCP Java Client Streamable HTTP transport 的 CLI。
- `infra/terraform/`：Cognito、DynamoDB、Lambda、API Gateway V2、JWT Authorizer、IAM 和日志组。
- `scripts/`：构建、打包、播种数据、冒烟、MCP 正向和安全验证脚本。
- `docs/`：架构、认证、MCP 请求流、威胁模型、限制、证据和演示步骤。

## 前置条件

- Java 21、Maven、Terraform、AWS CLI。
- 已由外部启动并监听 `http://localhost:4566` 的 LocalStack。当前验证使用的是这个已有实例；Codex 不创建、不启动、不停止 Docker 容器。
- 本地模拟凭据 `test/test`，区域 `us-east-1`。不使用 AWS profile，也不调用真实 AWS。

用户环境声明 LocalStack 为 Ultimate；本次健康接口实际返回 `edition=pro` 和开发版本信息，因此授权版别只以本机环境为准，文档不把它当作独立验证结论。

## PowerShell 快速开始

```powershell
Invoke-RestMethod http://localhost:4566/_localstack/health | ConvertTo-Json
powershell -File scripts/build.ps1
terraform -chdir=infra/terraform init
terraform -chdir=infra/terraform apply -auto-approve -parallelism=1 -var "lambda_localstack_endpoint=http://host.docker.internal:4566"
powershell -File scripts/seed-local-data.ps1
powershell -File scripts/mcp-test.ps1
powershell -File scripts/security-test.ps1
```

`lambda_localstack_endpoint` 在当前 Windows Docker Desktop 环境中使用 `host.docker.internal:4566`，因为 Lambda 运行时容器无法解析现有 LocalStack 容器的 `localstack` DNS 名称。若使用本目录的 Compose 网络，默认值 `http://localstack:4566` 适用；本次没有执行 `docker compose up`。

## MCP 工具

- `get_my_profile()`
- `list_my_orders(limit)`，`limit` 必须在 1 到 20 之间，默认 5。
- `get_my_preferences()`

工具 schema 不接受 `userId`、`principalId` 或 `email`。主体来自 API Gateway 已验证的 JWT：有 `sub` 时使用 `sub`，否则使用 `client_id`，并区分 `USER` 与 `SERVICE`。

## 验证

```powershell
mvn -q test
terraform -chdir=infra/terraform fmt -check
terraform -chdir=infra/terraform validate
docker compose config
powershell -File scripts/assert-local-only.ps1
powershell -File scripts/smoke-test.ps1
powershell -File scripts/mcp-test.ps1
powershell -File scripts/security-test.ps1
```

实际验证结果和未覆盖的边界见 [docs/test-evidence.md](docs/test-evidence.md)。
