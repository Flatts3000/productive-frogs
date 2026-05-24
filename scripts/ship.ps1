# Ship pipeline for Productive Frogs.
#
# Runs the automatable portion of the release playbook end-to-end:
#   1. Verify clean git state on main, at a release tag
#   2. Read version + tag, confirm they agree
#   3. Override JAVA_HOME to JDK 21 (system JAVA_HOME on this box is stale)
#   4. Clean build + prepareAllRuns
#      (prepareAllRuns is an aggregate task defined in build.gradle that
#      regenerates build/moddev/*RunVmArgs.txt and friends. IntelliJ's
#      runClient/runServer/etc launch configs reference those files;
#      gradle clean wipes them, so IDE launches break after every clean
#      unless we regenerate. Chaining the prep onto the build command
#      keeps the IDE working between pipeline runs.)
#   5. Run GameTests (separate CI gate, not invoked by `build`)
#   6. Sanity-check the produced jar (size, neoforge.mods.toml version)
#   7. Optionally upload the jar to the matching GitHub Release
#
# The two things this script CANNOT do for you:
#   * Smoke-test in a real client (requires eyeballs on rendering / tint / sound)
#   * Upload to CurseForge (no Gradle plugin configured; CF first-upload of a
#     new project must be manual anyway). Add CurseForgeGradle from v1.0.1
#     onward - see scripts/ship.ps1 header comment in CHANGELOG / docs.
#
# Usage:
#   .\scripts\ship.ps1                     # full pipeline, halts at smoke-test gate
#   .\scripts\ship.ps1 -Upload             # skip rebuild, just upload existing jar
#   .\scripts\ship.ps1 -SkipGameTests      # build+upload without gametest gate (NOT for release)
#   .\scripts\ship.ps1 -SkipUpload         # build+verify but don't touch GH Release
#   .\scripts\ship.ps1 -PublishCurseForge  # also push to CurseForge after GH Release upload

[CmdletBinding()]
param(
    [switch]$Upload,
    [switch]$SkipGameTests,
    [switch]$SkipUpload,
    [switch]$PublishCurseForge,
    [string]$JdkPath = "C:\Program Files\Java\jdk-21"
)

$ErrorActionPreference = "Stop"

# Resolve repo root from script location so the script works from any cwd.
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Write-Stage($n, $total, $msg) {
    Write-Host ""
    Write-Host "[$n/$total] $msg" -ForegroundColor Cyan
}

function Fail($msg) {
    Write-Host ""
    Write-Host "FAIL: $msg" -ForegroundColor Red
    exit 1
}

$total = 8
$start = Get-Date

# ----------------------------------------------------------------------------
# 1. Git state - clean tree, on main, at a tag
# ----------------------------------------------------------------------------
if (-not $Upload) {
    Write-Stage 1 $total "Verifying git state"

    $branch = (git rev-parse --abbrev-ref HEAD).Trim()
    if ($branch -ne "main") { Fail "expected branch=main, got '$branch'" }

    $dirty = git status --porcelain
    if ($dirty) { Fail "working tree dirty:`n$dirty" }

    git fetch origin main --quiet
    $behind = (git rev-list --count HEAD..origin/main).Trim()
    if ([int]$behind -gt 0) { Fail "branch is $behind commits behind origin/main; pull first" }

    Write-Host "  branch=main, clean, up-to-date" -ForegroundColor Green
} else {
    Write-Stage 1 $total "Verifying git state (skipped - -Upload mode)"
}

# ----------------------------------------------------------------------------
# 2. Tag + version agreement
# ----------------------------------------------------------------------------
Write-Stage 2 $total "Reading version + tag"

$gradleProps = Get-Content "gradle.properties"
$versionLine = $gradleProps | Where-Object { $_ -match '^mod_version=' }
if (-not $versionLine) { Fail "mod_version not found in gradle.properties" }
$modVersion = ($versionLine -replace '^mod_version=', '').Trim()
$expectedTag = "v$modVersion"

$tagDesc = (git describe --tags --exact-match HEAD 2>&1)
if ($LASTEXITCODE -ne 0) { Fail "HEAD is not at a tag (expected $expectedTag). git describe: $tagDesc" }
$actualTag = $tagDesc.Trim()
if ($actualTag -ne $expectedTag) { Fail "tag mismatch: HEAD at '$actualTag', expected '$expectedTag' from mod_version" }

Write-Host "  version=$modVersion, tag=$actualTag" -ForegroundColor Green

# ----------------------------------------------------------------------------
# 3. JAVA_HOME override
# ----------------------------------------------------------------------------
Write-Stage 3 $total "Setting JAVA_HOME"

if (-not (Test-Path $JdkPath)) { Fail "JDK not found at $JdkPath" }
$env:JAVA_HOME = $JdkPath
Write-Host "  JAVA_HOME=$JdkPath" -ForegroundColor Green

# ----------------------------------------------------------------------------
# 4. Clean build (skipped on -Upload)
# ----------------------------------------------------------------------------
$jarPath = "build\libs\productivefrogs-$modVersion.jar"

