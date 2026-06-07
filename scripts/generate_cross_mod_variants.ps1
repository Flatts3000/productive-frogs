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
    @{ name = "steel";     category = "cave";     tag = "c:ingots/steel";            mod = "mekanism";   result = "mekanism:ingot_steel";      primary = 0x8E9CAA; secondary = 0x5E6B78 }
    # CAVE - Powah base materials (bespoke item ids; colors are texture-faithful averages).
    # energized_steel follows the steel lineage (iron+gold, energized); uraninite is Powah's
    # mined ore - the skyblock-critical base resource (#146).
    @{ name = "energized_steel"; category = "cave"; tag = $null; primerItem = "powah:steel_energized"; mod = "powah"; result = "powah:steel_energized"; primary = 0xA79272; secondary = 0x756650 }
    @{ name = "uraninite"; category = "cave";     tag = $null; primerItem = "powah:uraninite";        mod = "powah"; result = "powah:uraninite";        primary = 0x119346; secondary = 0x0C6731 }
    # GEODE - gems/crystals
    @{ name = "certus_quartz"; category = "geode"; tag = "c:gems/certus_quartz";     mod = "ae2";        result = "ae2:certus_quartz_crystal"; primary = 0xC0CDDA; secondary = 0x90A4B8 }
    @{ name = "fluix";     category = "geode";    tag = "c:gems/fluix";              mod = "ae2";        result = "ae2:fluix_crystal";        primary = 0x7A5AC0; secondary = 0x57408F }
    @{ name = "fluorite";  category = "geode";    tag = "c:gems/fluorite";           mod = "mekanism";   result = "mekanism:fluorite_gem";    primary = 0xCFE0C0; secondary = 0xA8C090 }
    # silicon is shared by AE2 and Refined Storage (both populate c:silicon), so the
    # variant gates on EITHER mod (neoforge:or) and smelts back to whichever provider
    # is present. The RS recipe is also gated NOT(ae2) so the two never both fire when
    # both mods are installed - AE2 wins, you get ae2:silicon. (Two-provider scope: a
    # third c:silicon provider would need adding to gateMods AND a NOT entry in every
    # earlier recipe to keep them mutually exclusive.) RS ids source-verified against
    # refinedmods/refinedstorage2 (refinedstorage-common generated data).
    @{ name = "silicon"; category = "geode"; tag = "c:silicon"; gateMods = @("ae2", "refinedstorage"); recipes = @(
            @{ mod = "ae2"; result = "ae2:silicon"; suffix = "" }
            @{ mod = "refinedstorage"; notMods = @("ae2"); result = "refinedstorage:silicon"; suffix = "refinedstorage" }
        ); primary = 0x4A4A52; secondary = 0x32323A }
    # GEODE - Refined Storage processors (crystal-tech; primed by the exact processor item)
    @{ name = "basic_processor";    category = "geode"; tag = $null; primerItem = "refinedstorage:basic_processor";    mod = "refinedstorage"; result = "refinedstorage:basic_processor";    primary = 0x7FB7D9; secondary = 0x4F8FB0 }
    @{ name = "improved_processor"; category = "geode"; tag = $null; primerItem = "refinedstorage:improved_processor"; mod = "refinedstorage"; result = "refinedstorage:improved_processor"; primary = 0xD9B24F; secondary = 0xB08A2F }
    @{ name = "advanced_processor"; category = "geode"; tag = $null; primerItem = "refinedstorage:advanced_processor"; mod = "refinedstorage"; result = "refinedstorage:advanced_processor"; primary = 0x4FD9C8; secondary = 0x2FB0A0 }
    # INFERNAL - Refined Storage Quartz Enriched Iron (quartz lineage; quartz is an Infernal resource here)
    @{ name = "quartz_enriched_iron"; category = "infernal"; tag = $null; primerItem = "refinedstorage:quartz_enriched_iron"; mod = "refinedstorage"; result = "refinedstorage:quartz_enriched_iron"; primary = 0xD8D2C4; secondary = 0xA9A294 }
    # INFERNAL - Mekanism refined obsidian (obsidian lineage; obsidian is an Infernal resource - it gates behind a diamond pickaxe, so it must sit after diamond in the chain)
    @{ name = "refined_obsidian"; category = "infernal"; tag = "c:ingots/refined_obsidian"; mod = "mekanism"; result = "mekanism:ingot_refined_obsidian"; primary = 0x5A3FA0; secondary = 0x3F2A78 }
    # INFERNAL - Mekanism refined glowstone (glowstone lineage, same logic as refined obsidian following obsidian; issue #180). Colors sampled from ingot_refined_glowstone.png.
    @{ name = "refined_glowstone"; category = "infernal"; tag = "c:ingots/refined_glowstone"; mod = "mekanism"; result = "mekanism:ingot_refined_glowstone"; primary = 0xFFF1B2; secondary = 0x9A7644 }
    # INFERNAL - Flux Networks flux dust (obsidian lineage: born from redstone dropped on obsidian, so it shares obsidian's tier; issue #145).
    # Colors are texture-faithful: the flux_dust item texture is near-black (avg 0x0A0A0A), lifted to 0x2E1A2E with a magenta cast for shell visibility.
    @{ name = "flux_dust"; category = "infernal"; tag = $null; primerItem = "fluxnetworks:flux_dust"; mod = "fluxnetworks"; result = "fluxnetworks:flux_dust"; primary = 0x2E1A2E; secondary = 0x201220 }
    # VOID - mythic metals (tag-driven) + Powah crystals (bespoke) + Mystical Agriculture essences (magic/arcane)
    @{ name = "inferium";  category = "void";     tag = $null; primerItem = "mysticalagriculture:inferium_essence";  mod = "mysticalagriculture"; result = "mysticalagriculture:inferium_essence";  primary = 0x8FD060; secondary = 0x6FA840 }
    @{ name = "supremium"; category = "void";     tag = $null; primerItem = "mysticalagriculture:supremium_essence"; mod = "mysticalagriculture"; result = "mysticalagriculture:supremium_essence"; primary = 0xE05A7A; secondary = 0xB83F5A }
    @{ name = "orichalcum"; category = "void";    tag = "c:ingots/orichalcum";       mod = "mythicmetals"; result = "mythicmetals:orichalcum_ingot"; primary = 0x3FB890; secondary = 0x2A8568 }
    @{ name = "mythril";   category = "void";     tag = "c:ingots/mythril";          mod = "mythicmetals"; result = "mythicmetals:mythril_ingot"; primary = 0x9FD0E0; secondary = 0x70AEC4 }
    # niotic/nitro colors are texture-faithful averages of Powah's crystal item
    # textures (niotic is the CYAN crystal, nitro the RED one - the original
    # hand-picked values had the two swapped; caught in-game 2026-06-06).
    @{ name = "niotic";    category = "void";     tag = $null; primerItem = "powah:crystal_niotic";   mod = "powah"; result = "powah:crystal_niotic";   primary = 0x119EB6; secondary = 0x0C6F7F }
    @{ name = "spirited";  category = "void";     tag = $null; primerItem = "powah:crystal_spirited"; mod = "powah"; result = "powah:crystal_spirited"; primary = 0x4AC07A; secondary = 0x2F8F54 }
    @{ name = "nitro";     category = "void";     tag = $null; primerItem = "powah:crystal_nitro";    mod = "powah"; result = "powah:crystal_nitro";    primary = 0xA12928; secondary = 0x711D1C }
    # INFERNAL - Powah blazing crystal
    @{ name = "blazing";   category = "infernal"; tag = $null; primerItem = "powah:crystal_blazing";  mod = "powah"; result = "powah:crystal_blazing";  primary = 0xE8902A; secondary = 0xC06A14 }
    # BOG - organic / swamp (slime + organic-derived synthetics)
    @{ name = "pink_slime"; category = "bog";     tag = $null; primerItem = "industrialforegoing:pink_slime"; mod = "industrialforegoing"; result = "industrialforegoing:pink_slime"; primary = 0xE87AB0; secondary = 0xC04F8A }
    @{ name = "plastic";   category = "bog";      tag = $null; primerItem = "industrialforegoing:plastic"; mod = "industrialforegoing"; result = "industrialforegoing:plastic"; primary = 0xE6E2D8; secondary = 0xB8B5AC }
    # TIDE - Powah dry ice (frozen-water theme; deliberately placed to fatten the
    # documented-thin Tide roster - see cross_mod_compat.md "the weak species").
    @{ name = "dry_ice";   category = "tide";     tag = $null; primerItem = "powah:dry_ice";          mod = "powah"; result = "powah:dry_ice";          primary = 0xD9EAF1; secondary = 0x98A4A9 }
    # TIDE - the one strong modded aquatic resource
    @{ name = "aquarium";  category = "tide";     tag = "c:ingots/aquarium";         mod = "mythicmetals"; result = "mythicmetals:aquarium_ingot"; primary = 0x4FC0C8; secondary = 0x2F95A0 }
)

