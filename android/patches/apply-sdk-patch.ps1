# Apply the Android SDK patch to a freshly initialized ReXGlue submodule.
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$sdk = Join-Path $root "tools\rexglue-source"
$patch = Join-Path $PSScriptRoot "rexglue-android.patch"
$pin = "0c7b01a0ac0479801757507d80533f662fa0815d"

if (-not (Test-Path $patch)) {
    throw "Patch not found: $patch"
}

$head = (& git -C $sdk rev-parse HEAD).Trim()
if ($head -ne $pin) {
    throw "ReXGlue is at $head, expected $pin. Run tools/rexglue-source submodule update first."
}

& git -C $sdk apply --whitespace=nowarn $patch
if ($LASTEXITCODE -ne 0) { throw "git apply failed" }

Write-Host "Applied $patch to ReXGlue $pin"
