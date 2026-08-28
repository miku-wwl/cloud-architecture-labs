$ErrorActionPreference = 'Stop'
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/demo/reset' | ConvertTo-Json
