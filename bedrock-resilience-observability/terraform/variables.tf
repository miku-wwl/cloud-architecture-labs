variable "aws_region" {
  description = "LocalStack emulated AWS region."
  type        = string
  default     = "us-east-1"
}

variable "localstack_endpoint" {
  description = "Existing LocalStack Ultimate endpoint."
  type        = string
  default     = "http://localhost:4566"

  validation {
    condition     = can(regex("^https?://[^/]+:[0-9]+$", var.localstack_endpoint))
    error_message = "localstack_endpoint must be an HTTP(S) endpoint with an explicit port."
  }
}

variable "environment_name" {
  description = "Short environment name used in resource names and telemetry."
  type        = string
  default     = "lab"

  validation {
    condition     = can(regex("^[a-z0-9-]{2,16}$", var.environment_name))
    error_message = "environment_name must contain 2-16 lowercase letters, numbers or hyphens."
  }
}

variable "bedrock_model_id" {
  description = "Model ID stored in SSM and used by the LocalStack Bedrock application."
  type        = string
  default     = "ollama.smollm2:360m"
}

variable "throttling_alarm_threshold" {
  type    = number
  default = 1
}

variable "high_latency_seconds_threshold" {
  type    = number
  default = 5
}
