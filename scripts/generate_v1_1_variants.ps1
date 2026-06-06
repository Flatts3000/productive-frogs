# Generate the data + model JSON files for the v1.1 Resource Slime variants.
#
# WARNING - DO NOT RUN THIS SCRIPT WHOLESALE. It is one-shot v1.1 scaffolding,
# not an idempotent regenerator (unlike generate_cross_mod_variants.ps1):
#   - items #3/#4 below (milk blockstate + bucket model) are OWNED by
#     generate_milk_variant_assets.py since the v1.8 per-variant milk return;
#     this script's emit shape differs, so a run churns 44 files with pure
#     formatting noise.
#   - the $variants table lags later hand-edits unless someone syncs it; a
#     2026-06-06 wholesale run regenerated echo_shard back to its pre-v1.4.3
#     category and had to be reverted. When recategorizing or changing a smelt
#     result, update the table row (the roster of record) AND hand-edit the
#     committed JSON; do not re-run.
#   - the "PFFluidTypes.VARIANTS one-line Java edit" printout below is also
#     stale: since v1.8 the milk fluids mint from variants_index.json
#     (scripts/generate_variants_index.py), no Java edit.
# Single-row workflow that IS safe: edit the row, run, `git checkout --` every
# file you did not intend to change, keep the one you did. Or just hand-edit.
#
# This emits the four templated JSON files per new variant:
#   1. data/productivefrogs/productivefrogs/slime_variant/<name>.json
#   2. data/productivefrogs/recipe/configurable_froglight_<name>_to_<result>.json
#   3. assets/productivefrogs/blockstates/<name>_slime_milk.json   (SUPERSEDED - milk collapsed)
#   4. assets/productivefrogs/models/item/<name>_slime_milk_bucket.json   (SUPERSEDED - milk collapsed)
#
# It does NOT touch:
#   - lang/en_us.json (printed below for manual insertion at 7 section anchors)
#   - PFFluidTypes.VARIANTS (printed below for the one-line Java edit)
#   - the milk PNG textures (run generate_slime_milk_textures.ps1 afterward; it
#     auto-picks up the new slime_variant JSONs and tints per primary_color)
#
# secondary_color is auto-derived as 0.70x the primary per channel, matching the
# "second tint layer is a darker shade" convention the existing variants follow.
#
# Re-runnable: overwrites existing output. ASCII-only + BOMless UTF-8 writes so
# the generated JSON stays clean and the .gitattributes LF policy holds.
#
# Platform: Windows PowerShell 5.1 (System.Drawing not needed here - pure text).

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$variantDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\productivefrogs\slime_variant"
$recipeDir = Join-Path $repoRoot "src\main\resources\data\productivefrogs\recipe"
$blockstateDir = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\blockstates"
$bucketModelDir = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\models\item"

