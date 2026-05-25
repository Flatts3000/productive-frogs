# Generate the per-variant Slime Milk fluid + bucket textures by tinting
# vanilla milk and water textures with each variant's primary_color.
#
# Output (one set per variant in the slime_variant registry + 2 specials):
#   src/main/resources/assets/productivefrogs/textures/item/<variant>_slime_milk_bucket.png
#   src/main/resources/assets/productivefrogs/textures/block/<variant>_slime_milk_still.png  (+ .mcmeta)
#   src/main/resources/assets/productivefrogs/textures/block/<variant>_slime_milk_flow.png   (+ .mcmeta)
#
# Inputs:
# - Per-variant primary_color read from data/productivefrogs/productivefrogs/slime_variant/<name>.json
#   for every shipped resource variant (auto-discovered - no hardcoded list, so
#   adding a slime_variant JSON automatically grows the output set).
# - Hardcoded primary_color for the two special variants `vanilla` and
#   `magma` since they're not in the SlimeVariant datapack registry
#   (vanilla green slime + magma cube produce milk but never register a
#   variant). The values mirror in-game slime body / magma-cube colors.
# - Vanilla minecraft milk_bucket.png + bucket.png (for the bucket diff that
#   isolates milk pixels from the iron bucket pixels)
# - Vanilla minecraft water_still.png + water_flow.png (the animated strip as
#   the fluid pattern base; gives slime-milk a watery ripple instead of solid fill)
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
#   Tint every pixel of the full vanilla water_still / water_flow animation
#   strip and emit a sibling .mcmeta to drive the animation cadence.
#
# Performance + reliability note:
#   The per-pixel work runs in a compiled Add-Type helper using LockBits over a
#   raw byte buffer, NOT PowerShell GetPixel/SetPixel. The interpreted per-pixel
#   path churned millions of marshaled GDI+ calls and corrupted the PS 5.1
#   engine (AccessViolationException) once the variant count grew past ~14. The
#   compiled LockBits path is robust and ~100x faster while producing
#   byte-identical output (same max-channel lightness math, same banker's
#   rounding, same PNG encoder).
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
# Every resource variant: read primary_color from each JSON (auto-discovered).
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

# --- Compiled pixel core (LockBits, not GetPixel/SetPixel) ---
# Same tint math as the original interpreted path; runs in compiled code so the
# millions of per-pixel ops don't corrupt the PS 5.1 engine under high variant
# counts. See the "Performance + reliability note" in the header.
$tinterCode = @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class MilkTinter {
    // Load a PNG. We LockBits it as Format32bppArgb directly (no Graphics
    // redraw): the vanilla source PNGs are already 32bppArgb, so locking in
    // that format is a zero-conversion read whose bytes match a GetPixel scan
    // exactly. A Graphics.DrawImage redraw would alpha-round the translucent
    // water pixels by +-1 and drift from the original interpreted output.
    static Bitmap Load32(string path) {
        return new Bitmap(path);
    }

    static byte[] Read(Bitmap bmp, out int stride) {
        var rect = new Rectangle(0, 0, bmp.Width, bmp.Height);
        var d = bmp.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        stride = d.Stride;
        byte[] buf = new byte[d.Stride * bmp.Height];
        Marshal.Copy(d.Scan0, buf, 0, buf.Length);
        bmp.UnlockBits(d);
        return buf;
    }

    static void Write(Bitmap bmp, byte[] buf) {
        var rect = new Rectangle(0, 0, bmp.Width, bmp.Height);
        var d = bmp.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(buf, 0, d.Scan0, buf.Length);
        bmp.UnlockBits(d);
    }

    // Format32bppArgb byte order in memory is B, G, R, A.
    static int Light(byte r, byte g, byte b) {
        int m = r; if (g > m) m = g; if (b > m) m = b; return m;
    }

    public static void TintFluid(string srcPath, string dstPath, int tintRgb) {
        using (var bmp = Load32(srcPath)) {
            int stride; byte[] buf = Read(bmp, out stride);
            int tR = (tintRgb >> 16) & 0xFF, tG = (tintRgb >> 8) & 0xFF, tB = tintRgb & 0xFF;
            for (int i = 0; i < buf.Length; i += 4) {
                byte b = buf[i], g = buf[i + 1], r = buf[i + 2], a = buf[i + 3];
                if (a == 0) { buf[i] = 0; buf[i + 1] = 0; buf[i + 2] = 0; buf[i + 3] = 0; continue; }
                double l = Light(r, g, b) / 255.0;
                buf[i]     = (byte)Math.Round(tB * l);
                buf[i + 1] = (byte)Math.Round(tG * l);
                buf[i + 2] = (byte)Math.Round(tR * l);
                // alpha (buf[i + 3]) passed through untouched
            }
            Write(bmp, buf);
            bmp.Save(dstPath, ImageFormat.Png);
        }
    }

    public static void TintBucket(string emptyPath, string milkPath, string dstPath, int tintRgb) {
        using (var empty = Load32(emptyPath))
        using (var milk = Load32(milkPath)) {
            int ms; byte[] mb = Read(milk, out ms);
            int es; byte[] eb = Read(empty, out es);
            int ew = empty.Width, eh = empty.Height, w = milk.Width, h = milk.Height;
            int tR = (tintRgb >> 16) & 0xFF, tG = (tintRgb >> 8) & 0xFF, tB = tintRgb & 0xFF;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * ms + x * 4;
                    byte b = mb[i], g = mb[i + 1], r = mb[i + 2], a = mb[i + 3];
                    if (a == 0) { mb[i] = 0; mb[i + 1] = 0; mb[i + 2] = 0; mb[i + 3] = 0; continue; }
                    bool metal = false;
                    if (x < ew && y < eh) {
                        int j = y * es + x * 4;
                        if (eb[j + 3] != 0 && eb[j + 2] == r && eb[j + 1] == g && eb[j] == b) metal = true;
                    }
                    if (metal) continue; // bucket-metal pixel: keep verbatim
                    double l = Light(r, g, b) / 255.0;
                    mb[i]     = (byte)Math.Round(tB * l);
                    mb[i + 1] = (byte)Math.Round(tG * l);
                    mb[i + 2] = (byte)Math.Round(tR * l);
                }
            }
            Write(milk, mb);
            milk.Save(dstPath, ImageFormat.Png);
        }
    }
}
'@
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition $tinterCode

