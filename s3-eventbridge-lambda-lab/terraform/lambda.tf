resource "aws_lambda_function" "order_processor" {
  function_name    = local.lambda_name
  role             = aws_iam_role.lambda.arn
  runtime          = "java21"
  handler          = "com.example.eventlab.OrderProcessorHandler::handleRequest"
  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  timeout          = 30
  memory_size      = 512

  environment {
    variables = {
      AWS_ENDPOINT_URL     = var.lambda_endpoint
      AWS_REGION           = var.aws_region
      BUCKET_NAME          = aws_s3_bucket.orders.bucket
      EXPECTED_BUCKET_NAME = aws_s3_bucket.orders.bucket
      S3_FORCE_PATH_STYLE  = "true"
    }
  }
}
