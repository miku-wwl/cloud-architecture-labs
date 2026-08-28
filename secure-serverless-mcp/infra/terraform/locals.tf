locals {
  name_prefix        = "secure-mcp-"
  user_data_table    = "mcp-user-data"
  lambda_package_dir = var.lambda_package_dir != "" ? var.lambda_package_dir : "${path.module}/../build/lambda"
  cognito_issuer     = var.cognito_issuer != "" ? var.cognito_issuer : "http://localhost.localstack.cloud:4566/${aws_cognito_user_pool.mcp.id}"
}