function Build-BucketTexture {
    param([string]$variant, [int]$tintRgb)
    # NOTE: No eye overlay on milk buckets. Slime Milk is the extracted fluid,
    # not the live slime -- eyes belong on the Slime Bucket (the bucketed-slime
    # surface). The milk bucket should read as "bucket of tinted liquid."
    $outPath = Join-Path $itemDir "${variant}_slime_milk_bucket.png"
    [MilkTinter]::TintBucket($bucketPath, $milkBucketPath, $outPath, $tintRgb)
    Write-Output "wrote $outPath"
}

function Build-FluidTexture {
    param([string]$variant, [int]$tintRgb, [string]$sourcePath, [string]$kind)
    $outPath = Join-Path $blockDir "${variant}_slime_milk_${kind}.png"
    [MilkTinter]::TintFluid($sourcePath, $outPath, $tintRgb)
    Write-Output "wrote $outPath"
    # Sibling .mcmeta declaring the animation. frametime=2 matches vanilla
    # water_still.png.mcmeta's cadence. Width/height left implicit so MC
    # auto-detects frame size = texture width.
    $mcmetaPath = "$outPath.mcmeta"
    [System.IO.File]::WriteAllText($mcmetaPath, "{`n  `"animation`": {`n    `"frametime`": 2`n  }`n}`n", [System.Text.UTF8Encoding]::new($false))
}

# Ensure output dirs exist. The compiled MilkTinter writes via Bitmap.Save,
# which (unlike the old Save-Png helper) does not create parent directories, so
# the script would throw on save in a fresh checkout / alternate output root.
New-Item -ItemType Directory -Force -Path $itemDir, $blockDir | Out-Null

foreach ($variant in ($variants.Keys | Sort-Object)) {
    $tint = $variants[$variant]
    Build-BucketTexture -variant $variant -tintRgb $tint
    Build-FluidTexture -variant $variant -tintRgb $tint -sourcePath $waterStillPath -kind "still"
    Build-FluidTexture -variant $variant -tintRgb $tint -sourcePath $waterFlowPath -kind "flow"
}

Write-Output "done -- $($variants.Count) variants x (1 bucket + 1 still + 1 flow) PNGs"
