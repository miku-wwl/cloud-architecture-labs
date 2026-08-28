$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $labRoot 'mcp-server-lambda\target\mcp-server-lambda-1.0.0.jar'
$packageDir = Join-Path $labRoot 'infra\build\lambda'
if (-not (Test-Path $jar)) { throw "built Lambda jar not found: $jar" }
New-Item -ItemType Directory -Force $packageDir | Out-Null
foreach ($item in (Get-ChildItem -LiteralPath $packageDir -Force)) {
    try {
        Remove-Item -LiteralPath $item.FullName -Recurse -Force -ErrorAction Stop
    } catch {
        Write-Warning "Could not remove old Lambda file; continue by overlaying the new package: $($item.FullName)"
    }
}
Push-Location $packageDir
try {
    & jar xf $jar
    if ($LASTEXITCODE -ne 0) { throw 'jar xf failed' }
} finally {
    Pop-Location
}
if (-not (Test-Path (Join-Path $packageDir 'com\example\securemcp\StreamLambdaHandler.class'))) {
    throw 'Lambda handler missing after extraction'
}
Write-Output "Lambda package ready: $packageDir"
