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

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$InputPath,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [int]$Size = 16,
    [int]$DarkThreshold = 64,
    [int]$BodyLightness = 220
)

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
    $g = [System.Drawing.Graphics]::FromImage($down)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $g.DrawImage($srcImg, 0, 0, $Size, $Size)
    } finally { $g.Dispose() }

    # Then tone-map: body pixels -> near-white, dark accents preserved.
    $out = New-Object System.Drawing.Bitmap $Size, $Size
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
    $down.Dispose()

    $outDir = Split-Path $OutputPath -Parent
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $out.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "wrote $OutputPath (tone-mapped from $InputPath)"
} finally { $srcImg.Dispose() }
