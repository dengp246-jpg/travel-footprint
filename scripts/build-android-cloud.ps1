param(
    [Parameter(Mandatory = $true)]
    [string]$ServerUrl
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $projectRoot.StartsWith('D:\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Android cloud build must run from D drive. Current project: $projectRoot"
}

try {
    $serverUri = [System.Uri]::new($ServerUrl.Trim())
} catch {
    throw 'ServerUrl must be a valid HTTPS URL, for example https://travel-footprint-abcd.onrender.com'
}
if ($serverUri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($serverUri.Host)) {
    throw 'The cloud APK only accepts an HTTPS public server address.'
}
if (-not [string]::IsNullOrWhiteSpace($serverUri.Query) -or -not [string]::IsNullOrWhiteSpace($serverUri.Fragment)) {
    throw 'ServerUrl cannot contain query parameters or a fragment.'
}
$normalizedServerUrl = $serverUri.GetLeftPart([System.UriPartial]::Authority).TrimEnd('/')

Write-Host '[1/5] Verify the public cloud health endpoint'
$healthUri = "$normalizedServerUrl/health"
try {
    $healthResponse = Invoke-RestMethod -Uri $healthUri -Method Get -TimeoutSec 90
} catch {
    throw "The cloud server is not ready at $healthUri. Deploy it first, then retry. $($_.Exception.Message)"
}
if ($healthResponse.status -ne 'UP' -or -not $healthResponse.database -or -not $healthResponse.storage) {
    throw "Cloud health is not UP: $($healthResponse | ConvertTo-Json -Compress)"
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

Write-Host '[2/5] Run tests, lint, and build a cloud-configured APK'
Push-Location $androidRoot
try {
    & $gradleBin --no-daemon clean testDebugUnitTest lintDebug assembleDebug "-PserverUrl=$normalizedServerUrl"
    if ($LASTEXITCODE -ne 0) {
        throw "Android Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host '[3/5] Copy the installable cloud APK to outputs'
$sourceApk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
$outputRoot = Join-Path $projectRoot 'outputs'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$outputApk = Join-Path $outputRoot 'travel-footprint-android-cloud.apk'
Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force

Write-Host '[4/5] Refresh the APK bundled into the deployment image'
$distributionRoot = Join-Path $projectRoot 'distribution'
New-Item -ItemType Directory -Force -Path $distributionRoot | Out-Null
$distributionApk = Join-Path $distributionRoot 'travel-footprint-android.apk'
Copy-Item -LiteralPath $outputApk -Destination $distributionApk -Force

Write-Host '[5/5] Verify package signature and checksum'
$aapt = Join-Path $sdkRoot 'build-tools\35.0.0\aapt.exe'
$apkSigner = Join-Path $sdkRoot 'build-tools\35.0.0\apksigner.bat'
& $aapt dump badging $outputApk | Select-Object -First 8 | Out-Host
& $apkSigner verify --verbose $outputApk | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw 'APK signature verification failed.'
}
$apk = Get-Item -LiteralPath $outputApk
$sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputApk).Hash
Write-Host "Cloud server: $normalizedServerUrl"
Write-Host "APK: $($apk.FullName)"
Write-Host "Deployment APK: $distributionApk"
Write-Host "Size: $($apk.Length) bytes"
Write-Host "SHA-256: $sha256"
