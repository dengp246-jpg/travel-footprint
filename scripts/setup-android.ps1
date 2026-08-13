param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $projectRoot.StartsWith('D:\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Android tooling must stay on D drive. Current project: $projectRoot"
}

$sdkRoot = Join-Path $projectRoot '.android-sdk'
$toolsRoot = Join-Path $projectRoot '.android-tools'
$gradleHome = Join-Path $projectRoot '.gradle-user-home'
$commandToolsVersion = '15859902'
$commandToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-$($commandToolsVersion)_latest.zip"
$commandToolsSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
$gradleVersion = '8.11.1'
$gradleOfficialUrl = "https://downloads.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$gradleUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-$gradleVersion-bin.zip"
$gradleChecksumUrl = "$gradleOfficialUrl.sha256"

New-Item -ItemType Directory -Force -Path $sdkRoot, $toolsRoot, $gradleHome | Out-Null

$javaCommand = Get-Command java -ErrorAction Stop
$javaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:GRADLE_USER_HOME = $gradleHome

function Get-VerifiedArchive {
    param(
        [string]$Url,
        [string]$Destination,
        [string]$ExpectedSha256
    )
    $needsDownload = $Force -or -not (Test-Path -LiteralPath $Destination)
    if (-not $needsDownload) {
        $existingHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
        if ($existingHash -eq $ExpectedSha256.ToLowerInvariant()) { return }
        $needsDownload = $true
    }
    if ($needsDownload) {
        $curl = (Get-Command curl.exe -ErrorAction Stop).Source
        if ($Force -and (Test-Path -LiteralPath $Destination)) {
            Remove-Item -LiteralPath $Destination -Force
        }
        if (Test-Path -LiteralPath $Destination) {
            & $curl --location --fail --retry 3 --continue-at - --output $Destination $Url
        } else {
            & $curl --location --fail --retry 3 --output $Destination $Url
        }
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
    if ($actual -ne $ExpectedSha256.ToLowerInvariant()) {
        throw "Checksum mismatch for $Destination. Expected $ExpectedSha256 but received $actual"
    }
}

$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if ($Force -or -not (Test-Path -LiteralPath $sdkManager)) {
    Write-Host '[1/4] Download Android command-line tools to D drive'
    $commandToolsZip = Join-Path $toolsRoot "commandlinetools-win-$commandToolsVersion.zip"
    Get-VerifiedArchive -Url $commandToolsUrl -Destination $commandToolsZip -ExpectedSha256 $commandToolsSha256

    $extractRoot = Join-Path $toolsRoot 'commandlinetools-extract'
    if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $extractRoot | Out-Null
    Expand-Archive -LiteralPath $commandToolsZip -DestinationPath $extractRoot -Force

    $latestRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
    if (Test-Path -LiteralPath $latestRoot) { Remove-Item -LiteralPath $latestRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $latestRoot -Force | Out-Null
    Copy-Item -Path (Join-Path $extractRoot 'cmdline-tools\*') -Destination $latestRoot -Recurse -Force
    Remove-Item -LiteralPath $extractRoot -Recurse -Force
}

Write-Host '[2/4] Accept licenses and install Android API 35 build packages'
$sdkReady = (Test-Path -LiteralPath (Join-Path $sdkRoot 'platforms\android-35\android.jar')) -and
    (Test-Path -LiteralPath (Join-Path $sdkRoot 'build-tools\35.0.0\aapt.exe')) -and
    (Test-Path -LiteralPath (Join-Path $sdkRoot 'platform-tools\adb.exe'))
if (-not $sdkReady) {
    1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses | Out-Host
    & $sdkManager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }
} else {
    Write-Host 'Android API 35 packages are already installed.'
}

Write-Host '[3/4] Download and verify Gradle on D drive'
$gradleZip = Join-Path $toolsRoot "gradle-$gradleVersion-bin.zip"
$gradleExpectedSha = (Invoke-RestMethod -Uri $gradleChecksumUrl).Trim()
Get-VerifiedArchive -Url $gradleUrl -Destination $gradleZip -ExpectedSha256 $gradleExpectedSha
$gradleBin = Join-Path $toolsRoot "gradle-$gradleVersion\bin\gradle.bat"
if ($Force -or -not (Test-Path -LiteralPath $gradleBin)) {
    $gradleExtractRoot = Join-Path $toolsRoot 'gradle-extract'
    if (Test-Path -LiteralPath $gradleExtractRoot) { Remove-Item -LiteralPath $gradleExtractRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $gradleExtractRoot | Out-Null
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleExtractRoot -Force
    $expandedGradle = Join-Path $gradleExtractRoot "gradle-$gradleVersion"
    $finalGradle = Join-Path $toolsRoot "gradle-$gradleVersion"
    if (Test-Path -LiteralPath $finalGradle) { Remove-Item -LiteralPath $finalGradle -Recurse -Force }
    Move-Item -LiteralPath $expandedGradle -Destination $finalGradle
    Remove-Item -LiteralPath $gradleExtractRoot -Recurse -Force
}

Write-Host '[4/4] Write the ignored local Android SDK configuration'
$escapedSdk = $sdkRoot.Replace('\', '\\')
$localProperties = Join-Path $projectRoot 'android-app\local.properties'
[System.IO.File]::WriteAllText($localProperties, "sdk.dir=$escapedSdk`r`n", [System.Text.UTF8Encoding]::new($false))

Write-Host "Android SDK: $sdkRoot"
Write-Host "Gradle: $gradleBin"
Write-Host 'Android toolchain setup completed.'
