[CmdletBinding()]
param(
    [string]$LocalStackEndpoint = "http://localhost:4566",
    [string]$LambdaEndpoint = "http://host.docker.internal:4566",
    [string]$BucketName = "event-driven-orders"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$labRoot = Split-Path -Parent $PSScriptRoot
$terraformDir = Join-Path $labRoot "terraform"
$statePath = Join-Path $terraformDir "terraform.tfstate"
$stateBackupPath = Join-Path $terraformDir "terraform.tfstate.backup"
$lockPath = Join-Path $terraformDir ".terraform.tfstate.lock.info"
$ruleName = "$BucketName-object-created"
$lambdaName = "$BucketName-processor"
$roleName = "$BucketName-lambda-role"
$policyName = "$roleName-s3"
$tempDir = Join-Path (Join-Path $labRoot ".tools") "e2e-$([Guid]::NewGuid().ToString('N'))"
$applied = $false

function Invoke-LocalAws([string[]]$Arguments) {
    $output = & aws --endpoint-url $LocalStackEndpoint --no-cli-pager @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "LocalStack AWS CLI failed: aws $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return ($output -join "`n")
}

function Invoke-Terraform([string[]]$Arguments) {
    & terraform "-chdir=$terraformDir" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Terraform failed: terraform $($Arguments -join ' ')"
    }
}

function Test-S3Object([string]$Key) {
    & aws --endpoint-url $LocalStackEndpoint --no-cli-pager s3api head-object `
        --bucket $BucketName --key $Key 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

function Wait-ForObject([string]$Key, [int]$Attempts = 30) {
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if (Test-S3Object $Key) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for s3://$BucketName/$Key"
}

function Get-TerraformOutput([string]$Name) {
    $value = & terraform "-chdir=$terraformDir" output -raw $Name 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Terraform output is unavailable: $Name`n$($value -join "`n")"
    }
    return ($value -join "`n").Trim()
}

function Assert-Equal([string]$Name, $Actual, $Expected) {
    if ($Actual -ne $Expected) {
        throw "$Name expected '$Expected' but was '$Actual'"
    }
}

function Assert-ResourceBoundary {
    $bucketCount = [int](Invoke-LocalAws @(
            "s3api", "list-buckets", "--query", "length(Buckets[?Name=='$BucketName'])", "--output", "text"))
    $ruleCount = [int](Invoke-LocalAws @(
            "events", "list-rules", "--name-prefix", $ruleName,
            "--query", "length(Rules[?Name=='$ruleName'])", "--output", "text"))
    $functionCount = [int](Invoke-LocalAws @(
            "lambda", "list-functions", "--query", "length(Functions[?FunctionName=='$lambdaName'])", "--output", "text"))
    $roleCount = [int](Invoke-LocalAws @(
            "iam", "list-roles", "--query", "length(Roles[?RoleName=='$roleName'])", "--output", "text"))
    if ($bucketCount + $ruleCount + $functionCount + $roleCount -ne 0) {
        throw "Pre-existing or residual lab resources: bucket=$bucketCount rule=$ruleCount function=$functionCount role=$roleCount"
    }
}

function Assert-EventBridgeConfiguration([string]$RuleArn, [string]$FunctionArn) {
    $notification = (Invoke-LocalAws @(
            "s3api", "get-bucket-notification-configuration", "--bucket", $BucketName)) | ConvertFrom-Json
    if ($null -eq $notification.EventBridgeConfiguration) {
        throw "S3 EventBridge notification is not enabled"
    }
    Write-Host "S3_EVENTBRIDGE_NOTIFICATION PASS eventbridge=true"

    $rule = (Invoke-LocalAws @("events", "describe-rule", "--name", $ruleName)) | ConvertFrom-Json
    Assert-Equal "EventBridge rule state" $rule.State "ENABLED"
    if ($rule.EventPattern -notmatch "aws.s3" -or $rule.EventPattern -notmatch "input/") {
        throw "EventBridge rule pattern does not contain aws.s3 and input/"
    }
    Assert-Equal "EventBridge rule ARN" $rule.Arn $RuleArn
    Write-Host "EVENTBRIDGE_RULE PASS name=$ruleName state=ENABLED source=aws.s3 prefix=input/"

    $targets = (Invoke-LocalAws @("events", "list-targets-by-rule", "--rule", $ruleName)) | ConvertFrom-Json
    if ($targets.Targets.Count -ne 1) {
        throw "Expected exactly one EventBridge target, found $($targets.Targets.Count)"
    }
    Assert-Equal "EventBridge target ARN" $targets.Targets[0].Arn $FunctionArn
    Write-Host "EVENTBRIDGE_TARGET PASS lambda=$lambdaName"

    $policyEnvelope = (Invoke-LocalAws @("lambda", "get-policy", "--function-name", $lambdaName)) | ConvertFrom-Json
    $policyJson = [Uri]::UnescapeDataString([string]$policyEnvelope.Policy)
    $statements = @((($policyJson | ConvertFrom-Json).Statement))
    $eventStatement = $statements | Where-Object {
        $sourceArn = $_.SourceArn
        if ([string]::IsNullOrWhiteSpace([string]$sourceArn)) {
            $sourceArn = $_.Condition.ArnLike.'AWS:SourceArn'
        }
        $_.Principal.Service -eq "events.amazonaws.com" -and $sourceArn -eq $RuleArn
    }
    if ($null -eq $eventStatement) {
        throw "EventBridge-specific Lambda permission is missing"
    }
    Write-Host "LAMBDA_PERMISSION PASS principal=events.amazonaws.com sourceArn=rule"
}

function Read-S3Json([string]$Key) {
    $path = Join-Path $tempDir (($Key -replace '/', '_') + '.json')
    Invoke-LocalAws @("s3", "cp", "s3://$BucketName/$Key", $path) | Out-Null
    return (Get-Content -Raw -LiteralPath $path | ConvertFrom-Json)
}

New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = "us-east-1"

try {
    Assert-ResourceBoundary

    $commonArguments = @(
        "-auto-approve",
        "-input=false",
        "-no-color",
        "-var=localstack_endpoint=$LocalStackEndpoint",
        "-var=lambda_endpoint=$LambdaEndpoint",
        "-var=bucket_name=$BucketName"
    )

    Invoke-Terraform @("init", "-reconfigure", "-input=false", "-no-color")
    Invoke-Terraform @("validate", "-no-color")
    Invoke-Terraform (@("apply") + $commonArguments)
    $applied = $true

    $bucket = Get-TerraformOutput "bucket_name"
    $function = Get-TerraformOutput "lambda_function_name"
    $rule = Get-TerraformOutput "eventbridge_rule_name"
    $ruleArn = Get-TerraformOutput "eventbridge_rule_arn"
    Assert-Equal "Terraform bucket output" $bucket $BucketName
    Assert-Equal "Terraform Lambda output" $function $lambdaName
    Assert-Equal "Terraform rule output" $rule $ruleName
    Write-Host "TERRAFORM_APPLY PASS resources=8 bucket=$bucket lambda=$function rule=$rule"

    $functionArn = (Invoke-LocalAws @(
            "lambda", "get-function", "--function-name", $lambdaName,
            "--query", "Configuration.FunctionArn", "--output", "text"))
    Assert-EventBridgeConfiguration $ruleArn $functionArn

    Invoke-LocalAws @("s3", "cp", (Join-Path $labRoot "fixtures/order-001.json"),
        "s3://$BucketName/input/order-001.json") | Out-Null
    Write-Host "INPUT_UPLOAD PASS key=input/order-001.json"
    Wait-ForObject "processed/order-001.result.json"
    $processed = Read-S3Json "processed/order-001.result.json"
    Assert-Equal "processed status" $processed.status "PROCESSED"
    Assert-Equal "processed orderId" $processed.orderId "ORD-001"
    Assert-Equal "processed sourceKey" $processed.sourceKey "input/order-001.json"
    Write-Host "E2E_POSITIVE PASS: S3 -> EventBridge -> Lambda -> S3"

    Invoke-LocalAws @("s3", "cp", (Join-Path $labRoot "fixtures/order-001.json"),
        "s3://$BucketName/ignored/order-ignored.json") | Out-Null
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        if (Test-S3Object "processed/order-ignored.result.json") {
            throw "EventBridge prefix filter failed: ignored/ produced a result"
        }
        Start-Sleep -Seconds 1
    }
    Write-Host "E2E_NEGATIVE PASS: ignored/ object did not trigger processing"

    Invoke-LocalAws @("s3", "cp", (Join-Path $labRoot "fixtures/malformed-order.json"),
        "s3://$BucketName/input/malformed-order.json") | Out-Null
    Wait-ForObject "processed/malformed-order.error.json"
    $invalid = Read-S3Json "processed/malformed-order.error.json"
    Assert-Equal "malformed status" $invalid.status "INVALID"
    Assert-Equal "malformed sourceKey" $invalid.sourceKey "input/malformed-order.json"
    Assert-Equal "malformed reason" $invalid.reason "missing orderId"
    Write-Host "E2E_MALFORMED PASS: invalid payload produced deterministic error result"

    $processedKeys = @((Invoke-LocalAws @(
            "s3api", "list-objects-v2", "--bucket", $BucketName, "--prefix", "processed/",
            "--query", "Contents[].Key", "--output", "json")) | ConvertFrom-Json)
    $expectedKeys = @("processed/order-001.result.json", "processed/malformed-order.error.json")
    $actualKeySet = (@($processedKeys | ForEach-Object { [string]$_ } | Sort-Object) -join ",")
    $expectedKeySet = (@($expectedKeys | Sort-Object) -join ",")
    if ($processedKeys.Count -ne 2 -or $actualKeySet -ne $expectedKeySet) {
        throw "Unexpected processed keys: $($processedKeys -join ',')"
    }
    Write-Host "NO_RECURSION PASS processedCount=2 ignoredResult=false"
} finally {
    if ($applied -or (Test-Path -LiteralPath $statePath)) {
        try {
            Invoke-Terraform (@("apply", "-destroy") + $commonArguments)
            Assert-ResourceBoundary
            Write-Host "TERRAFORM_DESTROY PASS resources=0"
        } finally {
            foreach ($path in @($statePath, $stateBackupPath, $lockPath)) {
                if (Test-Path -LiteralPath $path) {
                    Remove-Item -LiteralPath $path -Force
                }
            }
        }
    }
    if (Test-Path -LiteralPath $tempDir) {
        Remove-Item -LiteralPath $tempDir -Recurse -Force
    }
}

Write-Host "FINAL_ACCEPTANCE PASS: S3 -> EventBridge -> Lambda E2E"
