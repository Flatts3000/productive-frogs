# Generate the Slime Churn GUI background by compositing vanilla Minecraft
# furnace.png into the Churn's 2x2 slot layout.
#
# Output:
#   src/main/resources/assets/productivefrogs/textures/gui/container/slime_churn.png
#
# Source: vanilla minecraft assets/minecraft/textures/gui/container/furnace.png
#   (256x256 PNG, 176x166 GUI region top-left)
#
# The Churn keeps vanilla's two left wells - milk bucket = vanilla input
# (56,17), empty buckets = vanilla fuel (56,53) - and ADDS a matching right
# column: slime-bucket output at (116,17), spent-container output at (116,53).
# Vanilla's big result well at (116,35) and the burn-flame outline between the
# left slots are both erased (the Churn has no flame; the arrow at (79,34)
# fills with the spawn-interval progress instead).
#
# Slot click targets in SlimeChurnMenu:
#   MILK (56,17)  BUCKETS (56,53)  SLIME_OUT (116,17)  EMPTY_OUT (116,53)
#
# Sprite re-inlining: MC 1.21.x moved the furnace arrow into a sprite-atlas
# entry; SlimeChurnScreen blits it from the background, so re-inline
# burn_progress (arrow, 24x16) at (176, 14). No flame needed.

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"
$outPath = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\gui\container\slime_churn.png"

if (-not (Test-Path (Join-Path $mcExtract "assets\minecraft\textures\gui\container"))) {
    $artifactsDir = Join-Path $repoRoot "build\moddev\artifacts"
    $jarMatches = @(Get-ChildItem -Path $artifactsDir -Filter "neoforge-*-client-extra-aka-minecraft-resources.jar" -ErrorAction SilentlyContinue)
    if ($jarMatches.Count -eq 0) {
        Write-Error "No NeoForge minecraft-resources jar found under $artifactsDir. Run .\gradlew createMinecraftArtifacts then re-run."
        exit 1
    }
    $jar = ($jarMatches | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    New-Item -ItemType Directory -Force -Path $mcExtract | Out-Null
    Write-Output "extracting $(Split-Path $jar -Leaf) -> $mcExtract"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jar, $mcExtract)
}

$furnacePath = Join-Path $mcExtract "assets\minecraft\textures\gui\container\furnace.png"
if (-not (Test-Path $furnacePath)) { Write-Error "Missing vanilla furnace.png at $furnacePath"; exit 1 }

$src = New-Object System.Drawing.Bitmap $furnacePath
try {
    $out = New-Object System.Drawing.Bitmap $src.Width, $src.Height
    $g = [System.Drawing.Graphics]::FromImage($out)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $g.DrawImage($src, 0, 0, $src.Width, $src.Height)

        # Flat-grey eraser: sample an 8x8 background tile from a known-flat
        # area (left of the slots, above the inventory label) and tile it over
        # regions we want gone.
        $tile = New-Object System.Drawing.Bitmap 8, 8
        $tileGfx = [System.Drawing.Graphics]::FromImage($tile)
        try {
            $tileGfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $tileGfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $srcRect = New-Object System.Drawing.Rectangle 30, 60, 8, 8
            $dstRect = New-Object System.Drawing.Rectangle 0, 0, 8, 8
            $tileGfx.DrawImage($src, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
        } finally { $tileGfx.Dispose() }

        # Tile the eraser across the rect, CLAMPED to the rect bounds - a
        # naive 8px step overshoots up to 7px past the rect edge, which is
        # exactly how v1 of this script wiped the top rows of the lower-left
        # slot well (the in-game "truncated slot" bug).
        function Erase-Region([System.Drawing.Graphics]$gfx, [System.Drawing.Bitmap]$t, [int]$x, [int]$y, [int]$w, [int]$h) {
            for ($ty = $y; $ty -lt ($y + $h); $ty += 8) {
                $th = [Math]::Min(8, $y + $h - $ty)
                for ($tx = $x; $tx -lt ($x + $w); $tx += 8) {
                    $tw = [Math]::Min(8, $x + $w - $tx)
                    $dstRect = New-Object System.Drawing.Rectangle $tx, $ty, $tw, $th
                    $srcRect = New-Object System.Drawing.Rectangle 0, 0, $tw, $th
                    $gfx.DrawImage($t, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
                }
            }
        }

        # Erase the vanilla 26x26 result well at (111,30) and the burn-flame
        # outline between the left slots (~(56,36) 14x14). The flame rect
        # stops at y=50 so it can never clip the fuel well that starts at 51.
        Erase-Region $g $tile 108 28 32 32
        Erase-Region $g $tile 54 34 18 16

        # Copy the 20x20 input-slot well (frame + bevel) from vanilla's input
        # position (54,15).
        $slot = New-Object System.Drawing.Bitmap 20, 20
        $slotGfx = [System.Drawing.Graphics]::FromImage($slot)
        try {
            $slotGfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $slotGfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $srcRect = New-Object System.Drawing.Rectangle 54, 15, 20, 20
            $dstRect = New-Object System.Drawing.Rectangle 0, 0, 20, 20
            $slotGfx.DrawImage($src, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
        } finally { $slotGfx.Dispose() }

        # Right column: slime output (114,15) + spent-container output (114,51),
        # mirroring the left column's input (54,15) + fuel (54,51) wells. The
        # left-bottom well is re-pasted too, so the layout is correct even if
        # an erase rect ever grazes it again.
        $g.DrawImage($slot, 114, 15, 20, 20)
        $g.DrawImage($slot, 114, 51, 20, 20)
        $g.DrawImage($slot, 54, 51, 20, 20)
        $slot.Dispose()
        $tile.Dispose()

        # Re-inline the cook-progress arrow (burn_progress, 24x16) at (176, 14).
        $arrowPath = Join-Path $mcExtract "assets\minecraft\textures\gui\sprites\container\furnace\burn_progress.png"
        if (Test-Path $arrowPath) {
            $arrow = New-Object System.Drawing.Bitmap $arrowPath
            try { $g.DrawImage($arrow, 176, 14, $arrow.Width, $arrow.Height) } finally { $arrow.Dispose() }
        } else {
            Write-Warning "Vanilla furnace burn_progress sprite not found at $arrowPath; progress arrow will be empty."
        }
    } finally { $g.Dispose() }

    $outDir = Split-Path $outPath -Parent
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "wrote $outPath"
} finally { $src.Dispose() }

Write-Output "done -- Slime Churn GUI composed from vanilla furnace.png (2x2 wells, result well + flame erased, arrow re-inlined)"
