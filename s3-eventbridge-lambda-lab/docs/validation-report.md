# 验证报告

> 本报告记录本实验的验证方法和结果。每次重新执行 `make verify` 后，应以终端输出中的最终结果和时间为准更新本文件。

## 实验范围

- 架构：S3 → EventBridge → Java Lambda → S3。
- 部署：Terraform `apply`。
- 运行环境：用户本机已经启动的 LocalStack Ultimate `http://localhost:4566`。
- 禁止项：不启动或创建 Docker 容器，不调用 `aws lambda invoke`，不连接真实 AWS。

## 验证命令

```bash
make verify
```

脚本会依次执行：

1. 检查现有 LocalStack 健康状态和所需服务。
2. 执行 Java Lambda 单元测试和打包。
3. 执行 Terraform 格式化检查与配置校验。
4. 使用 Terraform `init`、`validate`、`apply` 部署基础设施。
5. 读取并核对 S3 EventBridge 配置、EventBridge 规则、目标和 Lambda permission。
6. 上传合法、忽略目录和 malformed 文件，轮询 S3 结果。
7. 使用 Terraform `apply -destroy` 清理资源并验证资源边界。

## 预期结果

| 检查项 | 预期 |
| --- | --- |
| LocalStack | `200 OK`，S3、EventBridge、IAM、Lambda、STS 可用 |
| Java 单元测试 | 3 个测试通过 |
| Terraform | `fmt` 和 `validate` 通过 |
| Terraform apply | 创建 8 类主要资源 |
| Lambda 日志权限 | 执行角色包含 `logs:CreateLogGroup`、`logs:CreateLogStream`、`logs:PutLogEvents` |
| 合法订单 | `processed/order-001.result.json`，状态为 `PROCESSED` |
| ignored/ 对象 | 不产生处理结果 |
| malformed JSON | `processed/malformed-order.error.json`，状态为 `INVALID` |
| 递归检查 | `processed/` 结果不会再次触发处理 |
| Terraform destroy | 资源清理完成，测试桶、规则、Lambda 和 IAM 角色不存在 |

## 实际执行记录

最终执行时间：2026-08-30 10:05（Pacific/Auckland）。最终执行命令为：

```bash
make verify
```

最终执行结果：

- Java 单元测试：PASS，3 tests，0 failures，0 errors。
- Java Lambda 打包：PASS，生成 `lambda/target/order-processor.jar`。
- Terraform `fmt`：PASS。
- Terraform `init -backend=false`：PASS。
- Terraform `validate`：PASS。
- LocalStack `terraform apply` E2E：PASS，创建 8 个资源。
- Lambda 日志 IAM 权限：PASS，包含创建日志组、创建日志流和写入日志事件权限。
- S3 EventBridge notification：PASS，`eventbridge=true`。
- EventBridge Rule：PASS，状态为 `ENABLED`，来源为 `aws.s3`，key 前缀为 `input/`。
- EventBridge Target：PASS，唯一目标为 `event-driven-orders-processor`。
- Lambda permission：PASS，主体为 `events.amazonaws.com`，来源 ARN 为本实验 Rule。
- 合法订单 E2E：PASS，`S3 → EventBridge → Lambda → S3`，生成 `processed/order-001.result.json`。
- `ignored/` 负向 E2E：PASS，没有生成处理结果。
- malformed JSON E2E：PASS，生成 `processed/malformed-order.error.json`，状态为 `INVALID`。
- 递归检查：PASS，`processed/` 下只有预期的 2 个结果对象。
- destroy 后资源边界：PASS，S3 桶、EventBridge Rule、Lambda 和 IAM Role 均已清理。
- 直接 Lambda 调用检查：PASS，验证脚本没有调用 `aws lambda invoke`。

## 结论

最终结论：`FINAL_ACCEPTANCE PASS`

本次验证使用 LocalStack Ultimate `2026.8.0.dev194`，endpoint 为 `http://localhost:4566`，未创建或启动任何 Docker 容器，也未连接真实 AWS。

如果 LocalStack 版本或运行时能力导致某个步骤失败，必须保留实际错误、失败阶段和是否完成清理，不能把未验证项目标为 PASS。
