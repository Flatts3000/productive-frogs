# Generate the Spawnery GUI background by compositing vanilla Minecraft
# furnace.png with a second top slot (the primer slot) added.
#
# Output:
#   src/main/resources/assets/productivefrogs/textures/gui/container/spawnery.png
#
# Source: vanilla minecraft assets/minecraft/textures/gui/container/furnace.png
#   (256x256 PNG, 176x166 GUI region top-left)
#
# The Spawnery keeps the vanilla furnace's three wells - bottle = vanilla input
# (56,17), fuel = vanilla fuel (56,53), output = vanilla result (116,35) - plus
# the cook-progress arrow at (79,34) and the burn flame above the fuel. It ADDS
# one new well: the primer slot at (116,17), directly above the output.
#
# Slot click targets in SpawneryMenu:
#   BOTTLE (56,17)  FUEL (56,53)  PRIMER (116,17)  OUTPUT (116,35)
#
# Sprite re-inlining: in MC 1.21.x vanilla moved the furnace flame and arrow out
# of furnace.png into sprite-atlas entries. SpawneryScreen blits both from the
# background, so we re-inline:
#   - lit_progress  (flame, 14x14) at (176, 0)
#   - burn_progress (arrow, 24x16) at (176, 14)

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"
$outPath = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\gui\container\spawnery.png"

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

        # Add the primer slot well at (116,17): copy the 20x20 input-slot well
        # (frame + bevel) from vanilla's input position (54,15) and paste it at
        # (114,15) so the slot frame sits directly above the output slot.
        $slot = New-Object System.Drawing.Bitmap 20, 20
        try {
            $slotGfx = [System.Drawing.Graphics]::FromImage($slot)
            try {
                $slotGfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $slotGfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                $srcRect = New-Object System.Drawing.Rectangle 54, 15, 20, 20
                $dstRect = New-Object System.Drawing.Rectangle 0, 0, 20, 20
                $slotGfx.DrawImage($src, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
            } finally { $slotGfx.Dispose() }
            $g.DrawImage($slot, 114, 15, 20, 20)
        } finally { $slot.Dispose() }

        # Re-inline the burn flame (lit_progress, 14x14) at (176, 0).
        $flamePath = Join-Path $mcExtract "assets\minecraft\textures\gui\sprites\container\furnace\lit_progress.png"
        if (Test-Path $flamePath) {
            $flame = New-Object System.Drawing.Bitmap $flamePath
            try { $g.DrawImage($flame, 176, 0, $flame.Width, $flame.Height) } finally { $flame.Dispose() }
        } else {
            Write-Warning "Vanilla furnace lit_progress sprite not found at $flamePath; burn flame will be empty."
        }

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

Write-Output "done -- Spawnery GUI composed from vanilla furnace.png (primer slot added, flame + arrow re-inlined)"
