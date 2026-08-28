# 学习路线

## 已完成 / 当前实验

1. **Canary Release Orchestrator**：学习 EventBridge、Step Functions、Lambda、CloudWatch 如何组成指标驱动的发布编排。
2. **Secure Serverless MCP**：学习 Cognito OAuth、JWT Authorizer、Lambda 承载的远程 MCP、身份绑定和 DynamoDB 访问控制。

建议顺序是先看架构图，再阅读 Terraform 资源关系，最后运行验证脚本。每个实验都先从最小的正向路径开始，再观察失败和安全边界。

## 后续可选实验

以下只作为学习方向，不在当前仓库中创建目录：

- Saga 与补偿工作流
- Transactional Outbox
- Serverless 幂等性
- 事件驱动订单处理
- 分布式限流
- 蓝绿发布
- 灾难恢复
- 多区域故障转移
