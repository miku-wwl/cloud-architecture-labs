resource "aws_cloudwatch_event_bus" "canary" {
  name = "canary-release-bus"
}

resource "aws_cloudwatch_event_rule" "release_requested" {
  name           = "canary-release-requested"
  description    = "Start the canary workflow for release requests"
  event_bus_name = aws_cloudwatch_event_bus.canary.name
  event_pattern = jsonencode({
    source      = ["demo.canary"]
    detail-type = ["CanaryReleaseRequested"]
  })
}

resource "aws_cloudwatch_event_target" "release_workflow" {
  rule           = aws_cloudwatch_event_rule.release_requested.name
  event_bus_name = aws_cloudwatch_event_bus.canary.name
  arn            = aws_sfn_state_machine.canary.arn
  role_arn       = aws_iam_role.events.arn
  input_path     = "$.detail"
}
