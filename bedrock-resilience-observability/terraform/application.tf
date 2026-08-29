resource "aws_ssm_parameter" "bedrock_model_id" {
  name        = "/${local.name}/application/bedrock-model-id"
  description = "Terraform-managed model used by the Bedrock resilience lab."
  type        = "String"
  value       = var.bedrock_model_id
}

resource "aws_iam_role" "app" {
  name        = "${local.name}-app"
  description = "LocalStack role assumed by the Spring Boot Bedrock lab."

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        AWS = "arn:aws:iam::000000000000:root"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "app" {
  name = "${local.name}-app"
  role = aws_iam_role.app.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "InvokeConfiguredBedrockModel"
        Effect   = "Allow"
        Action   = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
        Resource = "arn:aws:bedrock:${var.aws_region}::foundation-model/${var.bedrock_model_id}"
      },
      {
        Sid      = "ReadTerraformModelConfiguration"
        Effect   = "Allow"
        Action   = "ssm:GetParameter"
        Resource = aws_ssm_parameter.bedrock_model_id.arn
      },
      {
        Sid      = "PublishApplicationTelemetry"
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData", "xray:PutTraceSegments", "xray:PutTelemetryRecords"]
        Resource = "*"
      }
    ]
  })
}
