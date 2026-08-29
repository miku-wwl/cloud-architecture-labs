output "bucket_name" {
  value = aws_s3_bucket.orders.bucket
}

output "lambda_function_name" {
  value = aws_lambda_function.order_processor.function_name
}

output "eventbridge_rule_name" {
  value = aws_cloudwatch_event_rule.object_created.name
}

output "eventbridge_rule_arn" {
  value = aws_cloudwatch_event_rule.object_created.arn
}
