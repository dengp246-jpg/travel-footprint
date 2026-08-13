param(
    [Parameter(Mandatory = $true)]
    [string]$ServerUrl
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $projectRoot.StartsWith('D:\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Cloud verification must run from D drive. Current project: $projectRoot"
}

try {
    $uri = [System.Uri]::new($ServerUrl.Trim())
} catch {
    throw 'ServerUrl must be a valid HTTPS URL.'
}
if ($uri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($uri.Host)) {
    throw 'Cloud verification requires an HTTPS public URL.'
}
$baseUrl = $uri.GetLeftPart([System.UriPartial]::Authority).TrimEnd('/')

Write-Host "Verify cloud deployment: $baseUrl"
$health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get -TimeoutSec 90
if ($health.status -ne 'UP' -or -not $health.database -or -not $health.storage) {
    throw "Health check failed: $($health | ConvertTo-Json -Compress)"
}

$home = Invoke-WebRequest -Uri "$baseUrl/" -Method Get -UseBasicParsing -TimeoutSec 90
if ($home.StatusCode -ne 200 -or $home.Content -notmatch '旅迹') {
    throw 'The public home page did not return the expected application.'
}

$provinces = Invoke-RestMethod -Uri "$baseUrl/api/mini/catalog/provinces" -Method Get -TimeoutSec 90
if (@($provinces).Count -lt 30) {
    throw 'The mobile API did not return the expected province catalog.'
}

[pscustomobject]@{
    status = 'UP'
    server = $baseUrl
    database = [bool]$health.database
    storage = [bool]$health.storage
    home = $home.StatusCode
    provinceCount = @($provinces).Count
} | ConvertTo-Json
