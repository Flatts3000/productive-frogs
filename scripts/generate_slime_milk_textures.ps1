# Generate the 14 per-variant Slime Milk fluid + bucket textures by tinting
# vanilla milk and water textures with each variant's primary_color.
#
# Output:
#   src/main/resources/assets/productivefrogs/textures/item/<variant>_slime_milk_bucket.png   (14 files, 16x16)
#   src/main/resources/assets/productivefrogs/textures/block/<variant>_slime_milk_still.png   (14 files, 16x16)
#   src/main/resources/assets/productivefrogs/textures/block/<variant>_slime_milk_flow.png    (14 files, 16x16)
#
# Inputs:
# - Per-variant primary_color read from data/productivefrogs/productivefrogs/slime_variant/<name>.json
#   for the 12 resource variants (iron, copper, gold, redstone, lapis, coal,
#   diamond, emerald, prismarine, sponge, magma_cream, ender_pearl).
# - Hardcoded primary_color for the two special variants `vanilla` and
#   `magma` since they're not in the SlimeVariant datapack registry
#   (vanilla green slime + magma cube produce milk but never register a
#   variant). The values mirror in-game slime body / magma-cube colors.
# - Vanilla minecraft milk_bucket.png + bucket.png (for the bucket diff that
#   isolates milk pixels from the iron bucket pixels)
# - Vanilla minecraft water_still.png + water_flow.png (top-frame as the
#   fluid pattern base; gives slime-milk a watery ripple instead of solid fill)
# - All vanilla textures auto-extracted at runtime from the NeoForge dev
#   artifact, same pattern as generate_variant_slime_textures.ps1.
#
# Tint algorithm:
#   For each non-transparent source pixel, compute its perceived lightness
#   (max(R, G, B) / 255), then output color = lightness * tint.RGB. Preserves
#   the highlight + shadow gradient of the source while replacing the hue.
#   Alpha is passed through untouched.
#
# Bucket pipeline:
#   1. Load vanilla bucket.png (empty iron bucket - no milk).
#   2. Load vanilla milk_bucket.png (iron bucket + cream-white milk surface).
#   3. For each pixel: if the two source images agree, it's a bucket-metal
#      pixel; copy it verbatim. If they differ, it's a milk pixel; tint it.
#   This isolates the milk region without us hand-authoring a milk mask.
#
# Fluid pipeline:
#   Crop the top 16x16 frame of water_still.png / water_flow.png and apply
#   Apply-Tint to each non-transparent pixel. Apply-Tint uses the source
#   pixel's max(R,G,B) as a lightness factor and multiplies by the variant
#   tint RGB - effectively a hue swap that preserves vanilla water's
#   highlight + shadow gradient without needing an explicit grayscale pass.
#   Single-frame for V1 - the .mcmeta animation strip is deferred to polish;
#   the milk source-block isn't visually load-bearing day one.
#
# Re-run whenever a variant's primary_color changes, or when adding a new
# variant: drop the new entry into the slime_variant/ JSONs (for resource
# variants), or add a new key/value to the $variants hashtable below for
# the two specials (vanilla, magma) that don't ship a SlimeVariant entry.
#
# Platform: Windows. Same System.Drawing dependency as the slime script.

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$itemDir = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\item"
$blockDir = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\block"
$variantJsonDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\productivefrogs\slime_variant"
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"
$artifactsDir = Join-Path $repoRoot "build\moddev\artifacts"

