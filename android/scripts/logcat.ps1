#!/usr/bin/env pwsh
# Tail the useful logcat tags for the Android port.
$ErrorActionPreference = 'Stop'

$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path $sdk)) { $sdk = 'C:\Android\android-sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

& $adb logcat -c
& $adb logcat -v time project1944:V SDL:V SDL/APP:V AndroidRuntime:E DEBUG:E libc:E
