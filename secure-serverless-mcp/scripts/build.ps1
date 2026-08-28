$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
Push-Location $labRoot
try {
    & mvn -q package
    if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $labRoot 'scripts\package-lambda.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Lambda package preparation failed' }
} finally {
    Pop-Location
}
