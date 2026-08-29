resource "aws_s3_bucket" "orders" {
  bucket        = var.bucket_name
  force_destroy = true
}

resource "aws_s3_bucket_notification" "orders" {
  bucket      = aws_s3_bucket.orders.id
  eventbridge = true
}
