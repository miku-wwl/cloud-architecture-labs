$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$tfRoot = Join-Path $labRoot 'infra\terraform'
$endpoint = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { 'http://localhost:4566' }
$env:AWS_ACCESS_KEY_ID = 'test'; $env:AWS_SECRET_ACCESS_KEY = 'test'; $env:AWS_DEFAULT_REGION = 'us-east-1'; $env:AWS_REGION = 'us-east-1'; $env:AWS_EC2_METADATA_DISABLED = 'true'
function Invoke-Aws([string[]]$arguments) { & aws @arguments '--endpoint-url' $endpoint '--output' 'json' }
function Get-TfOutput([string]$name) { (& terraform "-chdir=$tfRoot" output -raw $name).Trim() }

Invoke-RestMethod -UseBasicParsing -Uri "$endpoint/_localstack/health" | Out-Null
$tables = (Invoke-Aws @('dynamodb','list-tables') | ConvertFrom-Json).TableNames
if ($tables -notcontains 'mcp-user-data') { throw 'mcp-user-data is missing' }
$functions = (Invoke-Aws @('lambda','list-functions') | ConvertFrom-Json).Functions.FunctionName
if ($functions -notcontains 'secure-mcp-server') { throw 'Java Lambda is missing' }
$apiId = Get-TfOutput 'api_id'
$apis = (Invoke-Aws @('apigatewayv2','get-apis') | ConvertFrom-Json).Items
if (-not ($apis | Where-Object { $_.ApiId -eq $apiId -and $_.ProtocolType -eq 'HTTP' })) { throw 'HTTP API is missing' }
$routes = (Invoke-Aws @('apigatewayv2','get-routes','--api-id',$apiId) | ConvertFrom-Json).Items
$route = $routes | Where-Object { $_.RouteKey -eq 'POST /mcp' }
if (-not $route -or $route.AuthorizationType -ne 'JWT') { throw 'POST /mcp has no JWT authorizer' }
$authorizers = (Invoke-Aws @('apigatewayv2','get-authorizers','--api-id',$apiId) | ConvertFrom-Json).Items
if (-not ($authorizers | Where-Object { $_.AuthorizerType -eq 'JWT' -and $_.IdentitySource -contains '$request.header.Authorization' })) { throw 'JWT authorizer identity source is missing' }
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $null = & aws lambda get-function-url-config --function-name 'secure-mcp-server' --endpoint-url $endpoint --output json 2>&1
    $functionUrlExitCode = $LASTEXITCODE
} catch {
    $functionUrlExitCode = 1
} finally {
    $ErrorActionPreference = $previousErrorAction
}
if ($functionUrlExitCode -eq 0) { throw 'an unexpected Lambda Function URL exists' }
$scan = Invoke-Aws @('dynamodb','scan','--table-name','mcp-user-data') | ConvertFrom-Json
if ($scan.Count -lt 2) { throw 'DynamoDB seed data contains fewer than two principals' }
Write-Output 'SMOKE PASS: Cognito/API Gateway v2/JWT/Lambda/DynamoDB resources are readable from LocalStack.'
