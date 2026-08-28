resource "aws_cloudwatch_log_group" "lambda" {
  for_each          = toset(["set_weight", "evaluate_health", "finalize_release"])
  name              = "/aws/lambda/canary-${each.key}"
  retention_in_days = 1
}

resource "aws_cloudwatch_log_group" "state_machine" {
  name              = "/aws/vendedlogs/states/CanaryReleaseWorkflow"
  retention_in_days = 1
}
