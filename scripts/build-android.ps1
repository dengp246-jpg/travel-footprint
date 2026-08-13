param(
    [string]$ServerUrl = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $projectRoot.StartsWith('D:\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Android build must run from D drive. Current project: $projectRoot"
}

$sdkRoot = Join-Path $projectRoot '.android-sdk'
$toolsRoot = Join-Path $projectRoot '.android-tools'
$gradleHome = Join-Path $projectRoot '.gradle-user-home'
$androidRoot = Join-Path $projectRoot 'android-app'
$gradleBin = Join-Path $toolsRoot 'gradle-8.11.1\bin\gradle.bat'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'

if (-not (Test-Path -LiteralPath $gradleBin) -or -not (Test-Path -LiteralPath $sdkManager)) {
    & (Join-Path $PSScriptRoot 'setup-android.ps1')
}

$javaCommand = Get-Command java -ErrorAction Stop
$env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:GRADLE_USER_HOME = $gradleHome

$gradleArguments = @('--no-daemon', 'clean', 'testDebugUnitTest', 'lintDebug', 'assembleDebug')
if (-not [string]::IsNullOrWhiteSpace($ServerUrl)) {
    $gradleArguments += "-PserverUrl=$ServerUrl"
}

Write-Host '[1/4] Run Android unit tests, lint, and debug APK build'
Push-Location $androidRoot
try {
    & $gradleBin @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Android Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host '[2/4] Copy the installable APK to outputs'
$sourceApk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $sourceApk)) { throw "APK was not generated: $sourceApk" }
$outputRoot = Join-Path $projectRoot 'outputs'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$outputApk = Join-Path $outputRoot 'travel-footprint-android-debug.apk'
Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force

Write-Host '[3/4] Refresh the APK bundled into the deployment image'
$distributionRoot = Join-Path $projectRoot 'distribution'
New-Item -ItemType Directory -Force -Path $distributionRoot | Out-Null
$distributionApk = Join-Path $distributionRoot 'travel-footprint-android.apk'
Copy-Item -LiteralPath $outputApk -Destination $distributionApk -Force

Write-Host '[4/4] Inspect package metadata and checksum'
$aapt = Join-Path $sdkRoot 'build-tools\35.0.0\aapt.exe'
if (-not (Test-Path -LiteralPath $aapt)) { throw "aapt was not found: $aapt" }
& $aapt dump badging $outputApk | Select-Object -First 8 | Out-Host
$apk = Get-Item -LiteralPath $outputApk
$sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputApk).Hash
Write-Host "APK: $($apk.FullName)"
Write-Host "Deployment APK: $distributionApk"
Write-Host "Size: $($apk.Length) bytes"
Write-Host "SHA-256: $sha256"
