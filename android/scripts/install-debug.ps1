#!/usr/bin/env pwsh
# Install the debug APK on the connected device and launch the launcher.
$ErrorActionPreference = 'Stop'

$androidRoot = Split-Path -Parent $PSScriptRoot
$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path $sdk)) { $sdk = 'C:\Android\android-sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$apk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    throw "APK not found: $apk. Run .\android\scripts\build-debug.ps1 first."
}

& $adb devices
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed with exit code $LASTEXITCODE" }

& $adb shell am force-stop com.project1944.cod3
& $adb shell am start -n com.project1944.cod3/.MainActivity
Write-Host 'Launched com.project1944.cod3/.MainActivity'
