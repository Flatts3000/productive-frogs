# Generate the Casting Mold's solidify recipes (v1.12 wave 2 part B,
# docs/froglight_crucible.md): one mold_casting recipe per metal in the wave-2
# roster, matching molten by c:molten_<metal> TAG so AllTheOres molten casts
# exactly like PF's own (the second half of the ATM interop - the melt side
# lives in generate_crucible_melt_recipes.ps1).
#
# Conditions follow the OUTPUT item's provider (the ingot must exist), not the
# fluid's: the c: tag resolves to whichever mod minted the fluid at runtime.
# Vanilla-ingot metals are unconditional - the tag is always populated (PF
# mints when ATO is absent, ATO provides when present).
#
# All casts are 90 mB -> 1 ingot (the Tinkers convention; the Crucible's
# 180 mB melt = 2 casts = the ore-doubling yield). Idempotent; rerun after
# roster changes. ASCII + BOMless UTF-8 (the .gitattributes LF policy).

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$recipeDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\recipe"
$utf8 = [System.Text.UTF8Encoding]::new($false)

$MB_PER_INGOT = 90

# metal -> output ingot id + the mod that must be loaded for that ingot
# ($null = vanilla output, unconditional).
$roster = @(
    @{ metal = "iron";             result = "minecraft:iron_ingot";              requires = $null }
    @{ metal = "copper";           result = "minecraft:copper_ingot";            requires = $null }
    @{ metal = "gold";             result = "minecraft:gold_ingot";              requires = $null }
    @{ metal = "tin";              result = "alltheores:tin_ingot";              requires = "alltheores" }
    @{ metal = "lead";             result = "alltheores:lead_ingot";             requires = "alltheores" }
    @{ metal = "osmium";           result = "alltheores:osmium_ingot";           requires = "alltheores" }
    @{ metal = "nickel";           result = "alltheores:nickel_ingot";           requires = "alltheores" }
    @{ metal = "silver";           result = "alltheores:silver_ingot";           requires = "alltheores" }
    @{ metal = "zinc";             result = "alltheores:zinc_ingot";             requires = "alltheores" }
    @{ metal = "aluminum";         result = "alltheores:aluminum_ingot";         requires = "alltheores" }
    @{ metal = "uranium";          result = "alltheores:uranium_ingot";          requires = "alltheores" }
    @{ metal = "brass";            result = "create:brass_ingot";                requires = "create" }
    @{ metal = "steel";            result = "mekanism:ingot_steel";              requires = "mekanism" }
    @{ metal = "refined_obsidian"; result = "mekanism:ingot_refined_obsidian";   requires = "mekanism" }
    @{ metal = "mythril";          result = "mythicmetals:mythril_ingot";        requires = "mythicmetals" }
    @{ metal = "orichalcum";       result = "mythicmetals:orichalcum_ingot";     requires = "mythicmetals" }
)

$count = 0
foreach ($m in $roster) {
    $cond = ""
    if ($m.requires) {
        $cond = "  `"neoforge:conditions`": [`n    { `"type`": `"neoforge:mod_loaded`", `"modid`": `"$($m.requires)`" }`n  ],`n"
    }
    $json = "{`n$cond  `"type`": `"productivefrogs:mold_casting`",`n  `"fluid`": { `"tag`": `"c:molten_$($m.metal)`", `"amount`": $MB_PER_INGOT },`n  `"result`": { `"id`": `"$($m.result)`" }`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $recipeDir "mold_cast_$($m.metal).json"), $json, $utf8)
    $count++
}
Write-Output "wrote $count mold_casting recipes"
