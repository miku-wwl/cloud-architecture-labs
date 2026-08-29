resource "aws_cloudwatch_dashboard" "bedrock" {
  dashboard_name = "${local.name}-observability"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6,
        properties = {
          title = "Requests and failures by model", region = var.aws_region, view = "timeSeries",
          metrics = [
            [local.namespace, "genai_bedrock_requests_total", "model_id", var.bedrock_model_id],
            [".", "genai_bedrock_failures_total", ".", "."]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6,
        properties = {
          title = "Throttling and SDK retries", region = var.aws_region, stat = "Sum",
          metrics = [
            [local.namespace, "genai_bedrock_throttled_attempts_total", "model_id", var.bedrock_model_id],
            [".", "genai_bedrock_retries_total", ".", "."]
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6,
        properties = {
          title = "Average SDK and model latency", region = var.aws_region, stat = "Average",
          metrics = [
            [local.namespace, "genai_bedrock_sdk_api_call_duration_seconds", "model_id", var.bedrock_model_id],
            [".", "genai_bedrock_model_latency_seconds", ".", "."]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 6, width = 12, height = 6,
        properties = {
          title = "Token usage by model", region = var.aws_region, stat = "Sum",
          metrics = [
            [local.namespace, "genai_bedrock_input_tokens_total", "model_id", var.bedrock_model_id],
            [".", "genai_bedrock_output_tokens_total", ".", "."]
          ]
        }
      }
    ]
  })
}

resource "aws_cloudwatch_metric_alarm" "throttling" {
  alarm_name          = "${local.name}-bedrock-throttling"
  alarm_description   = "Bedrock throttled attempts exceed the lab threshold."
  namespace           = local.namespace
  metric_name         = "genai_bedrock_throttled_attempts_total"
  dimensions          = { model_id = var.bedrock_model_id }
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = var.throttling_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "high_latency" {
  alarm_name          = "${local.name}-bedrock-high-latency"
  alarm_description   = "Average AWS SDK Bedrock call latency exceeds the lab threshold."
  namespace           = local.namespace
  metric_name         = "genai_bedrock_sdk_api_call_duration_seconds"
  dimensions          = { model_id = var.bedrock_model_id }
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = var.high_latency_seconds_threshold
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
}
