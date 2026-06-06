# Generate the Crucible's wave-2 molten-metal melt recipes + the c: fluid tags
# that make PF molten interop with AllTheOres' (v1.12, docs/froglight_crucible.md).
#
# Output model (mirrors PFMoltenFluids' minting rules exactly - recipe and
# registry must never disagree):
#   - DUAL metals (exist without ATO): two recipes - the ATO-output recipe
#     gated mod_loaded(alltheores) [+ provider], and a _pf fallback recipe
#     gated not(alltheores) [+ provider] outputting productivefrogs:molten_*.
#   - ATO-GATED metals (variant requires alltheores): one recipe outputting
#     alltheores:molten_*, gated mod_loaded(alltheores). No PF fluid ever.
#   - PF-ONLY metals (ATO has no molten for them): one recipe outputting
#     productivefrogs:molten_*, gated on the variant's provider.
#   - c:molten_<metal> tag files for every PF-minted fluid (required:false
#     entries, merging with ATO's identical tag files when both ship).
#
# All outputs are 180 mB - 2 ingots' worth at the 90 mB/ingot Tinkers
# convention, the decided ore-doubling yield. Idempotent; rerun after roster
# changes. ASCII + BOMless UTF-8 (the .gitattributes LF policy).

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$recipeDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\recipe"
$tagDir = Join-Path $repoRoot "src\main\resources\data\c\tags\fluid"
New-Item -ItemType Directory -Force $tagDir | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)

$AMOUNT = 180

# Metals whose variants exist without AllTheOres -> dual recipes + PF fluid tag.
$dual = @(
    @{ metal = "iron";   provider = $null }
    @{ metal = "copper"; provider = $null }
    @{ metal = "gold";   provider = $null }
    @{ metal = "brass";  provider = "create" }
    @{ metal = "steel";  provider = "mekanism" }
)
# Metals whose variants are themselves alltheores-gated -> ATO output only.
$atoOnly = @("tin", "lead", "osmium", "nickel", "silver", "zinc", "aluminum", "uranium")
# Metals ATO has no molten fluid for -> PF output + tag, gated on the provider.
$pfOnly = @(
    @{ metal = "refined_obsidian"; provider = "mekanism" }
    @{ metal = "mythril";          provider = "mythicmetals" }
    @{ metal = "orichalcum";       provider = "mythicmetals" }
)

function ModLoaded($modid) { return "    { `"type`": `"neoforge:mod_loaded`", `"modid`": `"$modid`" }" }
function NotLoaded($modid) { return "    { `"type`": `"neoforge:not`", `"value`": { `"type`": `"neoforge:mod_loaded`", `"modid`": `"$modid`" } }" }

function WriteMelt($metal, $fluidId, $conds, $suffix) {
    $condBlock = ($conds -join ",`n")
    $json = "{`n  `"neoforge:conditions`": [`n$condBlock`n  ],`n  `"type`": `"productivefrogs:crucible_melting`",`n  `"ingredient`": {`n    `"type`": `"neoforge:components`",`n    `"items`": [ `"productivefrogs:configurable_froglight`" ],`n    `"components`": { `"productivefrogs:slime_variant`": `"productivefrogs:$metal`" }`n  },`n  `"result`": { `"id`": `"$fluidId`", `"amount`": $AMOUNT }`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $recipeDir "crucible_melt_$metal$suffix.json"), $json, $utf8)
}

function WriteTag($metal) {
    $json = "{`n  `"values`": [`n    { `"id`": `"productivefrogs:molten_$metal`", `"required`": false },`n    { `"id`": `"productivefrogs:molten_${metal}_flowing`", `"required`": false }`n  ]`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $tagDir "molten_$metal.json"), $json, $utf8)
}

$count = 0
foreach ($m in $dual) {
    $atoConds = @()
    $pfConds = @()
    if ($m.provider) {
        $atoConds += ModLoaded $m.provider
        $pfConds += ModLoaded $m.provider
    }
    $atoConds += ModLoaded "alltheores"
    $pfConds += NotLoaded "alltheores"
    WriteMelt $m.metal "alltheores:molten_$($m.metal)" $atoConds ""
    WriteMelt $m.metal "productivefrogs:molten_$($m.metal)" $pfConds "_pf"
    WriteTag $m.metal
    $count += 2
}
foreach ($metal in $atoOnly) {
    WriteMelt $metal "alltheores:molten_$metal" @(ModLoaded "alltheores") ""
    $count++
}
foreach ($m in $pfOnly) {
    WriteMelt $m.metal "productivefrogs:molten_$($m.metal)" @(ModLoaded $m.provider) ""
    WriteTag $m.metal
    $count++
}
Write-Output "wrote $count melt recipes + $($dual.Count + $pfOnly.Count) c:molten tags"
