[CmdletBinding()]
param(
    [string]$LocalStackEndpoint = "http://localhost:4566",
    [string]$ModelId = "ollama.smollm2:360m",
    [string]$ModelParameterName = "",
    [string]$AwsAccessKeyId = "test",
    [string]$AwsSecretAccessKey = "test",
    [string]$AwsSessionToken = "",
    [int]$AppPort = 18080
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$labRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $labRoot ".tools"
$logsDir = Join-Path $labRoot "logs"
$collectorVersion = "0.122.0"
$agentVersion = "2.15.0"
$collectorPath = Join-Path $toolsDir "otelcol-contrib.exe"
$agentPath = Join-Path $toolsDir "opentelemetry-javaagent.jar"
$collectorLog = Join-Path $logsDir "otel-collector-localstack.out.log"
$collectorErrorLog = Join-Path $logsDir "otel-collector-localstack.err.log"
$appLog = Join-Path $logsDir "application-localstack.out.log"
$appErrorLog = Join-Path $logsDir "application-localstack.err.log"
$collectorProcess = $null
$appProcess = $null

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Wait-Http([string]$Name, [string]$Url, [int]$Attempts = 60) {
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 | Out-Null
            Write-Host "$Name READY: $Url"
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$Name did not become ready: $Url"
}

function Invoke-LocalAws([string[]]$AwsArguments) {
    $output = & aws --endpoint-url $LocalStackEndpoint --cli-connect-timeout 3 --cli-read-timeout 10 @AwsArguments --no-cli-pager 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "LocalStack AWS CLI call failed: aws $($AwsArguments -join ' ')`n$($output -join "`n")"
    }
    return ($output -join "`n")
}

function Stop-OwnedProcess($OwnedProcess) {
    if ($null -ne $OwnedProcess -and -not $OwnedProcess.HasExited) {
        Stop-Process -Id $OwnedProcess.Id -Force -ErrorAction SilentlyContinue
        $OwnedProcess.WaitForExit(5000) | Out-Null
    }
}

try {
    Assert-Command "java"
    Assert-Command "aws"
    Assert-Command "tar"

    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

    $health = Invoke-RestMethod -Uri "$LocalStackEndpoint/_localstack/health" -TimeoutSec 5
    if ($health.edition -ne "pro") {
        throw "LocalStack Pro/Ultimate runtime is required; actual edition=$($health.edition)"
    }
    if ($null -eq $health.services.'bedrock-runtime') {
        throw "LocalStack bedrock-runtime service is unavailable"
    }
    Write-Host "LOCALSTACK READY version=$($health.version) bedrock-runtime=$($health.services.'bedrock-runtime')"

    if (-not (Test-Path -LiteralPath $agentPath)) {
        $agentUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$agentVersion/opentelemetry-javaagent.jar"
        Write-Host "Downloading OpenTelemetry Java agent $agentVersion"
        Invoke-WebRequest -Uri $agentUrl -OutFile $agentPath
    }

    if (-not (Test-Path -LiteralPath $collectorPath)) {
        $archivePath = Join-Path $toolsDir "otelcol-contrib_$collectorVersion`_windows_amd64.tar.gz"
        $collectorUrl = "https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v$collectorVersion/otelcol-contrib_$collectorVersion`_windows_amd64.tar.gz"
        Write-Host "Downloading OpenTelemetry Collector Contrib $collectorVersion"
        Invoke-WebRequest -Uri $collectorUrl -OutFile $archivePath
        & tar -xzf $archivePath -C $toolsDir
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to extract OpenTelemetry Collector"
        }
        Remove-Item -LiteralPath $archivePath -Force
    }

    $env:AWS_ACCESS_KEY_ID = $AwsAccessKeyId
    $env:AWS_SECRET_ACCESS_KEY = $AwsSecretAccessKey
    if ([string]::IsNullOrWhiteSpace($AwsSessionToken)) {
        Remove-Item Env:AWS_SESSION_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:AWS_SESSION_TOKEN = $AwsSessionToken
    }
    $env:AWS_REGION = "us-east-1"
    $env:AWS_DEFAULT_REGION = "us-east-1"

    $collectorConfig = Join-Path $labRoot "observability/otel-collector-local.yaml"
    $collectorProcess = Start-Process -FilePath $collectorPath `
        -ArgumentList "--config=$collectorConfig" `
        -RedirectStandardOutput $collectorLog `
        -RedirectStandardError $collectorErrorLog `
        -WindowStyle Hidden `
        -PassThru

    Wait-Http "OTEL COLLECTOR" "http://localhost:13133" 30

    & (Join-Path $labRoot "mvnw.cmd") -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed"
    }

    $jarPath = Join-Path $labRoot "target/bedrock-resilience-observability-1.0.0.jar"
    $env:SPRING_PROFILES_ACTIVE = "local"
    $env:BEDROCK_ENDPOINT = $LocalStackEndpoint
    $env:BEDROCK_DUMMY_CREDENTIALS = "false"
    $env:BEDROCK_MODEL_PARAMETER_NAME = $ModelParameterName
    Remove-Item Env:BEDROCK_MODEL_ID -ErrorAction SilentlyContinue
    Remove-Item Env:BEDROCK_ALLOWED_MODEL_IDS -ErrorAction SilentlyContinue
    $env:OTEL_SERVICE_NAME = "bedrock-resilience-observability"
    $env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4318"
    $env:OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
    $env:OTEL_METRICS_EXPORTER = "none"
    $env:OTEL_LOGS_EXPORTER = "none"
    $env:OTEL_RESOURCE_ATTRIBUTES = "deployment.environment=localstack"

    $appProcess = Start-Process -FilePath "java" `
        -ArgumentList @("-javaagent:$agentPath", "-jar", $jarPath, "--server.port=$AppPort") `
        -RedirectStandardOutput $appLog `
        -RedirectStandardError $appErrorLog `
        -WindowStyle Hidden `
        -PassThru

    $appUrl = "http://localhost:$AppPort"
    Wait-Http "APP" "$appUrl/actuator/health" 60

    $requestBody = @{
        message = "Reply with exactly LOCALSTACK_APP_E2E_OK"
    } | ConvertTo-Json -Compress

    $response = Invoke-RestMethod -Method Post `
        -Uri "$appUrl/api/chat" `
        -ContentType "application/json" `
        -Body $requestBody `
        -TimeoutSec 180

    if ([string]::IsNullOrWhiteSpace($response.answer)) {
        throw "LOCALSTACK_BEDROCK FAIL: answer is empty"
    }
    if ($response.modelId -ne $ModelId -or $response.usage.totalTokens -le 0) {
        throw "LOCALSTACK_BEDROCK FAIL: response contract is incomplete"
    }
    $appLogText = Get-Content -LiteralPath $appLog -Raw
    if ([string]::IsNullOrWhiteSpace($ModelParameterName) -or
            $appLogText -notmatch "SSM_MODEL_CONFIG LOADED" -or
            $appLogText -notmatch [regex]::Escape($ModelParameterName)) {
        throw "LOCALSTACK_SSM_CONFIG FAIL: application did not load the Terraform model parameter"
    }
    Write-Host "LOCALSTACK_SSM_CONFIG PASS parameter=$ModelParameterName model=$($response.modelId) requestModelIdOmitted=true"
    Write-Host "LOCALSTACK_BEDROCK PASS model=$($response.modelId) tokens=$($response.usage.totalTokens) modelLatencyMs=$($response.modelLatencyMs) retryCount=$($response.retry.sdkRetryCount)"

    $prometheus = (Invoke-WebRequest -Uri "$appUrl/actuator/prometheus" -UseBasicParsing).Content
    if ($prometheus -notmatch "genai_bedrock_requests_total" -or $prometheus -notmatch [regex]::Escape($ModelId)) {
        throw "ACTUATOR_METRICS FAIL: Bedrock model metrics are absent"
    }
    if ($prometheus -match "LOCALSTACK_APP_E2E_OK") {
        throw "ACTUATOR_METRICS FAIL: prompt text leaked into metric labels"
    }
    Write-Host "ACTUATOR_METRICS PASS model dimension present, prompt absent"

    $cloudWatchFound = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $metricsJson = Invoke-LocalAws @("cloudwatch", "list-metrics", "--namespace", "GenAI/BedrockLab", "--output", "json")
        $metrics = $metricsJson | ConvertFrom-Json
        if ($metrics.Metrics.Count -gt 0) {
            $cloudWatchFound = $true
            Write-Host "LOCALSTACK_CLOUDWATCH PASS metrics=$($metrics.Metrics.Count)"
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $cloudWatchFound) {
        throw "LOCALSTACK_CLOUDWATCH FAIL: no GenAI/BedrockLab metrics found"
    }

    $appLogText = Get-Content -LiteralPath $appLog -Raw
    $traceMatches = [regex]::Matches($appLogText, "traceId=([0-9a-f]{32})")
    if ($traceMatches.Count -eq 0) {
        throw "LOCALSTACK_XRAY FAIL: application trace ID was not logged"
    }
    $w3cTraceId = $traceMatches[$traceMatches.Count - 1].Groups[1].Value
    $xrayTraceId = "1-$($w3cTraceId.Substring(0, 8))-$($w3cTraceId.Substring(8))"
    $traceFound = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $traceJson = Invoke-LocalAws @("xray", "batch-get-traces", "--trace-ids", $xrayTraceId, "--output", "json")
        if ($traceJson -match "bedrock.converse" -and $traceJson -match [regex]::Escape($ModelId)) {
            $traceFound = $true
            Write-Host "LOCALSTACK_XRAY PASS traceId=$xrayTraceId bedrock.converse=true modelId=true"
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $traceFound) {
        throw "LOCALSTACK_XRAY FAIL: model-specific bedrock.converse trace not found"
    }

    Write-Host "LOCALSTACK E2E PASS: Bedrock + Actuator + CloudWatch + X-Ray"
} catch {
    Write-Error $_ -ErrorAction Continue
    if (Test-Path -LiteralPath $collectorLog) {
        Write-Host "--- Collector tail ---"
        Get-Content -LiteralPath $collectorLog -Tail 80
    }
    if (Test-Path -LiteralPath $collectorErrorLog) {
        Get-Content -LiteralPath $collectorErrorLog -Tail 80
    }
    if (Test-Path -LiteralPath $appLog) {
        Write-Host "--- Application tail ---"
        Get-Content -LiteralPath $appLog -Tail 80
    }
    if (Test-Path -LiteralPath $appErrorLog) {
        Get-Content -LiteralPath $appErrorLog -Tail 80
    }
    throw
} finally {
    Stop-OwnedProcess $appProcess
    Stop-OwnedProcess $collectorProcess
}
