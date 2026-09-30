# Finds the Android SDK: ANDROID_HOME / ANDROID_SDK_ROOT, then the usual install places.
# Dot-source it: . "$PSScriptRoot\tow-sdk.ps1"  -> sets $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, $adb
$candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "E:\Android\Sdk", (Join-Path $env:LOCALAPPDATA "Android\Sdk")) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "platform-tools\adb.exe")) }
$sdk = $candidates | Select-Object -First 1
if (-not $sdk) {
    Write-Host "Android SDK not found (set ANDROID_HOME)." -ForegroundColor Red
    exit 1
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$adb = Join-Path $sdk "platform-tools\adb.exe"