if (-not $Upload) {
    Write-Stage 4 $total "Clean build + prepareAllRuns (keeps IDE launch configs working)"
    & .\gradlew.bat clean build prepareAllRuns
    if ($LASTEXITCODE -ne 0) { Fail "gradle build failed (exit $LASTEXITCODE)" }
    if (-not (Test-Path $jarPath)) { Fail "expected jar at $jarPath, not found" }
    Write-Host "  produced $jarPath" -ForegroundColor Green
} else {
    Write-Stage 4 $total "Clean build (skipped - -Upload mode)"
    if (-not (Test-Path $jarPath)) { Fail "expected jar at $jarPath, not found (run without -Upload first)" }
}

# ----------------------------------------------------------------------------
# 5. GameTests
# ----------------------------------------------------------------------------
if (-not $Upload -and -not $SkipGameTests) {
    Write-Stage 5 $total "Running GameTests"
    & .\gradlew.bat runGameTestServer
    if ($LASTEXITCODE -ne 0) { Fail "GameTests failed (exit $LASTEXITCODE)" }
    Write-Host "  all GameTests passed" -ForegroundColor Green
} else {
    Write-Stage 5 $total "GameTests (skipped)"
}

# ----------------------------------------------------------------------------
# 6. Jar sanity - size, version in neoforge.mods.toml
# ----------------------------------------------------------------------------
Write-Stage 6 $total "Sanity-checking jar"

$jarInfo = Get-Item $jarPath
$sizeKb = [Math]::Round($jarInfo.Length / 1024, 1)
if ($jarInfo.Length -lt 100KB) { Fail "jar suspiciously small ($sizeKb KB)" }
if ($jarInfo.Length -gt 50MB)  { Fail "jar suspiciously large ($sizeKb KB)" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jarInfo.FullName)
try {
    $entry = $zip.GetEntry("META-INF/neoforge.mods.toml")
    if (-not $entry) { Fail "META-INF/neoforge.mods.toml not in jar" }
    $reader = New-Object System.IO.StreamReader($entry.Open())
    try { $toml = $reader.ReadToEnd() } finally { $reader.Close() }
} finally { $zip.Dispose() }

if ($toml -notmatch [regex]::Escape("version = `"$modVersion`"")) {
    Fail "jar neoforge.mods.toml version line missing or mismatched (expected version = `"$modVersion`")"
}
if ($toml -notmatch 'logoFile\s*=\s*"logo\.png"') {
    Write-Host "  warning: logoFile not set to logo.png" -ForegroundColor Yellow
}

Write-Host "  $($jarInfo.Name), $sizeKb KB, version=$modVersion" -ForegroundColor Green

# ----------------------------------------------------------------------------
# 7. Upload to GitHub Release
# ----------------------------------------------------------------------------
if (-not $SkipUpload) {
    Write-Stage 7 $total "Uploading to GitHub Release $expectedTag"

    $releaseCheck = gh release view $expectedTag --json tagName,isDraft 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "GitHub Release $expectedTag not found. Create it with 'gh release create $expectedTag' first." }

    & gh release upload $expectedTag $jarPath --clobber
    if ($LASTEXITCODE -ne 0) { Fail "gh release upload failed (exit $LASTEXITCODE)" }

    Write-Host "  attached to https://github.com/Flatts3000/productive-frogs/releases/tag/$expectedTag" -ForegroundColor Green
} else {
    Write-Stage 7 $total "GitHub Release upload (skipped - -SkipUpload mode)"
}

# ----------------------------------------------------------------------------
# 8. Publish to CurseForge (optional)
#
# Calls the publishCurseForge gradle task, which uses CurseForgeGradle to
# upload via the official CF Upload API. Requires CURSEFORGE_API_KEY in .env
# at repo root (or env var, or cfApiToken in ~/.gradle/gradle.properties).
# Project description and metadata must still be edited by hand in the CF
# browser dashboard - the API only covers file uploads.
# ----------------------------------------------------------------------------
if ($PublishCurseForge) {
    Write-Stage 8 $total "Publishing to CurseForge"
    & .\gradlew.bat publishCurseForge
    if ($LASTEXITCODE -ne 0) { Fail "CurseForge publish failed (exit $LASTEXITCODE)" }
    Write-Host "  published to https://authors.curseforge.com/#/projects/1552728/files" -ForegroundColor Green
} else {
    Write-Stage 8 $total "CurseForge publish (skipped; pass -PublishCurseForge to enable)"
}

# ----------------------------------------------------------------------------
# Done
# ----------------------------------------------------------------------------
$elapsed = (Get-Date) - $start
Write-Host ""
Write-Host "DONE - pipeline finished in $([Math]::Round($elapsed.TotalSeconds, 1))s" -ForegroundColor Green
Write-Host ""
Write-Host "Manual steps remaining:" -ForegroundColor Yellow
Write-Host "  1. Smoke-test in dev client:  .\gradlew.bat runClient"
if (-not $PublishCurseForge) {
    Write-Host "  2. CurseForge upload (browser, or re-run this script with -PublishCurseForge):"
    Write-Host "     File: $jarPath"
    Write-Host "     Game ver: 1.21.1, Loader: NeoForge, Java: 21, Release type: Release"
    Write-Host "     Changelog: paste v$modVersion section from CHANGELOG.md"
}
