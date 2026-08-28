# 演示脚本

## 1. 确认外部 LocalStack

```powershell
Invoke-RestMethod http://localhost:4566/_localstack/health | ConvertTo-Json
```

确认服务已由外部环境监听 4566。不要在本实验中执行 `docker compose up`。

## 2. 构建并部署

```powershell
powershell -File scripts/build.ps1
terraform -chdir=infra/terraform init
terraform -chdir=infra/terraform apply -auto-approve -parallelism=1 -var "lambda_localstack_endpoint=http://host.docker.internal:4566"
powershell -File scripts/seed-local-data.ps1
```

如果 LocalStack 与 Lambda 位于同一个 Compose 网络，把 `lambda_localstack_endpoint` 改为 `http://localstack:4566`。

## 3. 运行正向 MCP 演示

```powershell
powershell -File scripts/mcp-test.ps1
```

应看到 OAuth 成功、`initialize`、三个工具列表项、三个工具调用，以及两个负向调用通过。

## 4. 运行安全矩阵

```powershell
powershell -File scripts/security-test.ps1
```

应看到 S1、S2、S3、S4、S6、S7 PASS。S3 在当前 LocalStack 版本预期打印 HTTP 500；这表示已被阻断，不代表授权成功。

## 5. 查看资源

```powershell
terraform -chdir=infra/terraform output
aws --endpoint-url http://localhost:4566 apigatewayv2 get-routes --api-id (terraform -chdir=infra/terraform output -raw api_id)
aws --endpoint-url http://localhost:4566 dynamodb describe-table --table-name mcp-user-data
```

演示结束后不要执行 `terraform destroy`，除非你明确希望删除当前 LocalStack 中的实验资源。
