# Builds an HTML comparison page at gen/comparison.html that shows, for each
# texture surface, all known candidates side-by-side at 1x, 4x, 8x, 16x zoom.
#
# Sources scanned:
#   1. src/main/resources/assets/productivefrogs/textures/  -> shipped versions
#      (the current asset that would ship if you merged the branch right now)
#   2. gen/<slug>-<timestamp>/<idx>.png  -> AI/script candidates pending review
#      (each subdir is one batch; multiple batches per slug get grouped)
#
# Output: a static HTML file with no JS dependencies. Open in browser to
# review. To re-run after new generations land, just call this script again.
#
# Per-surface row layout:
#   [surface name]   [1x raw]  [4x]  [8x]  [16x]   |   source label
#
# Pixel-perfect rendering: image-rendering: pixelated CSS ensures the
# browser does nearest-neighbor upscaling rather than blurring.

[CmdletBinding()]
param()

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$assetsRoot = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures"
$genRoot = Join-Path $repoRoot "gen"
$outHtml = Join-Path $genRoot "comparison.html"
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"

if (-not (Test-Path $genRoot)) { New-Item -ItemType Directory -Force -Path $genRoot | Out-Null }

# Populate the vanilla MC texture cache if missing -- same pattern as the
# generator scripts. Without this, the vanilla reference cards on the
# comparison page silently disappear on a fresh machine / temp dir.
if (-not (Test-Path (Join-Path $mcExtract "assets\minecraft\textures\block"))) {
    $artifactsDir = Join-Path $repoRoot "build\moddev\artifacts"
    $jarMatches = @(Get-ChildItem -Path $artifactsDir -Filter "neoforge-*-client-extra-aka-minecraft-resources.jar" -ErrorAction SilentlyContinue)
    if ($jarMatches.Count -gt 0) {
        $jar = ($jarMatches | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
        New-Item -ItemType Directory -Force -Path $mcExtract | Out-Null
        Write-Output "extracting $(Split-Path $jar -Leaf) -> $mcExtract"
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($jar, $mcExtract)
    } else {
        Write-Warning "No NeoForge minecraft-resources jar under $artifactsDir -- vanilla reference cards will be skipped. Run ./gradlew createMinecraftArtifacts to populate the cache."
    }
}

# Surfaces under active comparison. Each entry maps a slug to the asset path
# it would replace if approved, plus a list of vanilla Minecraft texture paths
# that serve as visual style references. Extend as new surfaces enter the
# PoC sweep. Vanilla refs are looked up under the extracted mc-extra cache.
$surfaces = @(
    @{
        slug = "slime_milker_top"
        assetPath = "block\slime_milker_top.png"
        vanillaRefs = @("block\cobblestone.png", "block\oak_planks.png", "block\dispenser_front_vertical.png")
    },
    @{
        slug = "slime_milker_side"
        assetPath = "block\slime_milker_side.png"
        vanillaRefs = @("block\cobblestone.png", "block\oak_planks.png", "block\blast_furnace_side.png")
    },
    @{
        slug = "slime_milker_bottom"
        assetPath = "block\slime_milker_bottom.png"
        vanillaRefs = @("block\cobblestone.png", "block\stone.png")
    },
    @{
        slug = "slime_milker_front"
        assetPath = "block\slime_milker_front.png"
        vanillaRefs = @("block\cobblestone.png", "block\oak_planks.png", "block\oak_door_bottom.png")
    },
    @{
        slug = "slime_milker_top_working"
        assetPath = "block\slime_milker_top_working.png"
        vanillaRefs = @("block\cobblestone.png", "block\oak_planks.png")
    },
    @{
        slug = "slime_milker_side_working"
        assetPath = "block\slime_milker_side_working.png"
        vanillaRefs = @("block\cobblestone.png", "block\oak_planks.png")
    },
    @{
        slug = "slime_milker_front_working"
        assetPath = "block\slime_milker_front_working.png"
        vanillaRefs = @("block\cobblestone.png", "block\oak_planks.png", "block\oak_door_bottom.png")
    },
    @{
        slug = "slime_milker_gui"
        assetPath = "gui\container\slime_milker.png"
        vanillaRefs = @("gui\container\furnace.png", "gui\container\blast_furnace.png")
    },
    @{
        slug = "tadpole_silhouette"
        assetPath = "item\tadpole_silhouette.png"
        vanillaRefs = @("item\tadpole_bucket.png", "item\milk_bucket.png", "item\bucket.png")
    },
    @{
        slug = "slime_silhouette"
        assetPath = "item\slime_silhouette.png"
        vanillaRefs = @("item\slime_ball.png", "item\bucket.png")
    },
    @{
        slug = "frog_spawn_egg"
        assetPath = "item\frog_spawn_egg.png"
        vanillaRefs = @("item\frog_spawn_egg.png", "item\egg.png", "item\turtle_spawn_egg.png")
    },
    @{
        slug = "tadpole_spawn_egg"
        assetPath = "item\tadpole_spawn_egg.png"
        vanillaRefs = @("item\frog_spawn_egg.png", "item\egg.png", "item\axolotl_spawn_egg.png")
    },
    @{
        slug = "slime_spawn_egg"
        assetPath = "item\slime_spawn_egg.png"
        vanillaRefs = @("item\slime_spawn_egg.png", "item\magma_cube_spawn_egg.png", "item\egg.png")
    }
)

# Each "candidate" = { label, path, sortKey, kind }. Path is the absolute path
# on disk; we relative-link them in the HTML so the file is portable.
# kind buckets the row visually: vanilla refs go in a light header, shipped is
# the current asset, candidates are the new generations.
function Get-Candidates {
    param([string]$slug, [string]$assetPath, [string[]]$vanillaRefs)
    $list = New-Object System.Collections.ArrayList

    # 1. Vanilla Minecraft references for style comparison.
    if ($vanillaRefs) {
        foreach ($vref in $vanillaRefs) {
            $vp = Join-Path $mcExtract "assets\minecraft\textures\$vref"
            if (Test-Path $vp) {
                [void]$list.Add(@{
                    label = "vanilla: $vref"
                    path = $vp
                    sortKey = "0-$vref"
                    kind = "vanilla"
                })
            }
        }
    }

    # 2. The currently shipped version.
    $shipped = Join-Path $assetsRoot $assetPath
    if (Test-Path $shipped) {
        [void]$list.Add(@{ label = "shipped (in-repo)"; path = $shipped; sortKey = "1"; kind = "shipped" })
    }

    # 3. Manually-curated comparisons sit at gen/comparisons/<slug>/*.png.
    $manualDir = Join-Path $genRoot "comparisons\$slug"
    if (Test-Path $manualDir) {
        foreach ($f in Get-ChildItem -Path $manualDir -Filter "*.png" -ErrorAction SilentlyContinue) {
            [void]$list.Add(@{
                label = "manual: $($f.BaseName)"
                path = $f.FullName
                sortKey = "2-$($f.Name)"
                kind = "candidate"
            })
        }
    }

    # 4. Batched generations from the AI scripts:
    #    gen/<slug>-YYYYMMDD-HHMMSS/<idx>.png
    $batches = Get-ChildItem -Path $genRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "$slug-*" } |
        Sort-Object Name -Descending
    foreach ($batch in $batches) {
        $batchTs = $batch.Name.Substring($slug.Length + 1)
        foreach ($f in Get-ChildItem -Path $batch.FullName -Filter "*.png" -ErrorAction SilentlyContinue |
                 Where-Object { $_.BaseName -notmatch "_preview$" }) {
            [void]$list.Add(@{
                label = "$batchTs / $($f.BaseName)"
                path = $f.FullName
                sortKey = "3-$batchTs-$($f.Name)"
                kind = "candidate"
            })
        }
    }

    return $list | Sort-Object { $_.sortKey }
}

