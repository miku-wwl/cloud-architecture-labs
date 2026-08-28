$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repo '.run'
$logDir = Join-Path $repo 'logs'
New-Item -ItemType Directory -Force -Path $runDir,$logDir | Out-Null

function Start-App([string]$Name, [string]$Jar, [string[]]$Arguments) {
    $pidPath = Join-Path $runDir "$Name.pid"
    if (Test-Path $pidPath) {
        $oldPid = [int](Get-Content -Raw $pidPath)
        if (Get-Process -Id $oldPid -ErrorAction SilentlyContinue) { return }
        Remove-Item -LiteralPath $pidPath -Force
    }
    $stdoutPath = Join-Path $logDir "$Name.out.log"
    $stderrPath = Join-Path $logDir "$Name.err.log"
    $process = Start-Process -FilePath 'java' -WorkingDirectory $repo -WindowStyle Hidden -PassThru -ArgumentList (@('-jar', $Jar) + $Arguments) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    Set-Content -LiteralPath $pidPath -Value $process.Id
    Write-Output "$Name started with PID $($process.Id)"
}

Start-App 'library-stable' 'apps/library-service/target/library-service-1.0.0-SNAPSHOT.jar' @('--server.port=8081','--app.version=stable-v1','--app.fault-mode=HEALTHY')
Start-App 'library-candidate' 'apps/library-service/target/library-service-1.0.0-SNAPSHOT.jar' @('--server.port=8082','--app.version=candidate-v2','--app.fault-mode=HEALTHY')
Start-App 'canary-control-plane' 'apps/canary-control-plane/target/canary-control-plane-1.0.0-SNAPSHOT.jar' @('--server.port=8080','--aws.endpoint=http://localhost:4566','--aws.region=us-east-1','--aws.access-key-id=test','--aws.secret-access-key=test','--app.stable-url=http://localhost:8081','--app.candidate-url=http://localhost:8082')

$deadline = (Get-Date).AddMinutes(2)
do {
    try {
        if ((Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8081/actuator/health').StatusCode -eq 200 -and
            (Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8082/actuator/health').StatusCode -eq 200 -and
            (Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/actuator/health').StatusCode -eq 200) {
            Write-Output 'All native Spring Boot apps are healthy.'
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
throw 'Spring Boot apps did not become healthy. Inspect logs/*.log.'
