# Забрать с телефона записи отладочной сборки (WAV + признаки + вердикт) в tools\whine\data\own.
$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\tow-sdk.ps1"

$dst = Join-Path $PSScriptRoot "..\tools\whine\data\own"
New-Item -ItemType Directory -Force $dst | Out-Null
& $adb start-server | Out-Null
& $adb pull /sdcard/Android/data/com.tidyorwhiny.app/files/voice/. $dst
Write-Host "-> $dst"
