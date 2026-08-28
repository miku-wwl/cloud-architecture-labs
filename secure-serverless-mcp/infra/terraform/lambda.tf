data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = local.lambda_package_dir
  output_path = "${path.module}/../build/secure-mcp-server.zip"
  excludes    = ["*.log"]
}

resource "aws_lambda_function" "mcp" {
  function_name    = "${local.name_prefix}server"
  role             = aws_iam_role.lambda.arn
  runtime          = "java21"
  handler          = "com.example.securemcp.StreamLambdaHandler::handleRequest"
  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256
  timeout          = 30
  memory_size      = 1024

  environment {
    variables = {
      AWS_ENDPOINT_URL      = var.lambda_localstack_endpoint
      AWS_REGION            = var.aws_region
      AWS_ACCESS_KEY_ID     = var.aws_access_key
      AWS_SECRET_ACCESS_KEY = var.aws_secret_key
      USER_DATA_TABLE       = local.user_data_table
      LOCAL_ONLY            = "true"
      ALLOW_DEBUG_PRINCIPAL = "false"
    }
  }

  depends_on = [aws_iam_role_policy.lambda]
}
