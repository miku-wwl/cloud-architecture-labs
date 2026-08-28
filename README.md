# Cloud Architecture Labs

这是一个面向学习的云架构实验仓库，使用 Java、Terraform、Docker 和 LocalStack 在本地复现真实的云架构模式。每个实验都尽量保持自包含，并配有自动化校验、失败场景和可重复的基础设施定义。

## 技术主题

- Java 21 与 Spring Boot
- Terraform 基础设施即代码
- Docker 与 LocalStack 本地云模拟
- Serverless 与事件驱动架构
- 分布式系统与可靠性工程
- 身份认证、授权与安全边界

## 实验列表

| 实验 | 状态 | 架构重点 | 关键概念 |
|---|---|---|---|
| Canary Release Orchestrator | ✅ 已完成 | EventBridge → Step Functions → Lambda → CloudWatch | 金丝雀发布、渐进式交付、指标门控晋级、自动回滚 |
| Secure Serverless MCP | ✅ 已完成 | Cognito → API Gateway → Lambda → MCP → DynamoDB | OAuth、JWT、远程 MCP、Streamable HTTP、身份传播 |

## 目录结构

```text
docs/                        仓库级学习路线和实验规范
canary-release-orchestrator/ 原有金丝雀发布实验，保持独立可运行
secure-serverless-mcp/       安全远程 MCP 端到端实验
```

两个实验都默认连接已经由外部提供的 `http://localhost:4566`。本次工作不会创建、启动或停止 LocalStack 容器；也不会创建真实 AWS 资源。每个实验的详细说明、验证命令和已知限制见对应目录。

## Git 边界

本地实现和验证不会自动提交、推送或修改远程仓库。远程仓库改名由维护者在 GitHub 网页端完成。
