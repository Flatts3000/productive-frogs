# Downscale + tone-map a PixelLab silhouette generation into a 16x16
# tintable mask for the bucket-content layer0.
#
# Input:  32x32 PixelLab RGBA PNG with the silhouette body on transparent BG.
# Output: 16x16 PNG, brightened so the body pixels approach near-white
#         (multiply well with the runtime category tint) while preserving
#         dark accent pixels (eyes, mouth) so they stay visible across all
#         category tints.
#
# Tint pipeline reminder: BucketedCategoryTint (registered in PFClientEvents)
# does color = tint.rgb * layer0.lightness per pixel. So:
#   white pixel * tint = tint color    (body reads as category color)
#   black pixel * tint = black          (eyes/outline stay dark)
#   gray pixel  * tint = darker tint    (avoid - mutes the variant signal)
#
# Tone-mapping rule below: for each non-transparent source pixel:
#   if max(R,G,B) >= 64 (it's a body pixel)  -> set to near-white (220,220,220)
#   if max(R,G,B) <  64 (it's a dark accent) -> keep as-is (eyes / outline)
# 64 is empirically the threshold that separates the silhouette body
# (mid-gray PixelLab output) from the eye dots (near-black).
#
# Usage:
#   .\scripts\process_silhouette.ps1 -InputPath gen/tadpole_silhouette-*\1.png -OutputPath src/main/resources/assets/productivefrogs/textures/item/tadpole_silhouette.png

#
# Platform: Windows. Depends on System.Drawing, which ships in-box on
# Windows but requires libgdiplus on Linux/macOS. Other generator scripts
# in this repo (generate_variant_slime_textures.ps1, generate_slime_milk_textures.ps1)
# have the same dependency -- run all of them on Windows or set up
# libgdiplus first.

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$InputPath,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [int]$Size = 16,
    [int]$DarkThreshold = 64,
    [int]$BodyLightness = 220
)

# Fail fast on non-Windows hosts so contributors don't trip into a confusing
# Add-Type / GDI+ runtime error. PSVersionTable.Platform is "Win32NT" on
# Windows; on Linux/macOS PowerShell Core it reads "Unix". $IsWindows is the
# modern way but only set in PS Core, so guard with both.
if (($IsWindows -ne $null -and -not $IsWindows) -or ($env:OS -ne "Windows_NT")) {
    Write-Error "process_silhouette.ps1 depends on Windows System.Drawing. Install libgdiplus and unset this guard if you really want to try it on Linux/macOS."
    exit 1
}

Add-Type -AssemblyName System.Drawing

# Resolve glob if InputPath contains *.
if ($InputPath -like '*`**') {
    $matches = @(Get-ChildItem -Path $InputPath -ErrorAction SilentlyContinue)
    if ($matches.Count -eq 0) { Write-Error "No file matched $InputPath"; exit 1 }
    $InputPath = ($matches | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

if (-not (Test-Path $InputPath)) { Write-Error "Input not found: $InputPath"; exit 1 }

$srcImg = [System.Drawing.Image]::FromFile($InputPath)
try {
    # First downscale to 16x16 nearest-neighbor.
    $down = New-Object System.Drawing.Bitmap $Size, $Size
    try {
        $g = [System.Drawing.Graphics]::FromImage($down)
        try {
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $g.DrawImage($srcImg, 0, 0, $Size, $Size)
        } finally { $g.Dispose() }

        # Then tone-map: body pixels -> near-white, dark accents preserved.
        $out = New-Object System.Drawing.Bitmap $Size, $Size
        try {
            for ($y = 0; $y -lt $Size; $y++) {
                for ($x = 0; $x -lt $Size; $x++) {
                    $p = $down.GetPixel($x, $y)
                    if ($p.A -eq 0) {
                        $out.SetPixel($x, $y, $p)
                        continue
                    }
                    $maxChannel = [Math]::Max([Math]::Max($p.R, $p.G), $p.B)
                    if ($maxChannel -ge $DarkThreshold) {
                        $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $BodyLightness, $BodyLightness, $BodyLightness))
                    } else {
                        $out.SetPixel($x, $y, $p)
                    }
                }
            }

            $outDir = Split-Path $OutputPath -Parent
            if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
            $out.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "wrote $OutputPath (tone-mapped from $InputPath)"
        } finally { $out.Dispose() }
    } finally { $down.Dispose() }
} finally { $srcImg.Dispose() }
