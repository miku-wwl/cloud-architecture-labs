# 测试证据

## 环境与版本

以下是本次本地验证实际使用或观察到的版本：

| 项目 | 版本 / 值 |
|---|---|
| Java（主机） | 21.0.6 |
| Lambda Java runtime（LocalStack 日志） | 21.0.12.1 |
| Spring Boot | 3.4.5 |
| Official MCP Java SDK | 2.0.1 |
| aws-serverless-java-container-springboot3 | 2.1.5 |
| AWS SDK for Java v2 | 2.31.27 |
| Terraform | 1.14.0 |
| LocalStack 健康接口观察值 | edition `pro`，开发版本 `2026.8.0.dev194` |
| LocalStack endpoint | `http://localhost:4566` |

用户环境声明使用 LocalStack Ultimate；健康接口返回的 edition 是 `pro`，因此 Ultimate 授权状态在本次记录中标为未独立验证。

## 已部署资源观察值

| 资源 | 实际值 |
|---|---|
| API id | `c227e8cf` |
| MCP endpoint | `http://c227e8cf.execute-api.localhost.localstack.cloud:4566/mcp` |
| User Pool | `us-east-1_1ccedad4322945b6b5f4187a585e2277` |
| JWT authorizer | `0391928c` |
| Lambda | `secure-mcp-server` |
| DynamoDB table | `mcp-user-data` |
| Resource server | `mcp-api` |
| Scope | `mcp-api/read` |
| Issuer | `http://localhost.localstack.cloud:4566/us-east-1_1ccedad4322945b6b5f4187a585e2277` |
| JWKS | `http://localhost.localstack.cloud:4566/us-east-1_1ccedad4322945b6b5f4187a585e2277/.well-known/jwks.json` |
| Token endpoint | `http://localhost:4566/_aws/cognito-idp/oauth2/token` |

本次 token 获取返回 `expires_in=3600`，JWT payload 的 scope 为 `mcp-api/read`；观察到的 `kid` 为 `2acff419-7ad1-455b-a0a2-ee0a9f6471c7`，并已与 JWKS 中的 key 匹配。完整 JWT、client secret 和授权 token 均不记录。每次 LocalStack 重建后 `kid` 可能变化，应重新检查。

## 验证结果

| 检查 | 结果 | 说明 |
|---|---|---|
| Java/Maven 单元测试 | PASS | 根项目 `mvn -q test` 已通过。 |
| Terraform 格式 | PASS | `terraform fmt -check`。 |
| Terraform 配置 | PASS | `terraform validate`。 |
| Compose 配置 | PASS | `docker compose config`，只解析配置，没有启动容器。 |
| Local-only 静态检查 | PASS | 无真实 AWS endpoint、无 Function URL、无 profile 依赖。 |
| 资源冒烟检查 | PASS | Cognito、API Gateway v2、JWT Authorizer、Lambda、DynamoDB 和 CloudWatch 日志可读。 |
| 数据播种 | PASS | A/B 演示数据写入 `mcp-user-data`。 |
| MCP full-demo | PASS | 官方 Java Client 完成 token、initialize、tools/list、三个 tools/call。 |
| MCP 负向调用 | PASS | `limit=1000` 和 `delete_everything` 均被拒绝。 |
| S1 无令牌 | PASS | HTTP 401。 |
| S2 垃圾令牌 | PASS | HTTP 401。 |
| S3 错误 issuer / signature | PASS（本地状态码差异） | 请求被阻断，LocalStack 返回 HTTP 500。 |
| S4 缺少 scope | PASS | HTTP 403。 |
| S5 正确令牌 | PASS | MCP initialize 返回 200 和 session id。 |
| S6 主体参数注入 | PASS | `userId` 不在工具 schema 中，调用被拒绝。 |
| S7 主体隔离 | PASS | 主体 B 只得到 B 绑定的数据。 |
| S8 无 Lambda Function URL | PASS | Terraform 和资源检查均未发现 Function URL。 |

## 未执行事项

- 未执行 `docker compose up`、`docker start` 或 `docker run`；LocalStack 由外部环境提供。
- 未执行 `git add`、`git commit`、`git push`，未修改 remote，也未调用 GitHub API。
- 高级 SSE、GET stream、resume、subscription 和 server push：NOT VERIFIED（明确不在本实验范围）。
