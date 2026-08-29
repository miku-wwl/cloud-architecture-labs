locals {
  lambda_name = "${var.bucket_name}-processor"
  rule_name   = "${var.bucket_name}-object-created"
  role_name   = "${var.bucket_name}-lambda-role"
}