# --- The variant table. One row per new v1.1 variant. ---
# Name      : registry path (also the lang key suffix, fluid/block/item id stem)
# Primer    : item that infuses a vanilla slime into this variant
# Category  : parent species pool (bog/cave/geode/tide/infernal/void)
# Smelt     : the resource the stamped Configurable Froglight smelts to
# Inner     : vanilla block rendered inside the slime
# Primary   : primary tint (0xRRGGBB), sampled from the canonical resource colour
# Exp       : furnace experience for the froglight smelt recipe
# Label     : human-readable display name
$variants = @(
    @{ Name = "clay_ball";           Primer = "minecraft:clay_ball";         Category = "bog";      Smelt = "minecraft:clay_ball";            Inner = "minecraft:clay";              Primary = 0xA4A8B8; Exp = 0.2; Label = "Clay" }
    @{ Name = "leather";             Primer = "minecraft:leather";           Category = "bog";      Smelt = "minecraft:leather";              Inner = "minecraft:brown_terracotta";  Primary = 0x9C6B3F; Exp = 0.2; Label = "Leather" }
    @{ Name = "feather";             Primer = "minecraft:feather";           Category = "bog";      Smelt = "minecraft:feather";              Inner = "minecraft:white_wool";        Primary = 0xEFEFEF; Exp = 0.2; Label = "Feather" }
    @{ Name = "dirt";                Primer = "minecraft:dirt";              Category = "bog";      Smelt = "minecraft:dirt";                 Inner = "minecraft:dirt";              Primary = 0x9B7653; Exp = 0.1; Label = "Dirt" }
    @{ Name = "mud";                 Primer = "minecraft:mud";               Category = "bog";      Smelt = "minecraft:mud";                  Inner = "minecraft:mud";               Primary = 0x4A433D; Exp = 0.1; Label = "Mud" }
    @{ Name = "moss";                Primer = "minecraft:moss_block";        Category = "bog";      Smelt = "minecraft:moss_block";           Inner = "minecraft:moss_block";        Primary = 0x5E7A33; Exp = 0.1; Label = "Moss" }
    @{ Name = "mycelium";            Primer = "minecraft:mycelium";          Category = "bog";      Smelt = "minecraft:mycelium";             Inner = "minecraft:mycelium";          Primary = 0x6E5F69; Exp = 0.1; Label = "Mycelium" }
    # NOTE: lily_pad is a hand-authored Bog variant NOT in this table - its vanilla
    # texture (lily_pad.png) is grayscale foliage (biome-tinted at runtime), so an
    # inner-block bake would render a gray interior. It ships tint-only (no
    # inner_block), green via primary_color 0x6FB04A. See the committed
    # slime_variant/lily_pad.json + the Bog recategorization note in
    # docs/species_as_category_redesign.md.
    @{ Name = "glow_ink_sac";        Primer = "minecraft:glow_ink_sac";      Category = "cave";     Smelt = "minecraft:glow_ink_sac";         Inner = "minecraft:verdant_froglight"; Primary = 0x3DBEB6; Exp = 0.2; Label = "Glow Ink Sac" }
    @{ Name = "obsidian";            Primer = "minecraft:obsidian";          Category = "infernal"; Smelt = "minecraft:obsidian";             Inner = "minecraft:obsidian";          Primary = 0x2A2440; Exp = 0.7; Label = "Obsidian" }
    # echo_shard moved Cave -> Void in v1.4.3 (#118); row synced 2026-06-06 after a stale
    # wholesale run briefly regenerated the old category.
    @{ Name = "echo_shard";          Primer = "minecraft:echo_shard";        Category = "void";     Smelt = "minecraft:echo_shard";           Inner = "minecraft:sculk";             Primary = 0x1E6B72; Exp = 1.0; Label = "Echo Shard" }
    @{ Name = "amethyst";            Primer = "minecraft:amethyst_shard";    Category = "geode";    Smelt = "minecraft:amethyst_shard";       Inner = "minecraft:amethyst_block";    Primary = 0x9D70D0; Exp = 1.0; Label = "Amethyst" }
    @{ Name = "ink_sac";             Primer = "minecraft:ink_sac";           Category = "tide";     Smelt = "minecraft:ink_sac";              Inner = "minecraft:black_concrete";    Primary = 0x26262B; Exp = 0.2; Label = "Ink Sac" }
    @{ Name = "prismarine_crystals"; Primer = "minecraft:prismarine_crystals"; Category = "tide";   Smelt = "minecraft:prismarine_crystals";  Inner = "minecraft:sea_lantern";       Primary = 0xA9DCC9; Exp = 1.0; Label = "Prismarine Crystals" }
    @{ Name = "netherite_scrap";     Primer = "minecraft:netherite_scrap";   Category = "infernal"; Smelt = "minecraft:netherite_scrap";      Inner = "minecraft:ancient_debris";    Primary = 0x70513F; Exp = 1.0; Label = "Netherite Scrap" }
    @{ Name = "glowstone_dust";      Primer = "minecraft:glowstone_dust";    Category = "infernal"; Smelt = "minecraft:glowstone_dust";       Inner = "minecraft:glowstone";         Primary = 0xD9A93F; Exp = 0.7; Label = "Glowstone Dust" }
    @{ Name = "soul_sand";           Primer = "minecraft:soul_sand";         Category = "infernal"; Smelt = "minecraft:soul_sand";            Inner = "minecraft:soul_sand";         Primary = 0x4A3A2E; Exp = 0.7; Label = "Soul Sand" }
    @{ Name = "soul_soil";           Primer = "minecraft:soul_soil";         Category = "infernal"; Smelt = "minecraft:soul_soil";            Inner = "minecraft:soul_soil";         Primary = 0x52423A; Exp = 0.7; Label = "Soul Soil" }
    @{ Name = "netherrack";          Primer = "minecraft:netherrack";        Category = "infernal"; Smelt = "minecraft:netherrack";           Inner = "minecraft:netherrack";        Primary = 0x703434; Exp = 0.7; Label = "Netherrack" }
    # blaze's resource is the ROD, not the powder (changed in #148, shipped v1.10.x):
    # the Froglight is the farmed stand-in for killing blazes, and blazes drop rods -
    # powder is a craft product (1 rod -> 2 powder). primer == smelt-output holds, both
    # moved to blaze_rod together; the v1.8.3 fuel lane already burned this Froglight
    # as a rod. (Originally primed AND smelted as blaze_powder, v1.1-v1.10.)
    @{ Name = "blaze";               Primer = "minecraft:blaze_rod";         Category = "infernal"; Smelt = "minecraft:blaze_rod";            Inner = "minecraft:shroomlight";       Primary = 0xE8A41C; Exp = 0.7; Label = "Blaze" }
    @{ Name = "quartz";              Primer = "minecraft:quartz";            Category = "infernal"; Smelt = "minecraft:quartz";               Inner = "minecraft:quartz_block";      Primary = 0xEAE4DA; Exp = 0.7; Label = "Quartz" }
    @{ Name = "chorus_fruit";        Primer = "minecraft:chorus_fruit";      Category = "void";     Smelt = "minecraft:chorus_fruit";         Inner = "minecraft:purpur_block";      Primary = 0x97709C; Exp = 0.7; Label = "Chorus Fruit" }
    @{ Name = "shulker_shell";       Primer = "minecraft:shulker_shell";     Category = "void";     Smelt = "minecraft:shulker_shell";        Inner = "minecraft:purpur_pillar";     Primary = 0x8E6090; Exp = 0.7; Label = "Shulker Shell" }
)

