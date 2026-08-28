resource "aws_dynamodb_table" "routing" {
  name         = "canary-routing-state"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "serviceName"

  attribute {
    name = "serviceName"
    type = "S"
  }
}

resource "aws_dynamodb_table" "releases" {
  name         = "canary-releases"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "releaseId"

  attribute {
    name = "releaseId"
    type = "S"
  }
}
