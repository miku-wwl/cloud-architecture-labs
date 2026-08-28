# E2E 测试报告

## 结论

2026 年 8 月 29 日，在用户已有的 LocalStack `http://localhost:4566` 上完成真实端到端验证，最终结果为 **PASS**。

验证链路：

```text
Java MCP Client
  → LocalStack Cognito OAuth Client Credentials
  → API Gateway V2 JWT Authorizer
  → Java 21 Lambda
  → Spring Boot + Official MCP Java SDK
  → DynamoDB
```

首次 MCP `initialize` 因 Java Lambda 冷启动耗时约 71.8 秒，超过客户端 60 秒超时而失败。使用同一 API 做直连诊断时最终返回 HTTP 200；Lambda 预热后重新执行完整脚本，OAuth、初始化、工具发现、三个工具调用和负向调用全部通过。因此最终结论是链路功能通过，同时保留冷启动风险记录。

## 环境与版本

| 项目 | 实际观察值 |
|---|---|
| 操作系统 | Windows 11 amd64 |
| Java | Eclipse Temurin 21.0.6 LTS |
| Maven | 3.9.9 |
| Spring Boot | 3.4.5 |
| Official MCP Java SDK | 2.0.1 |
| AWS SDK for Java v2 | 2.31.27 |
| Terraform | 1.14.0 |
| LocalStack endpoint | `http://localhost:4566` |
| LocalStack 健康接口 | edition `pro`，版本 `2026.8.0.dev194` |

用户环境声明使用 LocalStack Ultimate；健康接口只返回运行时 edition `pro`，因此本报告不把 Ultimate 订阅状态作为独立验证结论。

## 本次使用的资源

| 资源 | 实际值 |
|---|---|
| API Gateway API id | `fafc674d` |
| MCP endpoint | `http://fafc674d.execute-api.localhost.localstack.cloud:4566/mcp` |
| API route | `POST /mcp` |
| API integration | `AWS_PROXY`、`POST`、payload format `2.0` |
| Cognito User Pool | `us-east-1_a7f8e68108544bdfb357c32afab8cb1a` |
| JWT authorizer | `33de29a7` |
| Resource server | `mcp-api` |
| Scope | `mcp-api/read` |
| Issuer | `http://localhost.localstack.cloud:4566/us-east-1_a7f8e68108544bdfb357c32afab8cb1a` |
| JWKS | `http://localhost.localstack.cloud:4566/us-east-1_a7f8e68108544bdfb357c32afab8cb1a/.well-known/jwks.json` |
| Token endpoint | `http://localhost:4566/_aws/cognito-idp/oauth2/token` |
| Lambda | `secure-mcp-server` |
| Lambda runtime / 状态 | `java21` / `Active` / `LastUpdateStatus=Successful` |
| Lambda handler | `com.example.securemcp.StreamLambdaHandler::handleRequest` |
| DynamoDB table | `mcp-user-data` / `ACTIVE` |
| DynamoDB partition key | `principalId` |

LocalStack 中仍可能保留此前实验创建的旧资源；本报告只针对上表所列、当前 Terraform state 管理的资源。完整 JWT、client secret 和 access token 均未写入报告。

## 执行过程

### 1. 前置检查

- Git 分支为 `main`，验证开始前工作区干净。
- LocalStack 健康接口可访问。
- `lambda`、`dynamodb`、`apigatewayv2`、`cognito-idp` 均为 `running`。
- 未执行 `docker compose up`、`docker run` 或 `docker start`。

### 2. 构建与静态验证

执行内容：

```powershell
powershell -File scripts/build.ps1
mvn -q test
terraform -chdir=infra/terraform init -input=false
terraform -chdir=infra/terraform fmt -check
terraform -chdir=infra/terraform validate
docker compose config
powershell -File scripts/assert-local-only.ps1
```

结果：

- Maven 构建和 Lambda 打包通过。
- 3 个测试类共 6 个测试：0 failures、0 errors、0 skipped。
- Terraform 初始化、格式和配置校验通过。
- Compose 只做配置解析，没有启动容器。
- Local-only 检查通过：Terraform 只指向 LocalStack，没有 AWS profile、真实 AWS endpoint 或 Lambda Function URL 配置。

### 3. Terraform 状态恢复与部署

仓库是重新 clone 的工作区，本地没有此前未跟踪的 Terraform state，但 LocalStack 中仍有同名 DynamoDB 表和 IAM Role。第一次 apply 因此遇到：

- `mcp-user-data`：`ResourceInUseException`
- `secure-mcp-lambda-role`：`EntityAlreadyExists`