function Write-Bomless {
    param([string]$path, [string]$content)
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

function Darken {
    param([int]$rgb, [double]$factor)
    $r = [int][Math]::Round((($rgb -shr 16) -band 0xFF) * $factor)
    $g = [int][Math]::Round((($rgb -shr 8) -band 0xFF) * $factor)
    $b = [int][Math]::Round(($rgb -band 0xFF) * $factor)
    return ($r -shl 16) -bor ($g -shl 8) -bor $b
}

foreach ($v in $variants) {
    $name = $v.Name
    $primary = [int]$v.Primary
    $secondary = Darken $primary 0.70
    $resultPath = ($v.Smelt -split ":")[1]

    # 1. slime_variant registry JSON
    $variantJson = @"
{
  "primer_item": "$($v.Primer)",
  "category": "$($v.Category)",
  "primary_color": $primary,
  "secondary_color": $secondary,
  "inner_block": "$($v.Inner)"
}
"@
    Write-Bomless (Join-Path $variantDir "$name.json") ($variantJson + "`n")

    # 2. smelting recipe JSON
    $recipeJson = @"
{
  "type": "minecraft:smelting",
  "ingredient": {
    "type": "neoforge:components",
    "items": [
      "productivefrogs:configurable_froglight"
    ],
    "components": {
      "productivefrogs:slime_variant": "productivefrogs:$name"
    }
  },
  "result": {
    "id": "$($v.Smelt)"
  },
  "experience": $($v.Exp),
  "cookingtime": 200,
  "category": "misc",
  "group": "productivefrogs_froglight"
}
"@
    Write-Bomless (Join-Path $recipeDir "configurable_froglight_${name}_to_${resultPath}.json") ($recipeJson + "`n")

    # 3. milk fluid blockstate JSON (identical shared-model content)
    $blockstateJson = @"
{
  "variants": {
    "": { "model": "productivefrogs:block/slime_milk_fluid" }
  }
}
"@
    Write-Bomless (Join-Path $blockstateDir "${name}_slime_milk.json") ($blockstateJson + "`n")

    # 4. milk bucket item model JSON (only the texture path differs)
    $bucketModelJson = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "productivefrogs:item/${name}_slime_milk_bucket"
  }
}
"@
    Write-Bomless (Join-Path $bucketModelDir "${name}_slime_milk_bucket.json") ($bucketModelJson + "`n")

    Write-Output "wrote 4 files for $name"
}

Write-Output ""
Write-Output "=== PFFluidTypes.VARIANTS additions (insert before vanilla/magma) ==="
$names = ($variants | ForEach-Object { '"' + $_.Name + '"' }) -join ", "
Write-Output $names

Write-Output ""
Write-Output "=== lang: item.productivefrogs.slime_bucket.<v> ==="
foreach ($v in $variants) { Write-Output ("  `"item.productivefrogs.slime_bucket.$($v.Name)`": `"Bucket of $($v.Label) Slime`",") }

Write-Output ""
Write-Output "=== lang: item.productivefrogs.<v>_slime_milk_bucket ==="
foreach ($v in $variants) { Write-Output ("  `"item.productivefrogs.$($v.Name)_slime_milk_bucket`": `"Bucket of $($v.Label) Slime Milk`",") }

Write-Output ""
Write-Output "=== lang: block.productivefrogs.<v>_slime_milk ==="
foreach ($v in $variants) { Write-Output ("  `"block.productivefrogs.$($v.Name)_slime_milk`": `"$($v.Label) Slime Milk`",") }

Write-Output ""
Write-Output "=== lang: fluid_type.productivefrogs.<v>_slime_milk ==="
foreach ($v in $variants) { Write-Output ("  `"fluid_type.productivefrogs.$($v.Name)_slime_milk`": `"$($v.Label) Slime Milk`",") }

Write-Output ""
Write-Output "=== lang: block.productivefrogs.configurable_froglight.<v> ==="
foreach ($v in $variants) { Write-Output ("  `"block.productivefrogs.configurable_froglight.$($v.Name)`": `"$($v.Label) Froglight`",") }

Write-Output ""
Write-Output "=== lang: item.productivefrogs.resource_slime_spawn_egg.<v> ==="
foreach ($v in $variants) { Write-Output ("  `"item.productivefrogs.resource_slime_spawn_egg.$($v.Name)`": `"$($v.Label) Slime Spawn Egg`",") }

Write-Output ""
Write-Output "=== lang: entity.productivefrogs.resource_slime.<v> ==="
foreach ($v in $variants) { Write-Output ("  `"entity.productivefrogs.resource_slime.$($v.Name)`": `"$($v.Label) Slime`",") }

Write-Output ""
Write-Output "done -- $($variants.Count) variants x 4 JSON files"
