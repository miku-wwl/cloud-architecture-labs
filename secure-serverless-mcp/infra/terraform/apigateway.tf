resource "aws_apigatewayv2_api" "mcp" {
  name          = "${local.name_prefix}http-api"
  protocol_type = "HTTP"
  description   = "Cognito protected Streamable HTTP MCP endpoint"
}

resource "aws_apigatewayv2_integration" "mcp" {
  api_id                 = aws_apigatewayv2_api.mcp.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.mcp.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
  timeout_milliseconds   = 29000
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.mcp.id
  authorizer_type  = "JWT"
  authorizer_uri   = null
  identity_sources = ["$request.header.Authorization"]
  name             = "${local.name_prefix}jwt-authorizer"

  jwt_configuration {
    audience = [
      aws_cognito_user_pool_client.principal_a.id,
      aws_cognito_user_pool_client.principal_b.id,
      aws_cognito_user_pool_client.no_scope.id,
    ]
    issuer = local.cognito_issuer
  }
}

resource "aws_apigatewayv2_route" "mcp_post" {
  api_id               = aws_apigatewayv2_api.mcp.id
  route_key            = "POST /mcp"
  target               = "integrations/${aws_apigatewayv2_integration.mcp.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.jwt.id
  authorization_scopes = ["mcp-api/read"]
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.mcp.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_permission" "apigateway" {
  statement_id  = "AllowApiGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.mcp.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.mcp.execution_arn}/*/*"
}
