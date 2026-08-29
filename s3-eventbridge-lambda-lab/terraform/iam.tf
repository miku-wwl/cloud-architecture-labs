resource "aws_iam_role" "lambda" {
  name = local.role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "lambda" {
  name = "${local.role_name}-execution"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadInputObjects"
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.orders.arn}/input/*"
      },
      {
        Sid      = "WriteProcessedObjects"
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${aws_s3_bucket.orders.arn}/processed/*"
      },
      {
        Sid      = "CreateLambdaLogGroup"
        Effect   = "Allow"
        Action   = "logs:CreateLogGroup"
        Resource = "*"
      },
      {
        Sid    = "WriteLambdaLogEvents"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:*:log-group:/aws/lambda/${local.lambda_name}:*"
      }
    ]
  })
}
