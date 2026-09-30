$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\tow-sdk.ps1"

& $adb start-server | Out-Null
Write-Host "Following logcat tags Inspector (ответы Qwen) and AndroidRuntime (падения). Press Ctrl+C to stop."
& $adb logcat -v time Inspector:D AndroidRuntime:E *:S
