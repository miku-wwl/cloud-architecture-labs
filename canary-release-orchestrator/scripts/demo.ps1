param([ValidateSet('HEALTHY','ERROR','SLOW')][string]$Scenario = 'HEALTHY')
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$trafficScript = Join-Path $PSScriptRoot 'generate-traffic.ps1'
$trafficProcesses = @()
$workerCount = 8
$durationSeconds = 240

try {
    Invoke-RestMethod -Method Put -Uri "http://localhost:8082/internal/fault-mode/$Scenario" | Out-Null
    for ($worker = 0; $worker -lt $workerCount; $worker++) {
        $trafficProcesses += Start-Process -FilePath 'powershell' -WindowStyle Hidden -PassThru -ArgumentList @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $trafficScript,
            '-Rps', '30', '-DurationSeconds', "$durationSeconds",
            '-WorkerId', "$worker", '-WorkerCount', "$workerCount"
        )
    }
    Start-Sleep -Seconds 1

    $body = @{ candidateVersion = 'candidate-v2' } | ConvertTo-Json
    $release = Invoke-RestMethod -Method Post -Uri "$base/api/releases" `
        -ContentType 'application/json' -Body $body
    Write-Output ("Started {0} release {1}" -f $Scenario, $release.releaseId)

    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        $current = Invoke-RestMethod -Uri "$base/api/releases/$($release.releaseId)"
        Write-Output ("status={0} stage={1} candidate={2}%" -f `
            $current.status, $current.currentStage, $current.candidatePercentage)
        if ($current.status -in @('PROMOTED','ROLLED_BACK','FAILED')) { break }
    } while ((Get-Date) -lt $deadline)

    if ($current.status -notin @('PROMOTED','ROLLED_BACK')) {
        throw 'Release did not reach a terminal state.'
    }
    $expectedStatus = if ($Scenario -eq 'HEALTHY') { 'PROMOTED' } else { 'ROLLED_BACK' }
    $expectedPercentage = if ($Scenario -eq 'HEALTHY') { 100 } else { 0 }
    if ($current.status -ne $expectedStatus -or $current.candidatePercentage -ne $expectedPercentage) {
        throw ("Unexpected result: status={0}, candidate={1}%" -f `
            $current.status, $current.candidatePercentage)
    }
    $current | ConvertTo-Json -Depth 5
} finally {
    foreach ($process in $trafficProcesses) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
}
