$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$tfRoot = Join-Path $labRoot 'infra\terraform'
$endpoint = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { 'http://localhost:4566' }
$env:AWS_ACCESS_KEY_ID = 'test'
$env:AWS_SECRET_ACCESS_KEY = 'test'
$env:AWS_DEFAULT_REGION = 'us-east-1'
$env:AWS_REGION = 'us-east-1'
$env:AWS_EC2_METADATA_DISABLED = 'true'

function Get-TfOutput([string]$name) {
    $value = & terraform "-chdir=$tfRoot" output -raw $name
    if ($LASTEXITCODE -ne 0) { throw "Terraform output failed: $name" }
    return $value.Trim()
}

function Put-PrincipalData([string]$principalId, [string]$displayName, [string]$email, [string]$language, [string]$orderId, [string]$amount) {
    $item = @{
        principalId = @{ S = $principalId }
        displayName = @{ S = $displayName }
        email       = @{ S = $email }
        preferences = @{ M = @{
            currency = @{ S = 'NZD' }
            language = @{ S = $language }
        }}
        orders = @{ L = @(
            @{ M = @{
                orderId = @{ S = $orderId }
                amount  = @{ N = $amount }
                status  = @{ S = 'PAID' }
            }}
        )}
    } | ConvertTo-Json -Depth 10 -Compress
    $itemFile = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($itemFile, $item, [Text.UTF8Encoding]::new($false))
        & aws dynamodb put-item --endpoint-url $endpoint --table-name 'mcp-user-data' --item "file://$itemFile" --output json | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "principal data write failed: $principalId" }
    } finally {
        Remove-Item -LiteralPath $itemFile -Force -ErrorAction SilentlyContinue
    }
}

$principalA = Get-TfOutput 'app_client_id_a'
$principalB = Get-TfOutput 'app_client_id_b'
Put-PrincipalData $principalA 'Alice Service Principal' 'alice@example.local' 'en' 'order-a-1001' '49.90'
Put-PrincipalData $principalB 'Bob Service Principal' 'bob@example.local' 'zh' 'order-b-2001' '19.90'
Write-Output 'Seed PASS: principal A/B data written to mcp-user-data without real PII or tokens.'
