# LocalStack 限制与适配说明

本实验只使用外部已经运行的 LocalStack `http://localhost:4566`。仓库中的 `docker-compose.yml` 是可选拓扑和配置示例，本次验证没有执行 `docker compose up`，脚本也不会创建或停止容器。

## 当前观察到的限制

1. Java Lambda 首次启动较慢。LocalStack Lambda 运行时默认启动等待窗口可能只有约 20 秒，因此 `StreamLambdaHandler` 对 Spring Boot handler 做惰性初始化，避免在类加载阶段触发超时。
2. API Gateway v2 / serverless servlet 适配过程中，Accept 头可能只保留逗号分隔值的第一项。Lambda 适配器将 MCP 需要的两个媒体类型规范化为 `application/json; text-event-stream`，以兼容本实验的 JSON 请求响应路径。
3. 当前已有 LocalStack 容器没有向 Lambda 运行时提供可解析的 `localstack` DNS 别名。Terraform 本次 apply 使用 `lambda_localstack_endpoint=http://host.docker.internal:4566`；如果 Lambda 和 LocalStack 在同一个 Compose 网络，应使用默认的 `http://localstack:4566`。
4. S3 篡改 issuer/signature 的请求被阻断，但本地模拟器返回 HTTP 500，而不是常见的 401/403。脚本记录实际状态，不把它伪装成 AWS 的状态码。
5. 本实验只配置 `POST /mcp`，不验证长连接 SSE、GET stream、resume、subscription 或 server push。高级 transport 能力不在学习目标范围内。

## 保持不变的安全原则

这些适配只解决本地模拟器和 Lambda 容器的运行时差异，不绕过 API Gateway JWT Authorizer。应用仍然拒绝真实 AWS endpoint，工具仍然只能读取已验证 JWT 主体绑定的数据。
