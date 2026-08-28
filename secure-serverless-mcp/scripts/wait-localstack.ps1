$ErrorActionPreference = 'Stop'
$endpoint = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { 'http://localhost:4566' }
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        $health = Invoke-RestMethod -UseBasicParsing -Uri "$endpoint/_localstack/health"
        if ($health.services.dynamodb -eq 'running' -and $health.services.'cognito-idp' -in @('available', 'running') -and
            $health.services.apigatewayv2 -in @('available', 'running') -and $health.services.lambda -eq 'running') {
            Write-Output "LocalStack ready: $endpoint (edition=$($health.edition), version=$($health.version))"
            exit 0
        }
    } catch {
        # External LocalStack may still be starting.
    }
    Start-Sleep -Seconds 2
}
throw "LocalStack was not ready at $endpoint within 60 seconds"
