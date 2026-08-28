variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "aws_access_key" {
  type      = string
  default   = "test"
  sensitive = true
}

variable "aws_secret_key" {
  type      = string
  default   = "test"
  sensitive = true
}

variable "localstack_endpoint" {
  type    = string
  default = "http://localhost:4566"

  validation {
    condition     = can(regex("^https?://", var.localstack_endpoint))
    error_message = "localstack_endpoint 必须是 http 或 https URI。"
  }
}

variable "lambda_localstack_endpoint" {
  type    = string
  default = "http://localstack:4566"
}

variable "cognito_issuer" {
  type        = string
  default     = ""
  description = "留空时按 LocalStack user pool issuer 规则生成；以真实 token 的 iss 为准。"
}

variable "lambda_package_dir" {
  type    = string
  default = ""
}
