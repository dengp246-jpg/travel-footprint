$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $projectRoot "run"
$pidFile = Join-Path $runDir "app.pid"
$launcherPidFile = Join-Path $runDir "app.launcher.pid"
$stdoutLog = Join-Path $runDir "app.out.log"
$stderrLog = Join-Path $runDir "app.err.log"

function Get-PortProcess {
    $connection = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
            Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }

    try {
        return Get-Process -Id $connection.OwningProcess -ErrorAction Stop
    } catch {
        return $null
    }
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$existingProcess = Get-PortProcess
if ($null -ne $existingProcess) {
    Set-Content -LiteralPath $pidFile -Value $existingProcess.Id -Encoding ASCII
    Write-Host "System is already running on http://localhost:8080"
    Write-Host "App PID: $($existingProcess.Id)"
    exit 0
}

Remove-Item -LiteralPath $stdoutLog, $stderrLog -Force -ErrorAction SilentlyContinue

$launcher = Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "mvn spring-boot:run" `
    -WorkingDirectory $projectRoot `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru `
    -WindowStyle Hidden

Set-Content -LiteralPath $launcherPidFile -Value $launcher.Id -Encoding ASCII

$deadline = (Get-Date).AddSeconds(45)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $activeProcess = Get-PortProcess
    if ($null -ne $activeProcess) {
        Set-Content -LiteralPath $pidFile -Value $activeProcess.Id -Encoding ASCII
        Write-Host "System started successfully."
        Write-Host "Home page: http://localhost:8080"
        Write-Host "App PID: $($activeProcess.Id)"
        exit 0
    }

    $launcher.Refresh()
    if ($launcher.HasExited) {
        break
    }
}

Write-Host "Startup failed. Recent logs:"
if (Test-Path $stdoutLog) {
    Get-Content -LiteralPath $stdoutLog -Tail 30
}
if (Test-Path $stderrLog) {
    Get-Content -LiteralPath $stderrLog -Tail 30
}
exit 1
