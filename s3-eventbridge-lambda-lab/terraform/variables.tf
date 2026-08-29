variable "aws_region" {
  description = "LocalStack emulated AWS region."
  type        = string
  default     = "us-east-1"
}

variable "localstack_endpoint" {
  description = "Existing LocalStack endpoint used by Terraform and the verification CLI."
  type        = string
  default     = "http://localhost:4566"

  validation {
    condition     = can(regex("^https?://[^/]+:[0-9]+$", var.localstack_endpoint))
    error_message = "localstack_endpoint must be an HTTP(S) endpoint with an explicit port."
  }
}

variable "lambda_endpoint" {
  description = "Endpoint reachable from the LocalStack Lambda runtime network."
  type        = string
  default     = "http://host.docker.internal:4566"

  validation {
    condition     = can(regex("^https?://[^/]+:[0-9]+$", var.lambda_endpoint))
    error_message = "lambda_endpoint must be an HTTP(S) endpoint with an explicit port."
  }
}

variable "bucket_name" {
  description = "Single S3 bucket shared by input/ and processed/."
  type        = string
  default     = "event-driven-orders"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{2,62}$", var.bucket_name))
    error_message = "bucket_name must be a valid S3 bucket name."
  }
}

variable "lambda_package_path" {
  description = "Path to the shaded Java Lambda JAR, relative to the Terraform module."
  type        = string
  default     = "../lambda/target/order-processor.jar"
}
