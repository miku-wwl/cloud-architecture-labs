$ErrorActionPreference = 'Stop'
$deadline = (Get-Date).AddMinutes(3)
do {
    try {
        $health = Invoke-RestMethod -Uri 'http://localhost:4566/_localstack/health' -TimeoutSec 5
        if ($health) { Write-Output 'LocalStack is ready.'; exit 0 }
    } catch { }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
throw 'LocalStack did not become ready within three minutes.'
