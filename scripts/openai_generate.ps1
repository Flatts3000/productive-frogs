# OpenAI gpt-image-1 image generation client.
#
# Reads OPENAI_API_KEY, OPENAI_IMAGE_MODEL, OPENAI_IMAGE_QUALITY from .env at
# the repo root. Calls POST /v1/images/generations with the supplied prompt,
# saves the returned base64-encoded PNGs to gen/<slug>/<idx>.png, then writes
# a 16x16 nearest-neighbor + palette-quantized preview alongside each one.
#
# Usage:
#   .\scripts\openai_generate.ps1 -PromptFile prompts/slime_milker_gui.txt -Slug slime_milker_gui -Count 4
#   .\scripts\openai_generate.ps1 -Prompt "A 16-bit pixel art ..." -Slug test -Count 1
#
# The downscale-and-quantize preview is for evaluating pixel-art viability;
# the raw 1024x1024 is kept too for any surface that should ship at higher
# resolution (GUI backgrounds typically do -- Minecraft renders them at the
# stored pixel density, not at block-texture pixel density).

[CmdletBinding(DefaultParameterSetName='Prompt')]
param(
    [Parameter(ParameterSetName='Prompt', Mandatory=$true)]
    [string]$Prompt,

    [Parameter(ParameterSetName='PromptFile', Mandatory=$true)]
    [string]$PromptFile,

    [Parameter(Mandatory=$true)]
    [string]$Slug,

    [int]$Count = 4,
    [string]$Size = "1024x1024",
    [int]$PreviewSize = 16,
    [int]$PreviewColors = 16
)

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$envPath = Join-Path $repoRoot ".env"
$genRoot = Join-Path $repoRoot "gen"

# Load .env into a hashtable. Skip blank lines and comments. The whole point
# of .env is keys never appear on the command line -- Get-Content reads them
# locally, the script uses the value, but never echoes it.
function Read-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        Write-Error "No .env file at $Path. Copy from .env.example or create one with OPENAI_API_KEY=..."
        exit 1
    }
    $map = @{}
    foreach ($line in Get-Content $Path) {
        $trim = $line.Trim()
        if ($trim -eq "" -or $trim.StartsWith("#")) { continue }
        $eq = $trim.IndexOf("=")
        if ($eq -le 0) { continue }
        $k = $trim.Substring(0, $eq).Trim()
        $v = $trim.Substring($eq + 1).Trim().Trim('"').Trim("'")
        $map[$k] = $v
    }
    return $map
}

$envMap = Read-DotEnv -Path $envPath

# Prefer process env if set; fall back to .env. Never log either value.
$apiKey = if ($env:OPENAI_API_KEY) { $env:OPENAI_API_KEY } else { $envMap["OPENAI_API_KEY"] }
$model = if ($envMap["OPENAI_IMAGE_MODEL"]) { $envMap["OPENAI_IMAGE_MODEL"] } else { "gpt-image-1" }
$quality = if ($envMap["OPENAI_IMAGE_QUALITY"]) { $envMap["OPENAI_IMAGE_QUALITY"] } else { "low" }

if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -eq "sk-REPLACE-ME") {
    Write-Error "OPENAI_API_KEY missing or unset. Edit .env and replace the placeholder."
    exit 1
}

if ($PSCmdlet.ParameterSetName -eq 'PromptFile') {
    if (-not (Test-Path $PromptFile)) { Write-Error "Prompt file not found: $PromptFile"; exit 1 }
    $Prompt = (Get-Content $PromptFile -Raw).Trim()
}

# Output dir per slug. Versioned by timestamp so successive runs don't
# clobber prior candidates -- picking a best-of often means revisiting older
# generations.
$ts = (Get-Date -Format "yyyyMMdd-HHmmss")
$outDir = Join-Path $genRoot "$Slug-$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Output "OpenAI gpt-image-1: model=$model quality=$quality size=$Size n=$Count slug=$Slug"
Write-Output "Output: $outDir"

# Build the request body. gpt-image-1 returns base64-encoded PNGs in
# data[*].b64_json, so we don't need to deal with URL fetches.
$body = @{
    model = $model
    prompt = $Prompt
    n = $Count
    size = $Size
    quality = $quality
} | ConvertTo-Json -Depth 5

$headers = @{
    "Authorization" = "Bearer $apiKey"
    "Content-Type" = "application/json"
}

try {
    $resp = Invoke-RestMethod -Uri "https://api.openai.com/v1/images/generations" -Method POST -Headers $headers -Body $body -TimeoutSec 180
} catch {
    Write-Error "OpenAI request failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Write-Error $_.ErrorDetails.Message }
    exit 1
}

if (-not $resp.data) {
    Write-Error "Empty data array in OpenAI response."
    exit 1
}

# Save each candidate + a downscaled preview.
$idx = 0
foreach ($img in $resp.data) {
    $idx++
    if (-not $img.b64_json) {
        Write-Warning "Candidate $idx has no b64_json -- skipping."
        continue
    }
    $bytes = [Convert]::FromBase64String($img.b64_json)
    $rawPath = Join-Path $outDir "$idx.png"
    [System.IO.File]::WriteAllBytes($rawPath, $bytes)
    Write-Output "wrote $rawPath ($($bytes.Length) bytes)"

    # Build downscaled preview. Nearest-neighbor + indexed palette quantize.
    # This shows what the texture would look like at ship resolution; the
    # raw 1024 is preserved for surfaces that ship at higher density (GUIs).
    $srcImg = [System.Drawing.Image]::FromFile($rawPath)
    try {
        $preview = New-Object System.Drawing.Bitmap $PreviewSize, $PreviewSize
        $g = [System.Drawing.Graphics]::FromImage($preview)
        try {
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $g.DrawImage($srcImg, 0, 0, $PreviewSize, $PreviewSize)
        } finally { $g.Dispose() }
        # Palette quantize via System.Drawing PixelFormat conversion to 8bpp
        # indexed -- gives a 256-color palette by default; for stricter 16-color
        # quantization we'd need a third-party median-cut library. 256 is
        # already a reduction from 32bpp and good enough for PoC visual eval.
        $previewPath = Join-Path $outDir "${idx}_preview.png"
        $preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $preview.Dispose()
        Write-Output "wrote $previewPath ($PreviewSize x $PreviewSize)"
    } finally { $srcImg.Dispose() }
}

# Stamp the prompt + metadata into a sidecar so future runs / PR descriptions
# can reference exactly what was asked.
$meta = @{
    timestamp = $ts
    model = $model
    quality = $quality
    size = $Size
    count = $Count
    prompt = $Prompt
}
$metaPath = Join-Path $outDir "_meta.json"
$meta | ConvertTo-Json -Depth 5 | Out-File -FilePath $metaPath -Encoding utf8
Write-Output "wrote $metaPath"
Write-Output "done -- $idx candidate(s)"
