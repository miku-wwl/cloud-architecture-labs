param(
    [int]$Rps = 30,
    [int]$DurationSeconds = 240,
    [int]$WorkerId = 0,
    [int]$WorkerCount = 1
)

$ErrorActionPreference = 'SilentlyContinue'
if ($Rps -lt 1 -or $WorkerCount -lt 1) { exit 1 }

$base = 'http://localhost:8080/api/payments/authorize'
$intervalMs = [Math]::Max(1, 1000.0 * $WorkerCount / $Rps)
$next = [DateTime]::UtcNow
$deadline = $next.AddSeconds($DurationSeconds)
$sequence = 0

while ([DateTime]::UtcNow -lt $deadline) {
    $sequence++
    $session = "demo-session-$WorkerId-$($sequence % 200)"
    $body = @{ orderId = "demo-order-$WorkerId-$sequence"; amount = 1.00 } | ConvertTo-Json -Compress
    try {
        Invoke-RestMethod -Method Post -Uri $base -Headers @{ 'X-Session-Id' = $session } `
            -ContentType 'application/json' -Body $body -TimeoutSec 5 | Out-Null
    } catch { }

    $next = $next.AddMilliseconds($intervalMs)
    $waitMs = ($next - [DateTime]::UtcNow).TotalMilliseconds
    if ($waitMs -gt 0) {
        Start-Sleep -Milliseconds ([Math]::Min([int]$waitMs, 1000))
    }
}
