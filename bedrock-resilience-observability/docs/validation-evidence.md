# 验证证据

验证日期：2026-08-30（Pacific/Auckland）

## Git 起点

- 仓库：`D:/workshop/aug/cloud-architecture-labs`
- 分支：`main`
- HEAD：`47c4e715a18205549b762ec10b9fe8b6dfcb0a33`
- 初始 `git status --short`：干净。

## 环境

| 组件 | 实测值 |
|---|---|
| Java | Eclipse Temurin 21.0.6 LTS |
| Maven Wrapper | Apache Maven 3.9.9 |
| Terraform | 1.14.0 windows_amd64 |
| AWS CLI | 2.27.47 |
| LocalStack | 2026.8.0.dev194，edition=pro |
| LocalStack endpoint | `http://localhost:4566` |
| 本地模型 | `ollama.smollm2:360m` |

真实 AWS credentials：未配置，也不需要。

## Demo 结构精简

生产 Java 文件从 27 个减少到 7 个，总 Java 文件从 29 个减少到 9 个，即原文件数的约 31%。核心行为没有删除：Bedrock Converse、SDK Standard Retry、retry/backoff 指标、CloudWatch、X-Ray 和 LocalStack 兼容处理仍然保留。

## JUnit / WireMock

命令：

```powershell
.\mvnw.cmd -q test
```

结果：8 tests，8 passed，0 failed，0 errors，0 skipped。其中 7 个是 Spring Boot + 真实 BedrockRuntimeClient + JVM WireMock集成测试，1 个是 LocalStack `latencyMs`兼容单元测试。

| 场景 | 实际 attempts | SDK RetryCount | Backoff | 结果 |
|---|---:|---:|---:|---|
| SUCCESS | 1 | 0 | 0 ms | HTTP 200 |
| TRANSIENT_THEN_SUCCESS | 3 | 2 | 157 ms | HTTP 200 |
| THROTTLE_THEN_SUCCESS | 3 | 2 | 1713 ms | HTTP 200，throttled=true |
| PERSISTENT_THROTTLE | 3 | 2 | 1092 ms | 受控 HTTP 429 |
| MODEL_LATENCY_COMPARISON | 各 1 | 0 | 0 ms | fast=25 ms，slow=900 ms |

退避包含 jitter，数值每次会变化；断言只要求大于 0。

## LocalStack E2E

完整回归命令：

```powershell
& 'D:\Git\bin\bash.exe' scripts/verify.sh
```

该命令依次执行 JUnit、Terraform格式检查、真实 LocalStack `terraform apply`、部署期间的应用 E2E，以及 `terraform apply -destroy`。最终结果：**VERIFY PASS**。

```text
LOCALSTACK READY version=2026.8.0.dev194 bedrock-runtime=running
OTEL COLLECTOR READY: http://localhost:13133
APP READY: http://localhost:18080/actuator/health
TERRAFORM_APPLY PASS resources=6 dashboards=1 alarms=2 roles=1 rolePolicies=1 parameters=1
LOCALSTACK_STS PASS role=tf-e2e-bedrock-resilience-app temporaryCredentials=true
LOCALSTACK_SSM_CONFIG PASS parameter=/tf-e2e-bedrock-resilience/application/bedrock-model-id model=ollama.smollm2:360m requestModelIdOmitted=true
LOCALSTACK_BEDROCK PASS model=ollama.smollm2:360m tokens=57 modelLatencyMs=283744 retryCount=0
ACTUATOR_METRICS PASS model dimension present, prompt absent
LOCALSTACK_CLOUDWATCH PASS metrics=18
LOCALSTACK_XRAY PASS traceId=1-6b2e83a3-9a5d68751bbf85662f5877cd bedrock.converse=true modelId=true
LOCALSTACK E2E PASS: Bedrock + Actuator + CloudWatch + X-Ray
TERRAFORM_DESTROY PASS resources=0 dashboards=0 alarms=0 roles=0 rolePolicies=0 parameters=0 stateRemoved=true
TERRAFORM_LOCALSTACK_E2E PASS: apply -> application E2E -> destroy
VERIFY PASS
```

脚本结束后 18080、4318、13133均无监听进程，说明应用和 Collector已清理。LocalStack保持运行。

## 发现并修复的问题

LocalStack原始 Converse响应：

```json
"metrics": {"latencyMs": 76266.0}
```

AWS Java SDK 2.31.27 要求整数，最初产生 `VALUE_NUMBER_FLOAT` 反序列化错误并对 HTTP 200重试三次。新增 local-only response interceptor后，真实应用 E2E成功，RetryCount恢复为 0。AWS profile不加载该兼容处理。

## Terraform

```text
terraform fmt -check -recursive  PASS
terraform init -reconfigure      PASS
terraform validate               PASS
terraform apply                  PASS（6 added）
terraform apply -destroy         PASS（6 destroyed）
```

Terraform CLI通过真实 AWS provider，以 `test/test` 连接 `http://localhost:4566`，显式 `terraform apply`创建并读取：

- Dashboard：`arn:aws:cloudwatch::000000000000:dashboard/tf-e2e-bedrock-resilience-observability`。
- 两个 CloudWatch Alarm，ARN账号同为 `000000000000`。
- IAM Role和内联策略：应用可读取指定 SSM参数、调用配置的 Bedrock模型并发布遥测。
- SSM Parameter：`/tf-e2e-bedrock-resilience/application/bedrock-model-id`，值为 `ollama.smollm2:360m`。

上述 6 个资源保持部署时，脚本 AssumeRole并把临时凭据交给 Spring Boot。请求不传 `modelId`，应用从 SSM读到 Terraform配置后成功调用 Bedrock。之后通过 `terraform apply -destroy` 销毁，并用 AWS CLI查询得到全部资源计数为 0；临时 state也已删除。Terraform配置不包含 ECS、ALB、VPC或ECR，因此没有触发容器创建。

## 已确认的 LocalStack Terraform限制

`aws_xray_sampling_rule` 可以在 LocalStack创建，但 AWS provider随后调用 `ListTagsForResource` 时收到 501，导致资源无法正常完成 apply/destroy闭环。最终配置没有保留该资源，也没有用 `local-exec`绕过 provider。X-Ray Trace仍通过运行时 E2E验证。

## 容器边界

- 项目执行的 `docker run`：0。
- 项目执行的 `docker compose up`：0。
- 项目创建的 Docker容器：0。
- 使用的 LocalStack：用户预先运行的实例。

LocalStack在接到 Bedrock Runtime请求后可能按自身配置启动内部 Ollama引擎；项目没有调用 Docker API管理该引擎。

## AWS

- **AWS REAL DEPLOYMENT: NOT RUN**
- 真实 AWS `terraform apply`：NOT RUN
- 真实 Amazon Bedrock/X-Ray/CloudWatch：NOT VERIFIED

LocalStack E2E不能冒充真实 AWS E2E。
