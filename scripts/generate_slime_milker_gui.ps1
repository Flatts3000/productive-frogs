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
# Slot click targets in SlimeMilkerMenu must match vanilla furnace positions:
#   INPUT_SLOT_X=56, INPUT_SLOT_Y=17
#   OUTPUT_SLOT_X=112, OUTPUT_SLOT_Y=30
# Arrow at (79, 34) - same as vanilla. The arrow sprite at (176, 14) on the
# vanilla PNG is reused unchanged.
#
# Why vanilla composite rather than AI gen: GUI text rendering on gpt-image-1
# is unreliable (it mangled "Slime Milker" into "Slivve Hilk jar"). The Java
# screen renders the title at runtime via Component.literal anyway, so the
# PNG just needs to be the container chrome. Vanilla is the best chrome.

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
            # Paint out two vanilla fuel-system artifacts that the milker
            # doesn't use:
            #
            # 1. The fuel slot well at approximately (54, 51) to (72, 69) -
            #    19x19 region. Slime milker has no fuel slot.
            #
            # 2. The burn-flame indicator above the fuel slot at approximately
            #    (54, 35) to (62, 51) - the vertical "wavy flame outline"
            #    pixels that vanilla draws as the empty indicator and overlays
            #    a flame sprite on top of when burning. No fuel = no burn
            #    indicator, so this column gets erased too.
            #
            # Combined paint region: (54, 35) to (72, 70) - a single 19x35
            # vertical strip covering both artifacts.
            $g.FillRectangle($brush, 54, 35, 19, 35)
        } finally { $brush.Dispose() }

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
