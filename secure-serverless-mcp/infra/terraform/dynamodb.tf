resource "aws_dynamodb_table" "user_data" {
  name         = local.user_data_table
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "principalId"

  attribute {
    name = "principalId"
    type = "S"
  }
}
