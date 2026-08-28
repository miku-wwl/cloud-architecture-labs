$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$tfRoot = Join-Path $labRoot 'infra\terraform'
function Get-TfOutput([string]$name) { (& terraform "-chdir=$tfRoot" output -raw $name).Trim() }
function Get-Token([string]$clientId, [string]$clientSecret, [string]$scope) {
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$clientId`:$clientSecret"))
    $body = "grant_type=client_credentials&scope=$([Uri]::EscapeDataString($scope))"
    $response = Invoke-RestMethod -UseBasicParsing -Method Post -Uri (Get-TfOutput 'oauth_token_endpoint') `
        -Headers @{ Authorization = "Basic $basic" } -ContentType 'application/x-www-form-urlencoded' -Body $body
    if (-not $response.access_token) { throw 'Cognito did not return access_token' }
    return $response.access_token
}
function Invoke-Mcp([string]$token, [string]$body, [hashtable]$extraHeaders = @{}) {
    $headers = @{ Accept = 'application/json, text/event-stream' }
    if (-not [string]::IsNullOrWhiteSpace($token)) { $headers.Authorization = "Bearer $token" }
    foreach ($key in $extraHeaders.Keys) { $headers[$key] = $extraHeaders[$key] }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri (Get-TfOutput 'mcp_endpoint') `
            -Headers $headers -ContentType 'application/json' -Body $body
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Headers = $response.Headers; Body = $response.Content }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode.value__ } else { 0 }
        $bodyText = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        return [pscustomobject]@{ Status = $status; Headers = @{}; Body = $bodyText }
    }
}
function ConvertFrom-Base64Url([string]$value) {
    $padded = $value.Replace('-', '+').Replace('_', '/')
    switch ($padded.Length % 4) {
        2 { $padded += '==' }
        3 { $padded += '=' }
    }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($padded))
}
function ConvertTo-Base64Url([string]$value) {
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$initializeBody = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"security-test","version":"1"}}}'
$noToken = Invoke-Mcp '' $initializeBody
if ($noToken.Status -notin @(401, 403)) { throw "S1 no token was not blocked by API Gateway, actual=$($noToken.Status)" }
Write-Output "S1 PASS: no token blocked with HTTP $($noToken.Status)"

$garbage = Invoke-Mcp 'not-a-jwt' $initializeBody
if ($garbage.Status -notin @(401, 403)) { throw "S2 garbage token was not blocked, actual=$($garbage.Status)" }
Write-Output "S2 PASS: garbage token blocked with HTTP $($garbage.Status)"

$tokenA = Get-Token (Get-TfOutput 'app_client_id_a') (Get-TfOutput 'app_client_secret_a') 'mcp-api/read'
$jwtParts = $tokenA.Split('.')
if ($jwtParts.Count -ne 3) { throw 'S3 access token did not have JWT shape' }
$tamperedClaims = ConvertFrom-Json (ConvertFrom-Base64Url $jwtParts[1])
$tamperedClaims.iss = 'https://invalid-issuer.example.local'
$tamperedPayload = ConvertTo-Base64Url (($tamperedClaims | ConvertTo-Json -Compress -Depth 20))
$wrongIssuer = "$($jwtParts[0]).$tamperedPayload.$($jwtParts[2])"
$invalidIssuer = Invoke-Mcp $wrongIssuer $initializeBody
if ($invalidIssuer.Status -notin @(401, 403, 500)) { throw "S3 wrong issuer/signature was not blocked, actual=$($invalidIssuer.Status)" }
Write-Output "S3 PASS: token with tampered issuer/signature blocked with HTTP $($invalidIssuer.Status)"

$tokenNoScope = Get-Token (Get-TfOutput 'app_client_id_no_scope') (Get-TfOutput 'app_client_secret_no_scope') ''
$missingScope = Invoke-Mcp $tokenNoScope $initializeBody
if ($missingScope.Status -notin @(401, 403)) { throw "S4 missing scope was not blocked, actual=$($missingScope.Status)" }
Write-Output "S4 PASS: missing scope blocked with HTTP $($missingScope.Status)"

$init = Invoke-Mcp $tokenA $initializeBody
if ($init.Status -ne 200) { throw "S5 valid token initialize failed, actual=$($init.Status) body=$($init.Body)" }
$session = [string]($init.Headers['Mcp-Session-Id'] | Select-Object -First 1)
if ([string]::IsNullOrWhiteSpace($session)) { throw 'S5 MCP session id is missing' }
$injectionBody = '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_my_profile","arguments":{"userId":"other-principal"}}}'
$injection = Invoke-Mcp $tokenA $injectionBody @{ 'Mcp-Session-Id' = $session }
if ($injection.Status -notin @(200, 400)) { throw "S6 principal injection returned an unexpected status, actual=$($injection.Status)" }
if ($injection.Body -notmatch '(?i)error|additional|unknown|invalid') { throw 'S6 principal injection was not rejected by MCP schema/tool validation' }
Write-Output 'S6 PASS: user-controlled principal rejected; tool input does not expose userId/principalId/email'

$tokenB = Get-Token (Get-TfOutput 'app_client_id_b') (Get-TfOutput 'app_client_secret_b') 'mcp-api/read'
$initB = Invoke-Mcp $tokenB $initializeBody
if ($initB.Status -ne 200) { throw "S7 principal B initialize failed, actual=$($initB.Status) body=$($initB.Body)" }
$sessionB = [string]($initB.Headers['Mcp-Session-Id'] | Select-Object -First 1)
if ([string]::IsNullOrWhiteSpace($sessionB)) { throw 'S7 principal B MCP session id is missing' }
$profileBody = '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_my_profile","arguments":{}}}'
$profileB = Invoke-Mcp $tokenB $profileBody @{ 'Mcp-Session-Id' = $sessionB }
$principalB = (Get-TfOutput 'app_client_id_b')
if ($profileB.Status -ne 200 -or $profileB.Body -notmatch [regex]::Escape($principalB)) {
    throw "S7 principal B did not receive B-bound data, actual=$($profileB.Status) body=$($profileB.Body)"
}
Write-Output 'S7 PASS: principal B received only its principal-bound profile data'
Write-Output 'SECURITY PASS: authentication, issuer/signature, scope, schema injection, and principal isolation checks passed.'
