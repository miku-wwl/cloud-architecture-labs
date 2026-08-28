# 威胁模型

## 保护对象

- 不同服务主体的图书 profile、订单和偏好数据。
- API Gateway 到 Lambda 之间的身份边界。
- Terraform 和应用不误调用真实 AWS 的安全边界。
- 令牌和凭据不出现在日志、样例数据和文档中。

## 主要威胁与控制

| 威胁 | 控制 | 证据 |
|---|---|---|
| 没有令牌访问 MCP | API Gateway JWT Authorizer | S1：HTTP 401 |
| 伪造或损坏的令牌 | JWT 签名、issuer、JWKS 验证 | S2：401；S3：本地模拟器返回 500 并阻断 |
| 合法令牌缺少 scope | 路由要求 `mcp-api/read` | S4：HTTP 403 |
| 客户端伪造主体头 | Lambda 删除输入头，只注入 API Gateway claims | S6：注入参数被 MCP schema 拒绝 |
| 主体 A 读取主体 B | 工具不接收主体参数，DynamoDB key 来自 JWT | S7：主体 B 只能得到 B 的数据 |
| 绕过 API Gateway 直达 Lambda | 不创建 Function URL，仅保留 API Gateway route | S8：资源检查无 Function URL |
| 真实 AWS 误调用 | endpoint override、fake credentials、local-only host guard | `assert-local-only.ps1`：PASS |

## 数据策略

种子数据使用 `alice@example.local` 和演示主体 id，不包含真实密码、JWT、access token、银行卡或真实个人信息。脚本只输出 token fingerprint，不输出完整令牌。客户端 secret 由 Terraform sensitive output 提供给当前进程，不写入仓库。

## 剩余边界

本实验是本地学习项目，不等同于生产部署。LocalStack 对 JWT 错误可能返回与 AWS 不同的状态码；当前 S3 已观察到 500。生产系统还需要 TLS、密钥轮换、审计、限流、WAF、真正的 secret 管理和更完整的会话策略。
