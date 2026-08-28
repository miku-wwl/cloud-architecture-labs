param([ValidateSet('HEALTHY','ERROR','SLOW')][string]$Scenario = 'HEALTHY')
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
try {
    Invoke-RestMethod -Method Post -Uri "$base/api/demo/traffic/start?rps=30" | Out-Null
    $body = @{ candidateVersion = 'candidate-v2'; scenario = $Scenario } | ConvertTo-Json
    $release = Invoke-RestMethod -Method Post -Uri "$base/api/releases" -ContentType 'application/json' -Body $body
    Write-Output ("Started {0} release {1}" -f $Scenario, $release.releaseId)
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        $current = Invoke-RestMethod -Uri "$base/api/releases/$($release.releaseId)"
        Write-Output ("status={0} stage={1} candidate={2}%" -f $current.status, $current.currentStage, $current.candidatePercentage)
        if ($current.status -in @('PROMOTED','ROLLED_BACK','FAILED')) { break }
    } while ((Get-Date) -lt $deadline)
    if ($current.status -notin @('PROMOTED','ROLLED_BACK')) { throw 'Release did not reach a terminal state.' }
    $expectedStatus = if ($Scenario -eq 'HEALTHY') { 'PROMOTED' } else { 'ROLLED_BACK' }
    $expectedPercentage = if ($Scenario -eq 'HEALTHY') { 100 } else { 0 }
    if ($current.status -ne $expectedStatus -or $current.candidatePercentage -ne $expectedPercentage) {
        throw ("Unexpected result: status={0}, candidate={1}%" -f $current.status, $current.candidatePercentage)
    }
    $current | ConvertTo-Json -Depth 5
} catch {
    Write-Error $_
    exit 1
}
