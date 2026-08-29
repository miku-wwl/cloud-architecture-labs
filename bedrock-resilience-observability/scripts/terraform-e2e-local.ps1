[CmdletBinding()]
param(
    [string]$LocalStackEndpoint = "http://localhost:4566",
    [string]$EnvironmentName = "tf-e2e",
    [string]$ModelId = "ollama.smollm2:360m",
    [int]$AppPort = 18080
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$labRoot = Split-Path -Parent $PSScriptRoot
$terraformDir = Join-Path $labRoot "terraform"
$toolsDir = Join-Path $labRoot ".tools"
$statePath = Join-Path $toolsDir "terraform-localstack-e2e.tfstate"
$stateBackupPath = "$statePath.backup"
$resourcePrefix = "$EnvironmentName-bedrock-resilience"
$roleName = "$resourcePrefix-app"
$policyName = "$resourcePrefix-app"
$parameterName = "/$resourcePrefix/application/bedrock-model-id"

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Invoke-Terraform([string[]]$Arguments) {
    & terraform "-chdir=$terraformDir" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Terraform failed: terraform $($Arguments -join ' ')"
    }
}

function Invoke-LocalAws([string[]]$Arguments) {
    $output = & aws --endpoint-url $LocalStackEndpoint --no-cli-pager @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "LocalStack AWS CLI failed: aws $($Arguments -join ' ')`n$output"
    }
    return $output
}

function Get-ResourceCounts {
    $dashboardCount = [int](Invoke-LocalAws @(
            "cloudwatch", "list-dashboards", "--dashboard-name-prefix", $resourcePrefix,
            "--query", "length(DashboardEntries)", "--output", "text"))
    $alarmCount = [int](Invoke-LocalAws @(
            "cloudwatch", "describe-alarms", "--alarm-name-prefix", $resourcePrefix,
            "--query", "length(MetricAlarms)", "--output", "text"))
    $roleCount = [int](Invoke-LocalAws @(
            "iam", "list-roles", "--query", "length(Roles[?RoleName=='$roleName'])", "--output", "text"))
    $rolePolicyCount = if ($roleCount -eq 1) {
        [int](Invoke-LocalAws @(
                "iam", "list-role-policies", "--role-name", $roleName,
                "--query", "length(PolicyNames[?@=='$policyName'])", "--output", "text"))
    } else { 0 }
    $parameterCount = [int](Invoke-LocalAws @(
            "ssm", "describe-parameters", "--query", "length(Parameters[?Name=='$parameterName'])",
            "--output", "text"))
    return [pscustomobject]@{
        Dashboards   = $dashboardCount
        Alarms       = $alarmCount
        Roles        = $roleCount
        RolePolicies = $rolePolicyCount
        Parameters   = $parameterCount
    }
}

function Format-ResourceCounts($Counts) {
    return "dashboards=$($Counts.Dashboards) alarms=$($Counts.Alarms) roles=$($Counts.Roles) " +
        "rolePolicies=$($Counts.RolePolicies) parameters=$($Counts.Parameters)"
}

Assert-Command "terraform"
Assert-Command "aws"
New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null

$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = "us-east-1"

$health = Invoke-RestMethod "$LocalStackEndpoint/_localstack/health"
foreach ($service in @("cloudwatch", "iam", "ssm", "sts", "xray")) {
    if ($health.services.$service -notin @("available", "running")) {
        throw "LocalStack service is not ready: $service"
    }
}

if (Test-Path -LiteralPath $statePath) {
    throw "Stale Terraform E2E state exists: $statePath"
}

$before = Get-ResourceCounts
if ($before.Dashboards + $before.Alarms + $before.Roles + $before.RolePolicies +
        $before.Parameters -ne 0) {
    throw "Existing $resourcePrefix resources make the E2E result ambiguous"
}

$commonArguments = @(
    "-auto-approve",
    "-input=false",
    "-no-color",
    "-var=localstack_endpoint=$LocalStackEndpoint",
    "-var=environment_name=$EnvironmentName",
    "-var=bedrock_model_id=$ModelId"
)

try {
    Invoke-Terraform @("init", "-reconfigure", "-input=false", "-no-color")
    Invoke-Terraform @("validate", "-no-color")
    Invoke-Terraform (@("apply") + $commonArguments)

    $deployed = Get-ResourceCounts
    if ($deployed.Dashboards -ne 1 -or $deployed.Alarms -ne 2 -or $deployed.Roles -ne 1 -or
            $deployed.RolePolicies -ne 1 -or $deployed.Parameters -ne 1) {
        throw "TERRAFORM_APPLY FAIL: $(Format-ResourceCounts $deployed)"
    }
    $storedModelId = Invoke-LocalAws @(
        "ssm", "get-parameter", "--name", $parameterName, "--query", "Parameter.Value", "--output", "text")
    if ($storedModelId -ne $ModelId) {
        throw "TERRAFORM_SSM FAIL: expected=$ModelId actual=$storedModelId"
    }
    Write-Host "TERRAFORM_APPLY PASS resources=6 $(Format-ResourceCounts $deployed) state=$statePath"

    $roleArn = Invoke-LocalAws @(
        "iam", "get-role", "--role-name", $roleName, "--query", "Role.Arn", "--output", "text")
    $session = (Invoke-LocalAws @(
        "sts", "assume-role", "--role-arn", $roleArn, "--role-session-name", "bedrock-e2e",
        "--duration-seconds", "900", "--output", "json")) | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($session.Credentials.SessionToken)) {
        throw "LOCALSTACK_STS FAIL: AssumeRole did not return temporary credentials"
    }
    Write-Host "LOCALSTACK_STS PASS role=$roleName temporaryCredentials=true"

    & (Join-Path $PSScriptRoot "e2e-local.ps1") `
        -LocalStackEndpoint $LocalStackEndpoint `
        -ModelId $ModelId `
        -ModelParameterName $parameterName `
        -AwsAccessKeyId $session.Credentials.AccessKeyId `
        -AwsSecretAccessKey $session.Credentials.SecretAccessKey `
        -AwsSessionToken $session.Credentials.SessionToken `
        -AppPort $AppPort
} finally {
    if (Test-Path -LiteralPath $statePath) {
        $env:AWS_ACCESS_KEY_ID = "test"
        $env:AWS_SECRET_ACCESS_KEY = "test"
        Remove-Item Env:AWS_SESSION_TOKEN -ErrorAction SilentlyContinue
        Invoke-Terraform (@("apply", "-destroy") + $commonArguments)
        $after = Get-ResourceCounts
        if ($after.Dashboards + $after.Alarms + $after.Roles + $after.RolePolicies +
                $after.Parameters -ne 0) {
            throw "TERRAFORM_DESTROY FAIL: $(Format-ResourceCounts $after)"
        }
        [System.IO.File]::Delete($statePath)
        if (Test-Path -LiteralPath $stateBackupPath) {
            [System.IO.File]::Delete($stateBackupPath)
        }
        Write-Host "TERRAFORM_DESTROY PASS resources=0 $(Format-ResourceCounts $after) stateRemoved=true"
    }
}

Write-Host "TERRAFORM_LOCALSTACK_E2E PASS: apply -> application E2E -> destroy"
