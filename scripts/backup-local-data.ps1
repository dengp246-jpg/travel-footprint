$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([System.IO.Path]::GetPathRoot($projectRoot) -ne "D:\") {
    throw "Backups must be created inside the D-drive project."
}

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    throw "Port 8080 is still serving the app. Run stop-app.bat before copying the H2 database."
}

$backupRoot = Join-Path $projectRoot "backups"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destination = Join-Path $backupRoot $timestamp
$resolvedBackupRoot = [System.IO.Path]::GetFullPath($backupRoot)
$resolvedDestination = [System.IO.Path]::GetFullPath($destination)
if (-not $resolvedDestination.StartsWith($resolvedBackupRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Backup destination is outside the allowed root: $resolvedDestination"
}

New-Item -ItemType Directory -Path $resolvedDestination -Force | Out-Null
foreach ($name in @("data", "uploads")) {
    $source = Join-Path $projectRoot $name
    if (Test-Path -LiteralPath $source) {
        Copy-Item -LiteralPath $source -Destination (Join-Path $resolvedDestination $name) -Recurse
    }
}
Write-Host "Local data backup created at: $resolvedDestination" -ForegroundColor Green
