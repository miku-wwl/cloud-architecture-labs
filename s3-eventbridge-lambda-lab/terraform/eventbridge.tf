resource "aws_cloudwatch_event_rule" "object_created" {
  name           = local.rule_name
  description    = "Route S3 input/ Object Created events to the Java order processor."
  event_bus_name = "default"

  event_pattern = jsonencode({
    source        = ["aws.s3"]
    "detail-type" = ["Object Created"]
    detail = {
      bucket = {
        name = [var.bucket_name]
      }
      object = {
        key = [{ prefix = "input/" }]
      }
    }
  })
}

resource "aws_cloudwatch_event_target" "order_processor" {
  rule           = aws_cloudwatch_event_rule.object_created.name
  event_bus_name = "default"
  target_id      = "order-processor-lambda"
  arn            = aws_lambda_function.order_processor.arn
}

resource "aws_lambda_permission" "eventbridge" {
  statement_id  = "AllowEventBridgeObjectCreated"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.order_processor.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.object_created.arn
}
