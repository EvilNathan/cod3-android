# Export the Android changes in tools/rexglue-source as a single patch.
# The SDK submodule itself is never committed: the pinned upstream commit plus
# this patch is the complete Android SDK source.
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$sdk = Join-Path $root "tools\rexglue-source"
$out = Join-Path $PSScriptRoot "rexglue-android.patch"

if (-not (Test-Path (Join-Path $sdk ".git"))) {
    throw "ReXGlue submodule missing at $sdk"
}

# Stage everything except build trees so new files are included in the patch.
& git -C $sdk add -A -- . ":(exclude)build-android"
if ($LASTEXITCODE -ne 0) { throw "git add failed" }

$lines = & git -C $sdk diff --cached --binary
if ($LASTEXITCODE -ne 0) { throw "git diff failed" }

# Write LF endings without a BOM so git apply works on every platform.
$text = ($lines -join "`n") + "`n"
[IO.File]::WriteAllText($out, $text, [Text.UTF8Encoding]::new($false))

& git -C $sdk reset --quiet
Write-Host "Wrote $out ($([Math]::Round($text.Length / 1KB)) KiB)"