if (-not (Test-Path (Join-Path $mcExtract "assets\minecraft\textures\block"))) {
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

# --- Source vanilla textures ---
$bucketPath = Join-Path $mcExtract "assets\minecraft\textures\item\bucket.png"
$milkBucketPath = Join-Path $mcExtract "assets\minecraft\textures\item\milk_bucket.png"
$waterStillPath = Join-Path $mcExtract "assets\minecraft\textures\block\water_still.png"
$waterFlowPath = Join-Path $mcExtract "assets\minecraft\textures\block\water_flow.png"

foreach ($p in @($bucketPath, $milkBucketPath, $waterStillPath, $waterFlowPath)) {
    if (-not (Test-Path $p)) { Write-Error "Missing vanilla texture: $p"; exit 1 }
}

# --- Variant -> color map ---
# 12 resource variants: read primary_color from each JSON.
$variants = @{}
foreach ($json in Get-ChildItem -Path $variantJsonDir -Filter "*.json") {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($json.Name)
    $parsed = Get-Content $json.FullName -Raw | ConvertFrom-Json
    $variants[$name] = [int]$parsed.primary_color
}

# 2 specials. Not in the variant registry, so hardcoded:
#   vanilla = 0x52A03A (green, mirrors vanilla slime body overlay tint)
#   magma   = 0xE36E1B (orange, mirrors magma cube body)
$variants["vanilla"] = 0x52A03A
$variants["magma"]   = 0xE36E1B

# Per-pixel tint: preserve source lightness, swap hue to target.
function Get-Lightness {
    param([System.Drawing.Color]$c)
    # Perceived lightness via max channel; matches the look-and-feel of how
    # Minecraft's runtime BlockColor tints overlay vanilla textures.
    return [int][Math]::Max([Math]::Max($c.R, $c.G), $c.B)
}

function Apply-Tint {
    param([System.Drawing.Color]$src, [int]$tintRgb)
    $tR = ($tintRgb -shr 16) -band 0xFF
    $tG = ($tintRgb -shr 8) -band 0xFF
    $tB = $tintRgb -band 0xFF
    $l = (Get-Lightness $src) / 255.0
    $r = [int][Math]::Round($tR * $l)
    $g = [int][Math]::Round($tG * $l)
    $b = [int][Math]::Round($tB * $l)
    return [System.Drawing.Color]::FromArgb($src.A, $r, $g, $b)
}

function Save-Png {
    param([System.Drawing.Bitmap]$bmp, [string]$path)
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Build-BucketTexture {
    param([string]$variant, [int]$tintRgb)
    $emptyBmp = New-Object System.Drawing.Bitmap $bucketPath
    $milkBmp  = New-Object System.Drawing.Bitmap $milkBucketPath
    try {
        $w = $milkBmp.Width
        $h = $milkBmp.Height
        $out = New-Object System.Drawing.Bitmap $w, $h
        for ($y = 0; $y -lt $h; $y++) {
            for ($x = 0; $x -lt $w; $x++) {
                $mp = $milkBmp.GetPixel($x, $y)
                if ($mp.A -eq 0) { continue }
                # If empty-bucket has a non-transparent pixel here AND the
                # RGB matches, it's bucket-metal; pass through unchanged.
                if ($x -lt $emptyBmp.Width -and $y -lt $emptyBmp.Height) {
                    $ep = $emptyBmp.GetPixel($x, $y)
                    if ($ep.A -ne 0 -and $ep.R -eq $mp.R -and $ep.G -eq $mp.G -and $ep.B -eq $mp.B) {
                        $out.SetPixel($x, $y, $mp)
                        continue
                    }
                }
                # Otherwise it's a milk pixel -- tint it.
                $out.SetPixel($x, $y, (Apply-Tint $mp $tintRgb))
            }
        }
        # Slime eyes overlay: two dark pixels written AFTER the tint loop
        # has finished, so they bypass Apply-Tint entirely and ship at their
        # literal (28,28,28) value on every variant. The eye positions are
        # chosen against the vanilla milk_bucket.png milk shape -- y=3 is the
        # top of the widest milk band; x=6 / x=9 symmetric around center give
        # vanilla-slime-style 2-pixel eye spacing.
        $eyeColor = [System.Drawing.Color]::FromArgb(255, 28, 28, 28)
        $out.SetPixel(6, 3, $eyeColor)
        $out.SetPixel(9, 3, $eyeColor)

        try {
            $outPath = Join-Path $itemDir "${variant}_slime_milk_bucket.png"
            Save-Png $out $outPath
            Write-Output "wrote $outPath"
        } finally { $out.Dispose() }
    } finally {
        $emptyBmp.Dispose()
        $milkBmp.Dispose()
    }
}

function Build-FluidTexture {
    param([string]$variant, [int]$tintRgb, [string]$sourcePath, [string]$kind)
    $src = New-Object System.Drawing.Bitmap $sourcePath
    try {
        # Animated multi-frame strip: copy the FULL vanilla water_still /
        # water_flow vertical strip (32 frames each) and tint every pixel.
        # Sibling .mcmeta is emitted below to drive the animation cadence.
        $w = $src.Width
        $h = $src.Height
        $out = New-Object System.Drawing.Bitmap $w, $h
        try {
            for ($y = 0; $y -lt $h; $y++) {
                for ($x = 0; $x -lt $w; $x++) {
                    $p = $src.GetPixel($x, $y)
                    if ($p.A -eq 0) {
                        # Explicit transparent write so we don't rely on
                        # the Bitmap ctor's initial contents (technically
                        # implementation-defined). Matches vanilla water
                        # alpha which is fully opaque, but keeps the loop
                        # invariant clear if vanilla ever ships transparent
                        # pixels in these strips.
                        $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                        continue
                    }
                    $out.SetPixel($x, $y, (Apply-Tint $p $tintRgb))
                }
            }
            $outPath = Join-Path $blockDir "${variant}_slime_milk_${kind}.png"
            Save-Png $out $outPath
            Write-Output "wrote $outPath ($w x $h)"
            # Sibling .mcmeta declaring the animation. frametime=2 matches
            # vanilla water_still.png.mcmeta's cadence. Width/height left
            # implicit so MC auto-detects frame size = texture width.
            $mcmetaPath = "$outPath.mcmeta"
            [System.IO.File]::WriteAllText($mcmetaPath, "{`n  `"animation`": {`n    `"frametime`": 2`n  }`n}`n", [System.Text.UTF8Encoding]::new($false))
            Write-Output "wrote $mcmetaPath"
        } finally { $out.Dispose() }
    } finally { $src.Dispose() }
}

foreach ($variant in ($variants.Keys | Sort-Object)) {
    $tint = $variants[$variant]
    Build-BucketTexture -variant $variant -tintRgb $tint
    Build-FluidTexture -variant $variant -tintRgb $tint -sourcePath $waterStillPath -kind "still"
    Build-FluidTexture -variant $variant -tintRgb $tint -sourcePath $waterFlowPath -kind "flow"
}

Write-Output "done -- 14 variants x (1 bucket + 1 still + 1 flow) = 42 PNGs"
