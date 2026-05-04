$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $projectRoot "run"
$pidFile = Join-Path $runDir "app.pid"

$connection = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1

if ($null -eq $connection) {
    Write-Host "System status: stopped"
    exit 0
}

$pidValue = [int]$connection.OwningProcess
$storedPid = $null
if (Test-Path $pidFile) {
    $storedPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
}

Write-Host "System status: running"
Write-Host "Home page: http://localhost:8080"
Write-Host "Listening PID: $pidValue"
if ($storedPid) {
    Write-Host "Stored PID: $storedPid"
}
