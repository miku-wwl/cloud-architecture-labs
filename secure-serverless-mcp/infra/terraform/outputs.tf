output "user_pool_id" {
  value = aws_cognito_user_pool.mcp.id
}

output "resource_server_identifier" {
  value = aws_cognito_resource_server.mcp.identifier
}

output "app_client_id_a" {
  value = aws_cognito_user_pool_client.principal_a.id
}

output "app_client_secret_a" {
  value     = aws_cognito_user_pool_client.principal_a.client_secret
  sensitive = true
}

output "app_client_id_b" {
  value = aws_cognito_user_pool_client.principal_b.id
}

output "app_client_secret_b" {
  value     = aws_cognito_user_pool_client.principal_b.client_secret
  sensitive = true
}

output "app_client_id_no_scope" {
  value = aws_cognito_user_pool_client.no_scope.id
}

output "app_client_secret_no_scope" {
  value     = aws_cognito_user_pool_client.no_scope.client_secret
  sensitive = true
}

output "oauth_token_endpoint" {
  value = "${var.localstack_endpoint}/_aws/cognito-idp/oauth2/token"
}

output "cognito_issuer" {
  value = local.cognito_issuer
}

output "cognito_jwks_endpoint" {
  value = "${local.cognito_issuer}/.well-known/jwks.json"
}

output "api_id" {
  value = aws_apigatewayv2_api.mcp.id
}

output "api_endpoint" {
  value = aws_apigatewayv2_api.mcp.api_endpoint
}

output "mcp_endpoint" {
  value = "${aws_apigatewayv2_api.mcp.api_endpoint}/mcp"
}

output "jwt_authorizer_id" {
  value = aws_apigatewayv2_authorizer.jwt.id
}

output "lambda_name" {
  value = aws_lambda_function.mcp.function_name
}

output "user_data_table" {
  value = aws_dynamodb_table.user_data.name
}
