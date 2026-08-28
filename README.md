# Cloud Architecture Labs

这是一个面向学习的云架构实验仓库，使用 Java、Terraform 和 LocalStack 在本地复现真实的云架构模式。每个目录都是一个独立、可运行、可验证的小项目。

## 实验项目

| 实验 | 状态 | 架构重点 | 关键概念 |
|---|---|---|---|
| [Canary Release Orchestrator](canary-release-orchestrator/README.md) | ✅ 已完成 | EventBridge → Step Functions → Lambda → CloudWatch | 金丝雀发布、渐进式交付、指标门控晋级、自动回滚 |
| [Secure Serverless MCP](secure-serverless-mcp/README.md) | ✅ 已完成 | Cognito → API Gateway → Lambda → MCP → DynamoDB | OAuth、JWT、远程 MCP、Streamable HTTP、身份传播 |

## 仓库结构

```text
cloud-architecture-labs/
├── canary-release-orchestrator/  金丝雀发布编排实验
└── secure-serverless-mcp/        安全远程 MCP 实验
```

每个项目都有自己的 `README.md`、源码、基础设施、脚本和项目级文档。根目录只负责列出项目，不承载某一个实验的 `apps`、`infra` 或 `docs`。

## 架构地图

### 01. Canary Release Orchestrator

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

该项目用简单的图书借阅业务承载发布流程，学习重点是 EventBridge、Step Functions、Lambda 和 CloudWatch 组成的指标驱动发布编排。

### 02. Secure Serverless MCP

```text
Java MCP Client
    ↓
Cognito OAuth / JWT / JWKS
    ↓
API Gateway V2 JWT Authorizer
    ↓
Java Lambda
    ↓
MCP Streamable HTTP
    ↓
Principal-bound Tools
    ↓
DynamoDB
```

该项目使用两个 Cognito 客户端模拟不同服务主体，重点学习 OAuth、JWT Authorizer、远程 MCP、身份传播和主体隔离。

## 建议学习顺序

1. 阅读项目 README 和架构图。
2. 查看 Terraform 资源关系。
3. 运行最小正向路径。
4. 执行自动化验证和失败场景。
5. 对照项目中的测试证据理解 LocalStack 与真实 AWS 的差异。

## 实验约定

- 每个实验保持自包含，禁止跨项目共享运行时状态。
- 基础设施优先使用 Terraform 定义。
- 正向路径必须真实穿过文档声称的云服务。
- 自动化验证同时覆盖正向路径和负向 / 故障场景。
- 不把令牌、密码或真实个人信息写入代码、样例数据和文档。
- 所有项目默认使用外部提供的 `http://localhost:4566`，不会创建真实 AWS 资源。
