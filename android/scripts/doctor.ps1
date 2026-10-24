#!/usr/bin/env pwsh
# Print the Android toolchain state without building anything.
$ErrorActionPreference = 'Continue'

$androidRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $androidRoot

Write-Host "Repository : $repoRoot"
Write-Host "JAVA_HOME  : $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"
Write-Host "ANDROID_NDK_HOME: $env:ANDROID_NDK_HOME"

$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) { & $java.Source -version 2>&1 | Select-Object -First 1 }

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = 'C:\Android\android-sdk' }
Write-Host "SDK        : $sdk"
Get-ChildItem (Join-Path $sdk 'platforms') -Directory -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Name | ForEach-Object { Write-Host "  platform $_" }
Get-ChildItem (Join-Path $sdk 'ndk') -Directory -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Name | ForEach-Object { Write-Host "  ndk $_" }

$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (Test-Path $adb) {
    Write-Host '--- adb devices ---'
    & $adb devices
    $features = & $adb shell pm list features 2>$null
    if ($features) {
        Write-Host '--- Vulkan features ---'
        $features | Select-String -Pattern 'vulkan|gamepad' | ForEach-Object { Write-Host $_.Line }
    }
} else {
    Write-Host 'adb: MISSING'
}

Write-Host '--- pinned source ---'
git -C (Join-Path $repoRoot 'tools\rexglue-source') log -1 --oneline 2>$null
git -C (Join-Path $repoRoot 'tools\rexglue-source\thirdparty\sdl3') log -1 --oneline 2>$null
