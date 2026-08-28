$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$tfRoot = Join-Path $labRoot 'infra\terraform'
$tfText = (Get-ChildItem -LiteralPath $tfRoot -Filter '*.tf' -File | Get-Content -Raw) -join "`n"
if ($tfText -match '(?i)https?://[^\s]*amazonaws\.com') { throw 'Terraform contains a real AWS HTTPS endpoint' }
if ($tfText -match '(?i)function_url_config|aws_lambda_function_url') { throw 'Lambda Function URL is forbidden' }
if ($tfText -match '(?i)profile\s*=|AWS_PROFILE') { throw 'AWS profile dependency is forbidden' }
if ($tfText -notmatch 'access_key\s*=\s*var\.aws_access_key' -or $tfText -notmatch 'secret_key\s*=\s*var\.aws_secret_key') {
    throw 'Terraform provider is not bound to local fake credentials'
}
if ($tfText -notmatch 'skip_credentials_validation\s*=\s*true' -or
    $tfText -notmatch 'skip_metadata_api_check\s*=\s*true' -or
    $tfText -notmatch 'skip_requesting_account_id\s*=\s*true') {
    throw 'Terraform provider is missing LocalStack safety flags'
}
if ($tfText -notmatch 'default\s*=\s*"http://localhost:4566"' -or
    $tfText -notmatch 'default\s*=\s*"http://localstack:4566"') {
    throw 'default endpoints are not LocalStack endpoints'
}
Write-Output 'LOCAL-ONLY PASS: Terraform points only to LocalStack; no real AWS endpoint, profile, or Function URL found.'
