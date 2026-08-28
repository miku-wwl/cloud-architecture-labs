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
}

variable "lambda_localstack_endpoint" {
  type    = string
  default = "http://host.docker.internal:4566"
}

variable "service_name" {
  type    = string
  default = "library-api"
}

variable "stable_version" {
  type    = string
  default = "stable-v1"
}

variable "candidate_version" {
  type    = string
  default = "candidate-v2"
}

variable "evaluation_window_seconds" {
  type    = number
  default = 10
}

variable "minimum_request_count" {
  type    = number
  default = 10
}

variable "max_error_rate" {
  type    = number
  default = 0.05
}

variable "max_latency_ms" {
  type    = number
  default = 300
}
