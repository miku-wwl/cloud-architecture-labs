resource "aws_cloudwatch_log_group" "lambda" {
  for_each          = toset(["initialize_release", "set_weight", "evaluate_health", "finalize_release"])
  name              = "/aws/lambda/canary-${each.key}"
  retention_in_days = 1
}

resource "aws_cloudwatch_log_group" "state_machine" {
  name              = "/aws/vendedlogs/states/CanaryReleaseWorkflow"
  retention_in_days = 1
}

resource "aws_cloudwatch_dashboard" "canary" {
  dashboard_name = "canary-release-orchestrator"
  dashboard_body = jsonencode({
    widgets = [{
      type   = "metric"
      width  = 12
      height = 6
      properties = {
        view   = "timeSeries"
        region = var.aws_region
        title  = "Payment API canary requests and errors"
        metrics = [["CanaryDemo/PaymentAPI", "RequestCount", "Version", var.candidate_version],
        ["CanaryDemo/PaymentAPI", "ErrorCount", "Version", var.candidate_version]]
      }
    }]
  })
}
