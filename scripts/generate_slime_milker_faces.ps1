# Generate the 3 Slime Milker block-face textures by compositing vanilla
# Minecraft block textures.
#
# Output:
#   src/main/resources/assets/productivefrogs/textures/block/slime_milker_top.png
#   src/main/resources/assets/productivefrogs/textures/block/slime_milker_side.png
#   src/main/resources/assets/productivefrogs/textures/block/slime_milker_bottom.png
#
# Block model is minecraft:block/cube_bottom_top — top/bottom/side, no front
# (the Slime Milker has no facing direction in V1).
#
# Per-face composite source:
#   top    = dispenser_front_vertical.png    — vanilla dispenser face has a
#            centered recessed dark slot that reads exactly like "bucket goes
#            in here." Center pixels are tinted slime-green to hint at the
#            captured fluid below.
#   side   = blast_furnace_side.png          — vertical metal banding /
#            riveting reads industrial. Used straight, no tint changes.
#   bottom = iron_block.png                  — plain iron underside, matching
#            the rest of the machine's metal palette.
#
# Why composites instead of AI generation: V1 visual scope is "blocks that
# read as vanilla-adjacent appliances," not bespoke pixel art. Pure composite
# guarantees palette / grid consistency with vanilla and ships ready-to-use
# without AI artifacts. The Slime Milker GUI background is the only Tier B
# surface that needs AI — see generate_slime_milker_gui.ps1 (when shipped).
#
# Tint algorithm for the top-face slime-fluid hint: pixels in the 6x6 inner
# slot region (anchored at the dark spot of the dispenser texture) get their
# RGB multiplied by a slime-green tint. The vanilla dispenser slot is already
# near-black, so the multiplication yields a desaturated dark-green that
# reads as "slime fluid in the slot" without overpowering the metal frame.

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$blockDir = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\block"
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"

# Extract MC client jar if not already cached. Same pattern as the slime /
# milk texture generators.
if (-not (Test-Path (Join-Path $mcExtract "assets\minecraft\textures\block"))) {
    $artifactsDir = Join-Path $repoRoot "build\moddev\artifacts"
    $jarMatches = @(Get-ChildItem -Path $artifactsDir -Filter "neoforge-*-client-extra-aka-minecraft-resources.jar" -ErrorAction SilentlyContinue)
    if ($jarMatches.Count -eq 0) {
        Write-Error ("No NeoForge minecraft-resources jar found under $artifactsDir.`n" +
            "Run ``./gradlew createMinecraftArtifacts`` (or any task that pulls deps) to populate it, then re-run this script.")
        exit 1
    }
    $jar = ($jarMatches | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    New-Item -ItemType Directory -Force -Path $mcExtract | Out-Null
    Write-Output "extracting $(Split-Path $jar -Leaf) -> $mcExtract"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jar, $mcExtract)
}

$dispenserPath = Join-Path $mcExtract "assets\minecraft\textures\block\dispenser_front_vertical.png"
$blastSidePath = Join-Path $mcExtract "assets\minecraft\textures\block\blast_furnace_side.png"
$ironBlockPath = Join-Path $mcExtract "assets\minecraft\textures\block\iron_block.png"

foreach ($p in @($dispenserPath, $blastSidePath, $ironBlockPath)) {
    if (-not (Test-Path $p)) { Write-Error "Missing vanilla texture: $p"; exit 1 }
}

# Slime-green tint for the inner slot. RGB chosen to match vanilla slime body
# overlay (#52A03A), same value used for the `vanilla` slime milk variant.
$slotTint = [System.Drawing.Color]::FromArgb(255, 0x52, 0xA0, 0x3A)

function Save-Png {
    param([System.Drawing.Bitmap]$bmp, [string]$path)
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

# Copy vanilla bottom + side faces unchanged.
function Copy-VanillaFace {
    param([string]$sourcePath, [string]$outName)
    $bmp = New-Object System.Drawing.Bitmap $sourcePath
    try {
        $outPath = Join-Path $blockDir $outName
        Save-Png $bmp $outPath
        Write-Output "wrote $outPath"
    } finally { $bmp.Dispose() }
}

# Build the top face: vanilla dispenser front + slime-green multiply over the
# centered dark slot.
function Build-TopFace {
    $src = New-Object System.Drawing.Bitmap $dispenserPath
    try {
        $w = $src.Width
        $h = $src.Height
        $out = New-Object System.Drawing.Bitmap $w, $h
        # Anchor the slime-green slot at the center 6x6 of a 16x16 texture.
        # The dispenser slot's darkest pixels live in that region; tinting
        # only those preserves the metal frame around the rim.
        $slotMinX = 5; $slotMaxX = 10
        $slotMinY = 5; $slotMaxY = 10
        for ($y = 0; $y -lt $h; $y++) {
            for ($x = 0; $x -lt $w; $x++) {
                $p = $src.GetPixel($x, $y)
                if ($p.A -eq 0) {
                    $out.SetPixel($x, $y, $p)
                    continue
                }
                $inSlotRegion = ($x -ge $slotMinX -and $x -le $slotMaxX -and $y -ge $slotMinY -and $y -le $slotMaxY)
                # Only tint pixels that are "dark enough" to be the slot
                # interior (lightness threshold ~ 60). Keeps the metal rim
                # untouched even where the slot region overlaps it.
                $l = [int][Math]::Max([Math]::Max($p.R, $p.G), $p.B)
                if ($inSlotRegion -and $l -lt 90) {
                    $factor = $l / 255.0
                    $r = [int][Math]::Round($slotTint.R * $factor)
                    $g = [int][Math]::Round($slotTint.G * $factor)
                    $b = [int][Math]::Round($slotTint.B * $factor)
                    $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $r, $g, $b))
                } else {
                    $out.SetPixel($x, $y, $p)
                }
            }
        }
        $outPath = Join-Path $blockDir "slime_milker_top.png"
        Save-Png $out $outPath
        Write-Output "wrote $outPath"
        $out.Dispose()
    } finally { $src.Dispose() }
}

Build-TopFace
Copy-VanillaFace -sourcePath $blastSidePath -outName "slime_milker_side.png"
Copy-VanillaFace -sourcePath $ironBlockPath -outName "slime_milker_bottom.png"

Write-Output "done -- 3 Slime Milker block faces generated"
