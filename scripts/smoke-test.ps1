$ErrorActionPreference = 'Stop'
$endpoint = 'http://localhost:4566'
$env:AWS_ACCESS_KEY_ID = 'test'; $env:AWS_SECRET_ACCESS_KEY = 'test'; $env:AWS_DEFAULT_REGION = 'us-east-1'
function Invoke-AwsCli([string[]]$Arguments) { & aws @Arguments --endpoint-url $endpoint }

Invoke-RestMethod -Uri "$endpoint/_localstack/health" | Out-Null
$tables = (Invoke-AwsCli @('dynamodb','list-tables') | ConvertFrom-Json).TableNames
foreach ($required in @('canary-routing-state','canary-releases','canary-metrics-window')) { if ($tables -notcontains $required) { throw "Missing DynamoDB table $required" } }
$buses = (Invoke-AwsCli @('events','list-event-buses') | ConvertFrom-Json).EventBuses.Name
if ($buses -notcontains 'canary-release-bus') { throw 'Missing EventBridge bus' }
$rules = (Invoke-AwsCli @('events','list-rules','--event-bus-name','canary-release-bus') | ConvertFrom-Json).Rules.Name
if ($rules -notcontains 'canary-release-requested') { throw 'Missing EventBridge rule' }
$targets = (Invoke-AwsCli @('events','list-targets-by-rule','--event-bus-name','canary-release-bus','--rule','canary-release-requested') | ConvertFrom-Json).Targets
if (-not ($targets | Where-Object { $_.Arn -like '*stateMachine*CanaryReleaseWorkflow*' })) { throw 'Rule target is not Step Functions' }
$machines = (Invoke-AwsCli @('stepfunctions','list-state-machines') | ConvertFrom-Json).stateMachines.name
if ($machines -notcontains 'CanaryReleaseWorkflow') { throw 'Missing state machine' }
$functions = (Invoke-AwsCli @('lambda','list-functions') | ConvertFrom-Json).Functions.FunctionName
foreach ($required in @('canary-initialize_release','canary-set_weight','canary-evaluate_health','canary-finalize_release')) { if ($functions -notcontains $required) { throw "Missing Lambda $required" } }
if ((Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8081/actuator/health').StatusCode -ne 200) { throw 'Stable service unhealthy' }
if ((Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8082/actuator/health').StatusCode -ne 200) { throw 'Candidate service unhealthy' }
if ((Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/actuator/health').StatusCode -ne 200) { throw 'Control plane unhealthy' }
$now = [DateTime]::UtcNow
$start = $now.AddMinutes(-2).ToString('o'); $end = $now.AddMinutes(2).ToString('o')
Invoke-AwsCli @('cloudwatch','put-metric-data','--namespace','CanaryDemo/PaymentAPI','--metric-name','RequestCount','--value','1','--dimensions','Version=smoke-v1') | Out-Null
Start-Sleep -Seconds 1
$stats = Invoke-AwsCli @('cloudwatch','get-metric-statistics','--namespace','CanaryDemo/PaymentAPI','--metric-name','RequestCount','--dimensions','Name=Version,Value=smoke-v1','--statistics','Sum','--period','60','--start-time',$start,'--end-time',$end) | ConvertFrom-Json
if (-not $stats.Datapoints) { throw 'CloudWatch GetMetricStatistics returned no datapoint after PutMetricData' }
Write-Output 'SMOKE PASS: LocalStack, DynamoDB, EventBridge, Step Functions, Lambda, services, PutMetricData and GetMetricStatistics.'
