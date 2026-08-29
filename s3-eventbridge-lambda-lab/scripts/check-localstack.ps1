[CmdletBinding()]
param(
    [string]$LocalStackEndpoint = "http://localhost:4566"
)

$ErrorActionPreference = "Stop"
$health = Invoke-RestMethod -Uri "$LocalStackEndpoint/_localstack/health" -TimeoutSec 5
if ($health.edition -ne "pro") {
    throw "LocalStack Ultimate/Pro is required; actual edition=$($health.edition)"
}
foreach ($service in @("s3", "events", "iam", "lambda", "sts")) {
    if ($health.services.$service -notin @("available", "running")) {
        throw "LocalStack service is not ready: $service"
    }
}
Write-Host "LOCALSTACK READY version=$($health.version) edition=$($health.edition) endpoint=$LocalStackEndpoint"