随后只导入确认存在的实验资源，包括 DynamoDB 表、IAM Role、Lambda、日志组和 Lambda permission，再补齐 API Gateway integration 与 route。没有执行 `terraform destroy`，也没有删除范围不明的旧资源。

最终完整检查：

```text
No changes. Your infrastructure matches the configuration.
```

### 4. 数据播种

执行：

```powershell
powershell -File scripts/seed-local-data.ps1
```

结果：主体 A/B 的演示数据成功写入 `mcp-user-data`，未写入真实 PII、token 或 client secret。

### 5. 正向 MCP E2E

执行：

```powershell
powershell -File scripts/mcp-test.ps1
```

第一次执行：

- OAuth 成功，`expiresIn=3600`，scope 为 `mcp-api/read`。
- MCP Client 在 `initialize` 等待 60 秒后超时。
- 直连同一 endpoint 的诊断证明 DNS、TCP 4566、JWT 和 API Gateway route 均正常。
- 直连 `initialize` 在约 71.8 秒后返回 HTTP 200，serverInfo 为 `secure-serverless-mcp/1.0.0`。

Lambda 预热后的第二次执行：

- OAuth：PASS。
- MCP `initialize`：PASS，协议版本 `2025-11-25`。
- `tools/list`：PASS，返回 3 个工具。
- `get_my_profile`：PASS。
- `list_my_orders`：PASS。
- `get_my_preferences`：PASS。
- `list_my_orders(limit=1000)`：PASS，非法参数被拒绝。
- 未知工具 `delete_everything`：PASS，被拒绝。

Windows Java CLI 中中文工具描述显示为 `????`，属于控制台字符显示问题；工具 schema、JSON 响应和调用结果均正确。

### 6. 安全矩阵

执行：

```powershell
powershell -File scripts/security-test.ps1
```

| 检查 | 结果 | 实际证据 |
|---|---|---|
| S1 无令牌 | PASS | HTTP 401 |
| S2 垃圾令牌 | PASS | HTTP 401 |
| S3 篡改 issuer / signature | PASS（LocalStack 状态码差异） | 请求被阻断，HTTP 500 |
| S4 缺少 scope | PASS | HTTP 403 |
| S5 正确令牌 | PASS | `initialize` 返回 HTTP 200 和 session id |
| S6 主体参数注入 | PASS | `userId` 不在工具 schema 中，调用被拒绝 |
| S7 主体隔离 | PASS | 主体 B 只获得 B 绑定的数据 |
| S8 无 Lambda Function URL | PASS | `GetFunctionUrlConfig` 返回 `ResourceNotFoundException` |

S3 的 HTTP 500 是当前 LocalStack JWT Authorizer 对篡改 token 的本地状态码差异；关键安全断言是请求没有进入 MCP 工具调用链路。

### 7. 资源冒烟与最终检查

执行：

```powershell
powershell -File scripts/smoke-test.ps1
terraform -chdir=infra/terraform plan -detailed-exitcode -var "lambda_localstack_endpoint=http://host.docker.internal:4566"
```

结果：

- Cognito、API Gateway V2、JWT Authorizer、Lambda、DynamoDB 和 CloudWatch 日志可读。
- API Gateway route 使用 JWT 授权和 `mcp-api/read` scope。
- Lambda 为 `Active`，最后更新状态为 `Successful`。
- Terraform 最终没有待应用变更。
- 验证结束后 Git 工作区干净。

## Docker 边界

本次没有由 Codex 执行任何 Docker 创建、启动或停止命令。调用 Java Lambda 时，LocalStack 自动生成了 `localstack-main-lambda-secure-mcp-server-*` Lambda runtime 容器，这是 LocalStack 的 Lambda 执行机制；外部 `localstack-main` 容器没有被停止或重建。

## 最终结果

| 验证层 | 结果 |
|---|---|
| Java 构建与单元测试 | PASS |
| Terraform 配置与最终一致性 | PASS |
| LocalStack 资源部署与可读性 | PASS |
| OAuth Client Credentials | PASS |
| API Gateway JWT 与 scope | PASS |
| MCP initialize / tools/list / tools/call | PASS |
| 参数与未知工具拒绝 | PASS |
| 主体注入防护与数据隔离 | PASS |
| Lambda Function URL 禁用边界 | PASS |
| 冷启动首次请求 | 观察项：约 71.8 秒，超过客户端默认 60 秒 |

综合结论：项目在现有 LocalStack 4566 环境中的核心安全 MCP Serverless E2E 链路验证通过。高级 SSE、GET stream、resume、subscription 和 server push 不在本实验范围，标记为 **NOT VERIFIED**。
