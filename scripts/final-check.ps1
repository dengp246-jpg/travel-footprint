$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([System.IO.Path]::GetPathRoot($projectRoot) -ne "D:\") {
    throw "This project must run from drive D. Current path: $projectRoot"
}

Set-Location -LiteralPath $projectRoot
Write-Host "[1/4] Verify D-drive data directories"
foreach ($name in @("data", "uploads", "run")) {
    $path = Join-Path $projectRoot $name
    if (-not (Test-Path -LiteralPath $path)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
    if (-not ((Resolve-Path -LiteralPath $path).Path.StartsWith($projectRoot, [System.StringComparison]::OrdinalIgnoreCase))) {
        throw "Directory is outside the project root: $path"
    }
}

Write-Host "[2/4] Run the complete automated test suite"
& mvn.cmd test
if ($LASTEXITCODE -ne 0) { throw "Automated tests failed." }

Write-Host "[3/4] Build the deployable JAR"
& mvn.cmd -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Application packaging failed." }

Write-Host "[4/4] Verify the build artifact"
$jarPath = Join-Path $projectRoot "target\travel-footprint-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path -LiteralPath $jarPath)) { throw "Build artifact not found: $jarPath" }
Write-Host "Final check passed: $jarPath" -ForegroundColor Green
