#!/usr/bin/env pwsh
# Build the M0a debug APK. Run from anywhere; paths are resolved from this file.
$ErrorActionPreference = 'Stop'

$androidRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $androidRoot

$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path $sdk)) {
    $sdk = $env:ANDROID_SDK_ROOT
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    foreach ($candidate in @('C:\Android\android-sdk', "$env:LOCALAPPDATA\Android\Sdk")) {
        if (Test-Path $candidate) {
            $sdk = $candidate
            break
        }
    }
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    throw 'Android SDK not found. Set ANDROID_HOME or install the SDK.'
}

$ndkVersion = '27.3.13750724'
$ndk = Join-Path $sdk "ndk\$ndkVersion"
if (-not (Test-Path $ndk)) {
    $available = Get-ChildItem (Join-Path $sdk 'ndk') -Directory -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Name
    throw "NDK $ndkVersion not found under $sdk\ndk. Available: $($available -join ', ')"
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_NDK_HOME = $ndk

$localProperties = Join-Path $androidRoot 'local.properties'
if (-not (Test-Path $localProperties)) {
    $sdkForProperties = $sdk.Replace('\', '/')
    "sdk.dir=$sdkForProperties" | Set-Content -Path $localProperties -Encoding ASCII
    Write-Host "Wrote $localProperties"
}

$gradlew = Join-Path $androidRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    throw "Gradle wrapper missing: $gradlew"
}

Write-Host "Android SDK : $sdk"
Write-Host "Android NDK : $ndk"
Write-Host "Repository  : $repoRoot"
Write-Host "Building    : :app:assembleDebug"

Push-Location $androidRoot
try {
    & $gradlew ':app:assembleDebug' '--stacktrace'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$apk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    throw "Build completed but APK was not found at $apk"
}
Write-Host "APK: $apk"
