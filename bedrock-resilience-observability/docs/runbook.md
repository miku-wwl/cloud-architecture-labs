# 运行与诊断手册

## 快速检查

```powershell
java -version
aws --version
terraform version
Invoke-RestMethod http://localhost:4566/_localstack/health
```

健康结果中应至少存在 `bedrock-runtime`、`cloudwatch` 和 `xray`。`bedrock-runtime=available` 只表示服务可启动；首次 Converse 后应变为 `running`。

## 完整本地 E2E

```powershell
.\scripts\e2e-local.ps1
```

脚本执行顺序：

1. 检查现有 LocalStack Ultimate。
2. 准备被 Git忽略的 OTel Java Agent/Collector。
3. 在宿主机启动 Collector。
4. 构建并在 18080 启动 Spring Boot。
5. 调用真实 LocalStack Bedrock Converse。
6. 检查 Actuator模型指标和 prompt泄漏。
7. 查询 LocalStack CloudWatch。
8. 按精确 trace ID查询 LocalStack X-Ray。
9. 清理脚本创建的应用/Collector进程；不停止 LocalStack。

## Terraform 部署后的完整 LocalStack E2E

```powershell
.\scripts\terraform-e2e-local.ps1
```

该脚本直接调用 Terraform CLI，不经过 Terraform测试框架。它依次执行：

1. 检查现有 LocalStack 和测试资源边界。
2. 执行 `terraform init -reconfigure`、`terraform validate` 和真实 `terraform apply`。
3. 通过 AWS CLI确认 Role 1 个、内联策略 1 个、SSM参数 1 个、Dashboard 1 个、Alarm 2 个。
4. AssumeRole取得临时凭据，并用这些凭据启动 Spring Boot和 Collector。
5. 不在请求中传 `modelId`，验证应用从 SSM读取 Terraform配置后调用 Bedrock。
6. 验证 Actuator、CloudWatch 和 X-Ray。
7. 在 `finally` 中执行 `terraform apply -destroy`，再次确认 6 个资源全部消失，并删除临时 state。

输出 ARN 的账号应为 LocalStack 的 `000000000000`。执行前后均不应存在 `tf-e2e-bedrock-resilience` 前缀的残留资源。

## 只学习重试

```powershell
.\mvnw.cmd test
```

场景：

| 模型 | 注入响应 | 预期 |
|---|---|---|
| `lab.success-model` | 200 | 1 attempt、RetryCount=0 |
| `lab.transient-model` | 500、500、200 | 3 attempts、最终成功 |
| `lab.throttle-model` | 429、429、200 | 3 attempts、退避 > 0 |
| `lab.persistent-throttle` | 始终 429 | 3 attempts、受控 HTTP 429 |
| fast/slow | 均 200 | 模型延迟 25/900 ms |

这些模型只存在于 JUnit，不应写进实际 LocalStack allow-list。

## 常见问题

### 没有 AWS credentials

这是正常状态。local profile和脚本在当前进程内使用 `test/test`，不会读写真实 AWS凭据文件。

### Bedrock 显示 available 但调用很慢

LocalStack 会在首次 runtime请求时启动 Bedrock/Ollama引擎并加载模型。等待 health变为 `bedrock-runtime=running`。本实验把 local API timeout设为 180 秒。

### Java SDK 对 HTTP 200 重试三次

检查异常是否包含：

```text
expecting VALUE_NUMBER_INT, got VALUE_NUMBER_FLOAT
```

这是 LocalStack 2026.8 把 `metrics.latencyMs` 输出为浮点数导致。确认 `LocalStackConfiguration` 中的 local-only 响应拦截器已启用；AWS profile不应启用。

### 18080 被占用

指定其他端口：

```powershell
.\scripts\e2e-local.ps1 -AppPort 18081
```

### CloudWatch 没有指标

- 确认 `LOCALSTACK_CLOUDWATCH_ENABLED` 没有设为 `false`。
- 等待 Micrometer 5 秒发布周期。
- 查询：

```powershell
$env:AWS_ACCESS_KEY_ID="test"
$env:AWS_SECRET_ACCESS_KEY="test"
$env:AWS_DEFAULT_REGION="us-east-1"
aws --endpoint-url http://localhost:4566 cloudwatch list-metrics --namespace GenAI/BedrockLab
```

### X-Ray summary 没有 ServiceIds

当前 LocalStack summary索引可能返回空 ServiceIds。E2E不依赖该字段，而是从应用日志取得 W3C trace ID，转换为 X-Ray格式后调用 `batch-get-traces` 精确验证。

### Collector 下载失败

脚本使用 Collector Contrib 0.122.0。0.123.0 的 Windows release资产不完整，因此没有使用。下载完成后文件位于 `.tools/`。

### Terraform 意外访问真实 AWS

确认 `terraform/providers.tf` 中 IAM、STS、SSM、CloudWatch 和 X-Ray endpoint 均为 `http://localhost:4566`。本实验 Terraform不需要真实 AWS credentials，也不应出现 ECS、ALB、VPC或ECR资源。

### X-Ray Sampling Rule 无法由 Terraform闭环管理

当前 LocalStack可以创建 Sampling Rule，但缺少 AWS provider读取资源时需要的 `ListTagsForResource` 路由，会返回 501。实验因此不创建该 Terraform资源，避免 apply成功一半、destroy又被刷新阶段阻塞。Trace本身仍通过 Collector写入 X-Ray并按精确 trace ID验证。
