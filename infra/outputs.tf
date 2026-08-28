output "event_bus_name" {
  value = aws_cloudwatch_event_bus.canary.name
}

output "state_machine_arn" {
  value = aws_sfn_state_machine.canary.arn
}

output "routing_table_name" {
  value = aws_dynamodb_table.routing.name
}

output "release_table_name" {
  value = aws_dynamodb_table.releases.name
}

output "metrics_table_name" {
  value = aws_dynamodb_table.metrics.name
}
