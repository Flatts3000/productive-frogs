# Generate the Slime Milker GUI background by compositing vanilla
# Minecraft furnace.png with the fuel slot painted out.
#
# Output:
#   src/main/resources/assets/productivefrogs/textures/gui/container/slime_milker.png
#
# Source: vanilla minecraft assets/minecraft/textures/gui/container/furnace.png
#   (256x256 PNG, 176x166 GUI region top-left)
#
# The Slime Milker has no fuel slot - the slime IS the input (consumed during
# the cook), so the fuel-slot well at approximately (55, 52) -> (71, 68) on
# vanilla furnace.png is overwritten with the surrounding gray background
# color (RGB 198/198/198 = vanilla's GUI panel gray).
#
# Slot click targets in SlimeMilkerMenu:
#   INPUT_SLOT_X=56, INPUT_SLOT_Y=35  (recentred vertically against output)
#   OUTPUT_SLOT_X=116, OUTPUT_SLOT_Y=35  (vanilla furnace result-slot position)
# Arrow at (79, 34) - same as vanilla. The arrow sprite at (176, 14) on the
# vanilla PNG is reused unchanged.
#
# We override vanilla furnace's stacked input/fuel column (input at y=17,
# fuel at y=53) and instead present ONE input slot aligned vertically with
# the output (both at y=35). The vanilla input slot well at (54, 15)-(73, 34)
# is painted out and re-emitted at (54, 33)-(73, 52) so the slot frame
# visually matches the item position.
#
# Vanilla composite over a hand-painted GUI: the Java screen renders the
# title at runtime via Component.literal, so the PNG only needs to be the
# container chrome. Vanilla furnace chrome is exactly the right shape and
# stays visually consistent with the rest of the inventory UX.

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"
$outPath = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\gui\container\slime_milker.png"

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

# Load vanilla furnace as the base.
$src = New-Object System.Drawing.Bitmap $furnacePath
try {
    $out = New-Object System.Drawing.Bitmap $src.Width, $src.Height
    $g = [System.Drawing.Graphics]::FromImage($out)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $g.DrawImage($src, 0, 0, $src.Width, $src.Height)

        # Vanilla GUI panel gray. Sampled from the empty area at the top
        # of the furnace GUI - the standard Minecraft container panel color.
        $panelGray = [System.Drawing.Color]::FromArgb(255, 198, 198, 198)
        $brush = New-Object System.Drawing.SolidBrush $panelGray
        try {
            # Move the input slot frame from the vanilla furnace y=17 position
            # down to y=35 so it aligns vertically with the output slot. Copy
            # the existing 20x20 input slot region (including the bevel /
            # shadow) from (54, 15) to (54, 33) first, then erase the
            # original location plus the fuel-system column underneath.
            #
            # Region copy chain:
            # 1. Sample (54, 15) - (73, 34) - 20x20 input slot well + bevel.
            # 2. Paste at (54, 33) - (73, 52) - new aligned position.
            # 3. Paint over (54, 15) - (73, 69) with panelGray to erase the
            #    original input position, the burn-flame indicator at
            #    (54, 35)-(62, 51), and the fuel slot well at (54, 51)-(72, 69).
            #    Single 20x55 fill covers everything.
            $inputSlot = New-Object System.Drawing.Bitmap 20, 20
            try {
                $slotGfx = [System.Drawing.Graphics]::FromImage($inputSlot)
                try {
                    $slotGfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                    $slotGfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                    $srcRect = New-Object System.Drawing.Rectangle 54, 15, 20, 20
                    $dstRect = New-Object System.Drawing.Rectangle 0, 0, 20, 20
                    $slotGfx.DrawImage($src, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
                } finally { $slotGfx.Dispose() }
                $g.FillRectangle($brush, 54, 15, 20, 55)
                $g.DrawImage($inputSlot, 54, 33, 20, 20)
            } finally { $inputSlot.Dispose() }
        } finally { $brush.Dispose() }

        # Composite the arrow progress sprite into (176, 14) so the
        # SlimeMilkerScreen blit works. In MC 1.21.x vanilla split the
        # furnace arrow into a sprite-atlas entry at
        # gui/sprites/container/furnace/burn_progress.png (24x16) rather
        # than keeping it inline in the GUI background. Our screen blits
        # from the background at (176, 14) — so we have to re-inline it.
        $arrowPath = Join-Path $mcExtract "assets\minecraft\textures\gui\sprites\container\furnace\burn_progress.png"
        if (Test-Path $arrowPath) {
            $arrow = New-Object System.Drawing.Bitmap $arrowPath
            try {
                $g.DrawImage($arrow, 176, 14, $arrow.Width, $arrow.Height)
            } finally { $arrow.Dispose() }
        } else {
            Write-Warning "Vanilla furnace burn_progress sprite not found at $arrowPath; progress arrow will be empty."
        }

        # Restore the inner shadow on the slot positions we KEEP (input + output)
        # by leaving them untouched. The original furnace's input and output
        # well shadows are preserved by virtue of only overwriting the fuel slot.
    } finally { $g.Dispose() }

    $outDir = Split-Path $outPath -Parent
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "wrote $outPath"
} finally { $src.Dispose() }

Write-Output "done -- Slime Milker GUI composed from vanilla furnace.png (fuel slot painted out)"
