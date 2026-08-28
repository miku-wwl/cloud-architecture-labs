$ErrorActionPreference = 'Stop'
Invoke-RestMethod -Method Put -Uri 'http://localhost:8082/internal/fault-mode/HEALTHY' | Out-Null
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/demo/reset' | ConvertTo-Json
