# 认证与授权流程

## Client Credentials

Terraform 创建一个 Cognito User Pool、资源服务器 `mcp-api`、scope `mcp-api/read` 和三个客户端：

- 客户端 A：带 scope，用于正向调用。
- 客户端 B：带 scope，用于主体隔离验证。
- `no_scope` 客户端：可获取令牌，但没有 `mcp-api/read`，用于验证 scope 授权。

客户端 CLI 使用 Basic Authentication 调用 LocalStack Cognito token endpoint，只输出过期时间、scope 和短指纹，不输出完整 access token。

## JWT 验证

API Gateway JWT Authorizer 配置为：

- issuer：以 Terraform 创建的 User Pool 和实际 token 的 `iss` 为准。
- audience：三个 Cognito client id。
- identity source：`$request.header.Authorization`。
- route scope：`mcp-api/read`。

验证时应检查 JWT header 的 `kid` 能够在 issuer 对应的 `/.well-known/jwks.json` 中找到。应用本身不接受客户端伪造的 `X-Mcp-Principal-*` 身份头；Lambda 适配器先删除这些输入，再从 API Gateway 已验证的 JWT claims 生成内部头。

## Principal 绑定

服务端先读取官方 MCP exchange transport context 中的主体，再回退到受信任的请求过滤器上下文。只有来源为 `validated-jwt` 的主体，或显式开启的本地调试主体，才允许访问工具。工具参数中没有用户选择主体的字段，因此 DynamoDB key 始终来自服务端解析的主体。

完整 issuer、JWKS endpoint、client id 和本次验证的 fingerprint 见 [test-evidence.md](test-evidence.md)；完整令牌永远不写入文档。