$utf8 = [System.Text.UTF8Encoding]::new($false)

# A single mod_loaded condition object.
function ModLoaded($modid) { return "{ `"type`": `"neoforge:mod_loaded`", `"modid`": `"$modid`" }" }

$count = 0
$recipeCount = 0
foreach ($v in $variants) {
    # Guard: every row must have a tag (tag-driven primer) or a primerItem
    # (bespoke). Without one, the JSON would emit an empty "primer_item" that
    # fails codec decode at server boot. Catch a careless table edit here.
    if (-not $v.tag -and -not $v.primerItem) {
        Write-Error "variant '$($v.name)' has neither tag nor primerItem - check the data table"
        exit 1
    }
    # A multi-provider (gateMods) row must list explicit recipes: the default
    # recipe path below reads $v.mod / $v.result, which such a row does not set,
    # so it would silently emit a recipe with a null result id.
    if ($v.gateMods -and -not $v.recipes) {
        Write-Error "variant '$($v.name)' sets gateMods but no recipes - list a recipe per provider"
        exit 1
    }
    # Variant gate: datapack-registry entries cannot use tag-based conditions
    # (tags load after registries), so we gate on the provider mod. A variant
    # shared by two providers (e.g. silicon: AE2 + Refined Storage) gates on
    # EITHER via neoforge:or. primer_tag still drives infusion at runtime where a
    # common tag exists; bespoke variants use primer_item.
    if ($v.gateMods) {
        $orValues = ($v.gateMods | ForEach-Object { "      " + (ModLoaded $_) }) -join ",`n"
        $conditionsInner = "    { `"type`": `"neoforge:or`", `"values`": [`n$orValues`n    ] }"
    }
    else {
        $conditionsInner = "    " + (ModLoaded $v.mod)
    }
    if ($v.tag) {
        $primerLine = "  `"primer_tag`": `"$($v.tag)`","
    }
    else {
        $primerLine = "  `"primer_item`": `"$($v.primerItem)`","
    }
    $variantJson = "{`n  `"neoforge:conditions`": [`n$conditionsInner`n  ],`n$primerLine`n  `"category`": `"$($v.category)`",`n  `"primary_color`": $([int]$v.primary),`n  `"secondary_color`": $([int]$v.secondary)`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $variantDir "$($v.name).json"), $variantJson, $utf8)

    # Smelt recipe(s): Froglight stamped with this variant -> the provider's item,
    # gated mod_loaded. A row may declare multiple `recipes` (one per provider,
    # each optionally with `notMods` so two providers don't both fire); else a
    # single recipe from `mod`/`result`. The conditions array is implicitly AND-ed,
    # so notMods become neoforge:not entries alongside the mod_loaded.
    $recipeList = if ($v.recipes) { $v.recipes } else { @(@{ mod = $v.mod; result = $v.result; suffix = "" }) }
    foreach ($r in $recipeList) {
        $condLines = @("    " + (ModLoaded $r.mod))
        if ($r.notMods) {
            foreach ($nm in $r.notMods) {
                $condLines += "    { `"type`": `"neoforge:not`", `"value`": " + (ModLoaded $nm) + " }"
            }
        }
        $conds = $condLines -join ",`n"
        $fname = if ($r.suffix) { "configurable_froglight_$($v.name)_$($r.suffix).json" } else { "configurable_froglight_$($v.name).json" }
        $recipeJson = "{`n  `"neoforge:conditions`": [`n$conds`n  ],`n  `"type`": `"minecraft:smelting`",`n  `"ingredient`": {`n    `"type`": `"neoforge:components`",`n    `"items`": [ `"productivefrogs:configurable_froglight`" ],`n    `"components`": { `"productivefrogs:slime_variant`": `"productivefrogs:$($v.name)`" }`n  },`n  `"result`": { `"id`": `"$($r.result)`" },`n  `"experience`": 0.7,`n  `"cookingtime`": 200,`n  `"category`": `"misc`",`n  `"group`": `"productivefrogs_froglight`"`n}`n"
        [System.IO.File]::WriteAllText((Join-Path $recipeDir $fname), $recipeJson, $utf8)
        $recipeCount++
    }
    $count++
}

Write-Output "wrote $count cross-mod variants + $recipeCount smelt recipes"
