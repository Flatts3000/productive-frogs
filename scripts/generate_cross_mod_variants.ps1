# Generate the V1.2 cross-mod Resource Slime variant pool + Froglight smelt
# recipes from a data table. See docs/cross_mod_compat.md (plan of record).
#
# Per variant emits two files (BOMless UTF-8):
#   data/productivefrogs/productivefrogs/slime_variant/<name>.json
#   data/productivefrogs/recipe/configurable_froglight_<name>.json
#
# Gating:
#   - EVERY variant + its smelt recipe is gated mod_loaded(<provider>) - the mod
#     whose verified item the smelt outputs. (NeoForge forbids tag-based
#     conditions like tag_empty when loading datapack-registry entries, because
#     registries load before tags. So we gate on the provider mod, not on the
#     tag being non-empty.)
#   - The primer is still tag-driven where a common tag exists: primer_tag
#     resolves at infusion time (runtime), where tags ARE loaded, so once the
#     variant is loaded it is primed by ANY mod's item in that tag (e.g. ATO or
#     Mekanism tin both prime the tin variant). Bespoke variants with no
#     per-resource common tag use primer_item (the provider's exact id).
#
# Item ids are verified against each mod's 1.21.x source (see the cross_mod_compat
# doc). Thermal Series is intentionally absent: it has no 1.21.1 release and is
# not in ATM10 (apatite/sulfur/signalum/lumium/enderium are deferred until it
# ports). Colours are representative per-resource tints (24-bit RGB).
#
# Platform: Windows PowerShell. ASCII only (em-dashes break PS 5.1 parsing).

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$variantDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\productivefrogs\slime_variant"
$recipeDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\recipe"
New-Item -ItemType Directory -Force -Path $variantDir, $recipeDir | Out-Null

# name, category, tag (or $null), primerItem (or $null), mod, result, primary, secondary
$variants = @(
    # CAVE - AllTheOres metals (tag-driven; smelt to the ATM10-canonical ATO ingot)
    @{ name = "tin";       category = "cave";     tag = "c:ingots/tin";              mod = "alltheores"; result = "alltheores:tin_ingot";      primary = 0xC8D2D2; secondary = 0x9FAAB0 }
    @{ name = "lead";      category = "cave";     tag = "c:ingots/lead";             mod = "alltheores"; result = "alltheores:lead_ingot";     primary = 0x52606E; secondary = 0x3A4652 }
    @{ name = "osmium";    category = "cave";     tag = "c:ingots/osmium";           mod = "alltheores"; result = "alltheores:osmium_ingot";   primary = 0x4AA6C8; secondary = 0x2F7D9A }
    @{ name = "nickel";    category = "cave";     tag = "c:ingots/nickel";           mod = "alltheores"; result = "alltheores:nickel_ingot";   primary = 0xE6DEAE; secondary = 0xC2BA86 }
    @{ name = "silver";    category = "cave";     tag = "c:ingots/silver";           mod = "alltheores"; result = "alltheores:silver_ingot";   primary = 0xDCE6EA; secondary = 0xB2BEC8 }
    @{ name = "zinc";      category = "cave";     tag = "c:ingots/zinc";             mod = "alltheores"; result = "alltheores:zinc_ingot";     primary = 0xC6C4B4; secondary = 0xA09E8E }
    @{ name = "aluminum";  category = "cave";     tag = "c:ingots/aluminum";         mod = "alltheores"; result = "alltheores:aluminum_ingot"; primary = 0xD6DADC; secondary = 0xACB2B6 }
    @{ name = "uranium";   category = "cave";     tag = "c:ingots/uranium";          mod = "alltheores"; result = "alltheores:uranium_ingot";  primary = 0x5FA030; secondary = 0x3F7820 }
    # CAVE - alloy / processed (tag-driven, single verified provider)
    @{ name = "brass";     category = "cave";     tag = "c:ingots/brass";            mod = "create";     result = "create:brass_ingot";       primary = 0xC9A23A; secondary = 0xA67F22 }
    @{ name = "refined_obsidian"; category = "cave"; tag = "c:ingots/refined_obsidian"; mod = "mekanism"; result = "mekanism:ingot_refined_obsidian"; primary = 0x5A3FA0; secondary = 0x3F2A78 }
    @{ name = "steel";     category = "cave";     tag = "c:ingots/steel";            mod = "mekanism";   result = "mekanism:ingot_steel";      primary = 0x8E9CAA; secondary = 0x5E6B78 }
    # GEODE - gems/crystals
    @{ name = "certus_quartz"; category = "geode"; tag = "c:gems/certus_quartz";     mod = "ae2";        result = "ae2:certus_quartz_crystal"; primary = 0xC0CDDA; secondary = 0x90A4B8 }
    @{ name = "fluix";     category = "geode";    tag = "c:gems/fluix";              mod = "ae2";        result = "ae2:fluix_crystal";        primary = 0x7A5AC0; secondary = 0x57408F }
    @{ name = "fluorite";  category = "geode";    tag = "c:gems/fluorite";           mod = "mekanism";   result = "mekanism:fluorite_gem";    primary = 0xCFE0C0; secondary = 0xA8C090 }
    @{ name = "silicon";   category = "geode";    tag = "c:silicon";                 mod = "ae2";        result = "ae2:silicon";              primary = 0x4A4A52; secondary = 0x32323A }
    # VOID - mythic metals (tag-driven) + Powah crystals (bespoke, no per-type tag)
    @{ name = "orichalcum"; category = "void";    tag = "c:ingots/orichalcum";       mod = "mythicmetals"; result = "mythicmetals:orichalcum_ingot"; primary = 0x3FB890; secondary = 0x2A8568 }
    @{ name = "mythril";   category = "void";     tag = "c:ingots/mythril";          mod = "mythicmetals"; result = "mythicmetals:mythril_ingot"; primary = 0x9FD0E0; secondary = 0x70AEC4 }
    @{ name = "niotic";    category = "void";     tag = $null; primerItem = "powah:crystal_niotic";   mod = "powah"; result = "powah:crystal_niotic";   primary = 0xD04A6A; secondary = 0x9F2F4A }
    @{ name = "spirited";  category = "void";     tag = $null; primerItem = "powah:crystal_spirited"; mod = "powah"; result = "powah:crystal_spirited"; primary = 0x4AC07A; secondary = 0x2F8F54 }
    @{ name = "nitro";     category = "void";     tag = $null; primerItem = "powah:crystal_nitro";    mod = "powah"; result = "powah:crystal_nitro";    primary = 0x3FC0D0; secondary = 0x2A92A0 }
    # INFERNAL - Powah blazing crystal
    @{ name = "blazing";   category = "infernal"; tag = $null; primerItem = "powah:crystal_blazing";  mod = "powah"; result = "powah:crystal_blazing";  primary = 0xE8902A; secondary = 0xC06A14 }
    # BOG - mob/organic drops
    @{ name = "pink_slime"; category = "bog";     tag = $null; primerItem = "industrialforegoing:pink_slime"; mod = "industrialforegoing"; result = "industrialforegoing:pink_slime"; primary = 0xE87AB0; secondary = 0xC04F8A }
    @{ name = "inferium";  category = "bog";      tag = $null; primerItem = "mysticalagriculture:inferium_essence";  mod = "mysticalagriculture"; result = "mysticalagriculture:inferium_essence";  primary = 0x8FD060; secondary = 0x6FA840 }
    @{ name = "supremium"; category = "bog";      tag = $null; primerItem = "mysticalagriculture:supremium_essence"; mod = "mysticalagriculture"; result = "mysticalagriculture:supremium_essence"; primary = 0xE05A7A; secondary = 0xB83F5A }
    # TIDE - the one strong modded aquatic resource
    @{ name = "aquarium";  category = "tide";     tag = "c:ingots/aquarium";         mod = "mythicmetals"; result = "mythicmetals:aquarium_ingot"; primary = 0x4FC0C8; secondary = 0x2F95A0 }
)

