$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$tfRoot = Join-Path $labRoot 'infra\terraform'
function Get-TfOutput([string]$name) { (& terraform "-chdir=$tfRoot" output -raw $name).Trim() }
$env:MCP_ENDPOINT = Get-TfOutput 'mcp_endpoint'
$env:MCP_TOKEN_ENDPOINT = Get-TfOutput 'oauth_token_endpoint'
$env:MCP_CLIENT_ID = Get-TfOutput 'app_client_id_a'
$env:MCP_CLIENT_SECRET = Get-TfOutput 'app_client_secret_a'
$env:MCP_SCOPE = 'mcp-api/read'
Push-Location $labRoot
try {
    & mvn -q -pl mcp-client-cli exec:java '-Dexec.mainClass=com.example.securemcp.client.McpClientCli' '-Dexec.args=full-demo' '-Dexec.classpathScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'official Java MCP Client full-demo failed' }
} finally { Pop-Location }

function Get-Token([string]$clientId, [string]$clientSecret, [string]$scope) {
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$clientId`:$clientSecret"))
    $body = "grant_type=client_credentials&scope=$([Uri]::EscapeDataString($scope))"
    $response = Invoke-RestMethod -UseBasicParsing -Method Post -Uri $env:MCP_TOKEN_ENDPOINT `
        -Headers @{ Authorization = "Basic $basic" } -ContentType 'application/x-www-form-urlencoded' -Body $body
    return $response.access_token
}
function Invoke-Mcp([string]$token, [string]$body, [hashtable]$extraHeaders = @{}) {
    $headers = @{ Authorization = "Bearer $token"; Accept = 'application/json, text/event-stream' }
    foreach ($key in $extraHeaders.Keys) { $headers[$key] = $extraHeaders[$key] }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $env:MCP_ENDPOINT `
            -Headers $headers -ContentType 'application/json' -Body $body
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Headers = $response.Headers; Body = $response.Content }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode.value__ } else { 0 }
        $bodyText = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        return [pscustomobject]@{ Status = $status; Headers = @{}; Body = $bodyText }
    }
}
$negativeToken = Get-Token $env:MCP_CLIENT_ID $env:MCP_CLIENT_SECRET $env:MCP_SCOPE
$negativeInitialize = Invoke-Mcp $negativeToken '{"jsonrpc":"2.0","id":10,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"mcp-negative-test","version":"1"}}}'
if ($negativeInitialize.Status -ne 200) { throw "negative MCP initialize failed, actual=$($negativeInitialize.Status)" }
$negativeSession = [string]($negativeInitialize.Headers['Mcp-Session-Id'] | Select-Object -First 1)
if ([string]::IsNullOrWhiteSpace($negativeSession)) { throw 'negative MCP initialize did not return a session id' }
$limit1000 = Invoke-Mcp $negativeToken '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"list_my_orders","arguments":{"limit":1000}}}' @{ 'Mcp-Session-Id' = $negativeSession }
if ($limit1000.Body -notmatch '(?i)error|invalid|maximum|range') { throw 'limit=1000 was not rejected' }
Write-Output 'Negative PASS: list_my_orders limit=1000 rejected'
$unknownTool = Invoke-Mcp $negativeToken '{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"delete_everything","arguments":{}}}' @{ 'Mcp-Session-Id' = $negativeSession }
if ($unknownTool.Body -notmatch '(?i)error|unknown|not found|tool') { throw 'unknown tool was not rejected' }
Write-Output 'Negative PASS: unknown tool rejected'
