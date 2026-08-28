$ErrorActionPreference = 'Stop'
$runDir = Join-Path (Split-Path -Parent $PSScriptRoot) '.run'
if (Test-Path $runDir) {
    Get-ChildItem -LiteralPath $runDir -Filter '*.pid' | ForEach-Object {
        $pidValue = [int](Get-Content -Raw $_.FullName)
        $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
        if ($process) { Stop-Process -Id $pidValue -Force }
        Remove-Item -LiteralPath $_.FullName -Force
    }
}
Write-Output 'Native Spring Boot app processes stopped.'