$utf8 = [System.Text.UTF8Encoding]::new($false)
$count = 0
foreach ($v in $variants) {
    # Guard: every row must have a tag (tag-driven primer) or a primerItem
    # (bespoke). Without one, the JSON would emit an empty "primer_item" that
    # fails codec decode at server boot. Catch a careless table edit here.
    if (-not $v.tag -and -not $v.primerItem) {
        Write-Error "variant '$($v.name)' has neither tag nor primerItem - check the data table"
        exit 1
    }
    # Every variant gates on mod_loaded(provider): datapack-registry entries
    # cannot use tag-based conditions (tags load after registries). primer_tag
    # still drives infusion at runtime where a common tag exists; bespoke
    # variants use primer_item.
    $condition = "    { `"type`": `"neoforge:mod_loaded`", `"modid`": `"$($v.mod)`" }"
    if ($v.tag) {
        $primerLine = "  `"primer_tag`": `"$($v.tag)`","
    }
    else {
        $primerLine = "  `"primer_item`": `"$($v.primerItem)`","
    }
    $variantJson = "{`n  `"neoforge:conditions`": [`n$condition`n  ],`n$primerLine`n  `"category`": `"$($v.category)`",`n  `"primary_color`": $([int]$v.primary),`n  `"secondary_color`": $([int]$v.secondary)`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $variantDir "$($v.name).json"), $variantJson, $utf8)

    # Smelt recipe: Froglight stamped with this variant -> the provider's item, gated mod_loaded.
    $recipeJson = "{`n  `"neoforge:conditions`": [`n    { `"type`": `"neoforge:mod_loaded`", `"modid`": `"$($v.mod)`" }`n  ],`n  `"type`": `"minecraft:smelting`",`n  `"ingredient`": {`n    `"type`": `"neoforge:components`",`n    `"items`": [ `"productivefrogs:configurable_froglight`" ],`n    `"components`": { `"productivefrogs:slime_variant`": `"productivefrogs:$($v.name)`" }`n  },`n  `"result`": { `"id`": `"$($v.result)`" },`n  `"experience`": 0.7,`n  `"cookingtime`": 200,`n  `"category`": `"misc`",`n  `"group`": `"productivefrogs_froglight`"`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $recipeDir "configurable_froglight_$($v.name).json"), $recipeJson, $utf8)
    $count++
}

Write-Output "wrote $count cross-mod variants + $count smelt recipes"
