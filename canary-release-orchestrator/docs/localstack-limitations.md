# LocalStack 限制与证据策略

所有 Java SDK 客户端都显式覆盖端点，每个 Lambda 都会接收到 `AWS_ENDPOINT_URL`；凭据使用 `us-east-1` 中的本地模拟值 `test/test`。系统不会回退到真实 AWS。

主要的可观测性路径是网关调用 CloudWatch `PutMetricData`。评估器使用 candidate 的 `Version` 维度调用 CloudWatch `GetMetricStatistics`。如果窗口内没有 `RequestCount` 数据点，评估器会按请求数不足处理并返回 `INSUFFICIENT_REQUESTS`；项目不会引入第二套指标存储。

完成资源配置后，运行 `scripts/smoke-test.ps1`，针对当前模拟器测试 `PutMetricData -> GetMetricStatistics`。如果冒烟检查失败，应先修复 LocalStack 的 CloudWatch 访问或指标聚合问题；发布流程不会静默改用其他指标来源。

LocalStack 执行 Lambda 需要能够访问 Docker socket，并设置 `LAMBDA_DOCKER_NETWORK=canary-network`；这些配置由运行 LocalStack 的外部环境提供。本仓库不会创建或管理 LocalStack 容器。Terraform 仍然会定义符合生产形态的 IAM 角色和策略，但 LocalStack 未必会像 AWS 一样精确执行每一项 IAM 决策。
