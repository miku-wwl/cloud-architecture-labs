output "cloudwatch_dashboard_name" {
  value = aws_cloudwatch_dashboard.bedrock.dashboard_name
}

output "cloudwatch_alarm_names" {
  value = [
    aws_cloudwatch_metric_alarm.throttling.alarm_name,
    aws_cloudwatch_metric_alarm.high_latency.alarm_name
  ]
}

output "application_role_arn" {
  value = aws_iam_role.app.arn
}

output "bedrock_model_parameter_name" {
  value = aws_ssm_parameter.bedrock_model_id.name
}
