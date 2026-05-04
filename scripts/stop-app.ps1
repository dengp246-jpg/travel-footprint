$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $projectRoot "run"
$pidFile = Join-Path $runDir "app.pid"
$launcherPidFile = Join-Path $runDir "app.launcher.pid"

function Get-CommandLine([int]$ProcessId) {
    try {
        return (Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId").CommandLine
    } catch {
        return $null
    }
}

function Stop-IfRunning([int]$ProcessId) {
    try {
        $process = Get-Process -Id $ProcessId -ErrorAction Stop
        Stop-Process -Id $process.Id -Force -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

$stoppedIds = [System.Collections.Generic.List[int]]::new()

if (Test-Path $pidFile) {
    $appPidText = (Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($appPidText -match "^\d+$") {
        $appPid = [int]$appPidText
        if (Stop-IfRunning $appPid) {
            $stoppedIds.Add($appPid)
        }
    }
}

if (Test-Path $launcherPidFile) {
    $launcherPidText = (Get-Content -LiteralPath $launcherPidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($launcherPidText -match "^\d+$") {
        $launcherPid = [int]$launcherPidText
        if (-not $stoppedIds.Contains($launcherPid) -and (Stop-IfRunning $launcherPid)) {
            $stoppedIds.Add($launcherPid)
        }
    }
}

$connection = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
if ($null -ne $connection) {
    $portPid = [int]$connection.OwningProcess
    $commandLine = Get-CommandLine $portPid
    $belongsToProject = $null -ne $commandLine -and (
        $commandLine.Contains($projectRoot) -or
        $commandLine.Contains("TravelFootprintApplication") -or
        $commandLine.Contains("travel-footprint")
    )

    if ($belongsToProject -and -not $stoppedIds.Contains($portPid) -and (Stop-IfRunning $portPid)) {
        $stoppedIds.Add($portPid)
    }
}

Remove-Item -LiteralPath $pidFile, $launcherPidFile -Force -ErrorAction SilentlyContinue

if ($stoppedIds.Count -gt 0) {
    Write-Host ("System stopped. Closed PID(s): " + ($stoppedIds -join ", "))
} else {
    Write-Host "No running system process was found."
}