# Build an HTML-safe relative path from outHtml's parent (genRoot) to a file.
function Get-RelativePath {
    param([string]$from, [string]$to)
    $fromUri = New-Object System.Uri (($from.TrimEnd('\') + '\'))
    $toUri = New-Object System.Uri $to
    $rel = $fromUri.MakeRelativeUri($toUri).ToString()
    # MakeRelativeUri produces forward slashes already — browsers want that.
    return $rel
}

$html = New-Object System.Text.StringBuilder
[void]$html.Append(@"
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Productive Frogs - Texture Candidate Comparison</title>
<style>
  :root { --bg: #161618; --bg2: #1f1f23; --fg: #ddd; --muted: #888; --accent: #5fb37a; --vanilla: #6c8dc8; --shipped: #5fb37a; }
  * { box-sizing: border-box; }
  body { font-family: -apple-system, Segoe UI, sans-serif; background: var(--bg); color: var(--fg); margin: 0; padding-bottom: 80px; }
  header { position: sticky; top: 0; background: var(--bg); padding: 14px 24px; border-bottom: 1px solid #333; z-index: 10; }
  header h1 { margin: 0 0 8px 0; font-size: 18px; }
  header .build-ts { color: var(--muted); font-size: 11px; margin-bottom: 8px; }
  header nav { display: flex; flex-wrap: wrap; gap: 6px; }
  header nav a { padding: 3px 9px; background: var(--bg2); color: #aaa; text-decoration: none; font-size: 11px; border-radius: 3px; font-family: monospace; }
  header nav a:hover { background: #333; color: #fff; }
  main { padding: 0 24px; }
  section { margin-top: 24px; padding-top: 12px; border-top: 1px solid #2a2a2a; scroll-margin-top: 120px; }
  section h2 { margin: 0 0 4px 0; font-size: 16px; font-family: monospace; }
  section .asset-path { color: var(--muted); font-size: 11px; font-family: monospace; margin-bottom: 12px; }
  /* The card grid: each candidate is a card with image + label stacked. */
  .cards { display: flex; flex-wrap: wrap; gap: 10px; }
  .card { background: var(--bg2); padding: 8px; border-radius: 4px; width: 208px; }
  .card.vanilla { border-left: 3px solid var(--vanilla); }
  .card.shipped { border-left: 3px solid var(--shipped); }
  .card.candidate { border-left: 3px solid #555; }
  .card .label { font-family: monospace; font-size: 10px; color: #aaa; margin-top: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .card.vanilla .label  { color: var(--vanilla); font-style: italic; }
  .card.shipped .label  { color: var(--shipped); font-weight: bold; }
  img { image-rendering: pixelated; image-rendering: crisp-edges; display: block; width: 192px; height: 192px; }
  .missing { color: #555; font-style: italic; }
</style>
</head>
<body>
<header>
  <h1>Productive Frogs - Texture Candidate Comparison</h1>
  <div class="build-ts">Built $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'). Re-run <code>.\scripts\build_comparison_page.ps1</code> after new generations land.</div>
  <nav>
"@)

# Sticky TOC links
foreach ($s in $surfaces) {
    [void]$html.Append("    <a href='#$($s.slug)'>$($s.slug)</a>`n")
}
[void]$html.Append("  </nav>`n</header>`n<main>`n")

foreach ($s in $surfaces) {
    $slug = $s.slug
    $assetPath = $s.assetPath
    $vanillaRefs = $s.vanillaRefs
    $candidates = Get-Candidates -slug $slug -assetPath $assetPath -vanillaRefs $vanillaRefs

    [void]$html.Append("<section id='$slug'>`n")
    [void]$html.Append("  <h2>$slug</h2>`n")
    [void]$html.Append("  <div class='asset-path'>$assetPath</div>`n")

    if ($candidates.Count -eq 0) {
        [void]$html.Append("  <p class='missing'>No candidates and no shipped version found.</p>`n")
        [void]$html.Append("</section>`n")
        continue
    }

    [void]$html.Append("  <div class='cards'>`n")
    foreach ($cand in $candidates) {
        $rel = Get-RelativePath -from $genRoot -to $cand.path
        $kind = if ($cand.kind) { $cand.kind } else { "candidate" }
        [void]$html.Append("    <div class='card $kind'>`n")
        [void]$html.Append("      <img src='$rel' alt='$($cand.label)'>`n")
        [void]$html.Append("      <div class='label'>$($cand.label)</div>`n")
        [void]$html.Append("    </div>`n")
    }
    [void]$html.Append("  </div>`n")
    [void]$html.Append("</section>`n")
}

[void]$html.Append("</main>`n</body></html>`n")

[System.IO.File]::WriteAllText($outHtml, $html.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Output "wrote $outHtml"
Write-Output "open in browser to compare candidates side-by-side"
