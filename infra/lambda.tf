locals {
  lambda_names = toset(["initialize_release", "set_weight", "evaluate_health", "finalize_release"])
}

data "archive_file" "lambda" {
  for_each    = local.lambda_names
  type        = "zip"
  source_dir  = "${path.module}/../lambda/${each.key}"
  output_path = "${path.module}/build/${each.key}.zip"
}

resource "aws_lambda_function" "control" {
  for_each         = local.lambda_names
  function_name    = "canary-${each.key}"
  role             = aws_iam_role.lambda.arn
  handler          = "handler.handler"
  runtime          = "python3.11"
  filename         = data.archive_file.lambda[each.key].output_path
  source_code_hash = data.archive_file.lambda[each.key].output_base64sha256
  timeout          = 30

  environment {
    variables = {
      AWS_ENDPOINT_URL          = var.lambda_localstack_endpoint
      AWS_REGION                = var.aws_region
      AWS_ACCESS_KEY_ID         = var.aws_access_key
      AWS_SECRET_ACCESS_KEY     = var.aws_secret_key
      SERVICE_NAME              = var.service_name
      STABLE_VERSION            = var.stable_version
      CANDIDATE_VERSION         = var.candidate_version
      ROUTING_TABLE             = aws_dynamodb_table.routing.name
      RELEASE_TABLE             = aws_dynamodb_table.releases.name
      METRICS_TABLE             = aws_dynamodb_table.metrics.name
      EVENT_BUS_NAME            = aws_cloudwatch_event_bus.canary.name
      CLOUDWATCH_NAMESPACE      = "CanaryDemo/PaymentAPI"
      EVALUATION_WINDOW_SECONDS = tostring(var.evaluation_window_seconds)
      MINIMUM_REQUEST_COUNT     = tostring(var.minimum_request_count)
      MAX_ERROR_RATE            = tostring(var.max_error_rate)
      MAX_LATENCY_MS            = tostring(var.max_latency_ms)
    }
  }

  depends_on = [aws_iam_role_policy.lambda]
}
