# LocalStack 限制与证据策略

所有 Java SDK 客户端都显式覆盖端点，每个 Lambda 都会接收到 `AWS_ENDPOINT_URL`；凭据使用 `us-east-1` 中的本地模拟值 `test/test`。系统不会回退到真实 AWS。

主要的可观测性路径是网关调用 CloudWatch `PutMetricData`。评估器使用 candidate 的 `Version` 维度调用 CloudWatch `GetMetricStatistics`。部分 LocalStack 版本的指标统计聚合可能不完整或存在延迟。当 `GetMetricStatistics` 抛出错误或没有返回 `RequestCount` 数据点时，评估器会输出 `cloudwatch_evaluation_fallback`，并查询 DynamoDB 表 `canary-metrics-window` 中逐请求保存的证据。该表是确定性的证据镜像，不是指标发布的替代品。返回结果中的 `metricSource` 会在工作流结果和日志中明确标识实际使用的证据来源。

完成资源配置后，运行 `scripts/smoke-test.ps1`，针对当前模拟器测试 `PutMetricData -> GetMetricStatistics`。即使冒烟检查失败，发布流程仍可通过显式的 DynamoDB 回退保持可复现；但必须在本次运行的证据中报告该限制。

LocalStack 执行 Lambda 需要能够访问 Docker socket，并设置 `LAMBDA_DOCKER_NETWORK=canary-network`；这些配置应由运行 LocalStack 的外部环境提供。本仓库不会创建或管理 LocalStack 容器。Terraform 仍然会定义符合生产形态的 IAM 角色和策略，但 LocalStack 未必会像 AWS 一样精确执行每一项 IAM 决策。
