resource "aws_cognito_user_pool" "mcp" {
  name = "${local.name_prefix}pool"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_numbers   = true
    require_symbols   = true
    require_uppercase = true
  }
}

resource "aws_cognito_resource_server" "mcp" {
  identifier   = "mcp-api"
  name         = "Secure MCP API"
  user_pool_id = aws_cognito_user_pool.mcp.id

  scope {
    scope_name        = "read"
    scope_description = "允许读取当前 principal 的资料、订单和偏好"
  }
}

resource "aws_cognito_user_pool_domain" "mcp" {
  domain       = "secure-mcp"
  user_pool_id = aws_cognito_user_pool.mcp.id
}

resource "aws_cognito_user_pool_client" "principal_a" {
  name                                 = "${local.name_prefix}principal-a"
  user_pool_id                         = aws_cognito_user_pool.mcp.id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = ["mcp-api/read"]
  supported_identity_providers         = ["COGNITO"]

  depends_on = [aws_cognito_resource_server.mcp]
}

resource "aws_cognito_user_pool_client" "principal_b" {
  name                                 = "${local.name_prefix}principal-b"
  user_pool_id                         = aws_cognito_user_pool.mcp.id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = ["mcp-api/read"]
  supported_identity_providers         = ["COGNITO"]

  depends_on = [aws_cognito_resource_server.mcp]
}

resource "aws_cognito_user_pool_client" "no_scope" {
  name                                 = "${local.name_prefix}no-scope"
  user_pool_id                         = aws_cognito_user_pool.mcp.id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = []
  supported_identity_providers         = ["COGNITO"]
}
