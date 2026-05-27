# Generate cross-mod crush recipes: a metal Froglight -> 2x its dust, gated by
# neoforge:mod_loaded. See docs/v1_3_crush_recipes.md (build plan) and
# docs/cross_mod_compat.md (source-verified rationale + per-mod dust sets).
#
# For each (crusher mod x crushable metal) pair, the output is resolved by
# precedence: (1) the crusher's own dust, else (2) alltheores:<metal>_dust
# gated on the crusher AND alltheores, else (3) skip the pair. Outputs are
# concrete item ids (no crusher accepts a tag in the result).
#
# BOMless UTF-8, LF endings, no em-dashes / en-dashes (all three break either
# PowerShell 5.1 parsing or downstream JSON parsing). Re-run after adding a
# metal variant or changing a per-mod dust map.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$recipeBase = Join-Path $root 'src\main\resources\data\productivefrogs\recipe'

# Curated crushable metals: the metal subset of the shipped slime_variant set.
# Metal-ness is NOT derivable from Category (CAVE also holds coal/redstone/lapis/
# diamond/emerald), so this is a hand-maintained list. Add new metal variants here.
$metals = @(
    'iron', 'copper', 'gold',
    'aluminum', 'tin', 'lead', 'nickel', 'osmium', 'silver', 'zinc', 'uranium',
    'mythril', 'orichalcum', 'brass', 'refined_obsidian', 'steel'
)

# AllTheOres dust set (the fallback layer). alltheores:<metal>_dust exists for
# this full range; its c:dusts/<metal> -> ingot smelt backstops the loop.
$atoMetals = @(
    'aluminum', 'copper', 'gold', 'iron', 'lead', 'nickel',
    'osmium', 'silver', 'tin', 'uranium', 'zinc'
)

# Per-crusher native dust: { metal -> dust item id }. Research baseline,
# source-verified 2026-05 (docs/cross_mod_compat.md). NOTE: the IE grit set is
# the documented baseline and is flagged for release-time re-verification (spec
# Open items); routing a metal IE actually grinds natively to the ATO fallback
# only costs a pack that lacks ATO that one recipe, never correctness.
$nativeDust = @{
    mekanism = @{
        recipeType = 'mekanism:enriching'
        metals     = @('iron', 'copper', 'gold', 'osmium', 'tin', 'lead', 'uranium')
        dustId     = { param($m) "mekanism:dust_$m" }
    }
    immersiveengineering = @{
        recipeType = 'immersiveengineering:crusher'
        metals     = @('iron', 'copper', 'aluminum', 'lead', 'silver', 'nickel', 'uranium')
        dustId     = { param($m) "immersiveengineering:dust_$m" }
    }
    enderio = @{
        recipeType = 'enderio:sag_milling'
        metals     = @('iron', 'gold', 'copper', 'tin')
        dustId     = { param($m) "enderio:powdered_$m" }
    }
}

# Recipe-body templates (placeholders, not -f, to dodge brace-escaping). The
# __CONDITIONS__ block is pre-indented to 4 spaces and injected verbatim.
$templates = @{
    mekanism = @'
{
  "neoforge:conditions": [
__CONDITIONS__
  ],
  "type": "mekanism:enriching",
  "input": {
    "type": "neoforge:components",
    "items": "productivefrogs:configurable_froglight",
    "components": { "productivefrogs:slime_variant": "productivefrogs:__METAL__" }
  },
  "output": { "count": 2, "id": "__DUST__" }
}
'@
    immersiveengineering = @'
{
  "neoforge:conditions": [
__CONDITIONS__
  ],
  "type": "immersiveengineering:crusher",
  "energy": 3000,
  "input": {
    "type": "neoforge:components",
    "items": "productivefrogs:configurable_froglight",
    "components": { "productivefrogs:slime_variant": "productivefrogs:__METAL__" }
  },
  "result": { "id": "__DUST__", "count": 2 }
}
'@
    enderio = @'
{
  "neoforge:conditions": [
__CONDITIONS__
  ],
  "type": "enderio:sag_milling",
  "energy": 2400,
  "bonus": "none",
  "input": {
    "type": "neoforge:components",
    "items": "productivefrogs:configurable_froglight",
    "components": { "productivefrogs:slime_variant": "productivefrogs:__METAL__" }
  },
  "outputs": [ { "item": { "count": 2, "id": "__DUST__" } } ]
}
'@
}

function Build-Conditions {
    param([string[]] $mods)
    ($mods | ForEach-Object { '    { "type": "neoforge:mod_loaded", "modid": "' + $_ + '" }' }) -join ",`n"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$written = 0
$skipped = @()

foreach ($mod in 'mekanism', 'immersiveengineering', 'enderio') {
    $cfg = $nativeDust[$mod]
    $template = $templates[$mod]
    $modDir = Join-Path $recipeBase $mod
    if (-not (Test-Path $modDir)) {
        New-Item -ItemType Directory -Path $modDir -Force | Out-Null
    }

    foreach ($metal in $metals) {
        if ($cfg.metals -contains $metal) {
            $dust = & $cfg.dustId $metal
            $conditions = @($mod)
        }
        elseif ($atoMetals -contains $metal) {
            $dust = "alltheores:${metal}_dust"
            $conditions = @($mod, 'alltheores')
        }
        else {
            $skipped += "$mod/$metal"
            continue
        }

        $condBlock = Build-Conditions $conditions
        $json = $template `
            -replace '__CONDITIONS__', $condBlock `
            -replace '__METAL__', $metal `
            -replace '__DUST__', $dust
        $json = ($json -replace "`r`n", "`n").TrimEnd("`n") + "`n"

        $path = Join-Path $modDir "$metal.json"
        [System.IO.File]::WriteAllText($path, $json, $utf8NoBom)
        $written++
        Write-Host "  wrote recipe/$mod/$metal.json -> $dust ($($conditions -join ' + '))"
    }
}

Write-Host ""
Write-Host "Wrote $written crush recipes."
Write-Host "Skipped (no dust source): $($skipped -join ', ')"
