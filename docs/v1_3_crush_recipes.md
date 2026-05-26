# v1.3 - Cross-Mod Crush Recipes

> Implementation spec for the v1.3 release: optional, `mod_loaded`-gated recipes that let an
> installed crusher mod turn a metal Froglight into 2x its resource. The cross-mod rationale,
> the source-verification of every recipe shape, and the per-mod findings live in
> [cross_mod_compat.md](./cross_mod_compat.md) (Crushing compat section). **This doc is the
> build plan**: what to generate, how, and how to verify it.

> **Status: implemented (in-tree, pre-release).** `scripts/generate_crush_recipes.ps1` generates
> 33 recipes (11 per crusher) under `data/productivefrogs/recipe/<modid>/`; `CrushRecipeTest` pins
> their JSON shape and the GameTest server confirms they load cleanly (gated out) without the
> crushers installed. The metals with no in-scope dust source - mythril, orichalcum, brass,
> refined_obsidian - are skipped by the generator (no recipe), as the precedence rules require.
> The manual per-mod `runClient` smoke test in [Testing](#testing) is the remaining pre-release gate.

## Goal

Give players a soft 2x incentive to route Froglights through a processing mod, without ever
requiring one:

- **No crusher mod:** smelt the Froglight in a vanilla furnace -> **1 ingot**. Always works (ships in v1.0).
- **With a crusher mod:** crush the Froglight -> **2 dust** -> smelt the dust -> **2 ingots**.

The crush path is strictly optional and additive. A pack without any crusher mod sees no change.

## Scope

**In scope (v1.3):**
- Generated `mod_loaded`-gated crush recipes for **Mekanism**, **Immersive Engineering**, and **EnderIO**.
- **AllTheOres** as the dust + smelt-back fallback layer (not a crusher).
- **Metal variants only.**

**Out of scope:**
- Native in-house crusher block -> **V2** (so the 2x path works with zero external mods).
- **Create** and **Actually Additions** -> deferred (see cross_mod_compat.md for why; both accept the
  ingredient but lack a native metal-dust pipeline). Easy to add later, same recipe family.
- **Just Dire Things** (no crusher) and **Thermal** (no 1.21.1 build) -> ruled out.
- Non-metal Froglights (coal, redstone, lapis, diamond, emerald, gems, organics) -> no crush recipe;
  they pass through a crusher unchanged. There is no "dust -> resource" doubling pipeline for them.

## Why no `crushable` item tag

Every variant is the **same** `configurable_froglight` item; the resource rides in its
`slime_variant` data component. An item tag operates on items, so it cannot select "metal variants"
- it would tag every variant at once. The earlier `productivefrogs:crushable/metallic` tag plan is
therefore dead. Crush recipes match **per-variant** via NeoForge's data-component ingredient, exactly
like the existing smelt recipes (`configurable_froglight_<variant>.json`).

## Ingredient mechanism (verified)

Match the Froglight with NeoForge's data-component ingredient. **On 1.21.1 the key is
`"type": "neoforge:components"`** (the `neoforge:ingredient_type` key is 1.21.4+). `strict`
defaults to `false` (partial match), which is what we want - match on the `slime_variant` component,
ignore any others:

    {
      "type": "neoforge:components",
      "items": "productivefrogs:configurable_froglight",
      "components": { "productivefrogs:slime_variant": "productivefrogs:iron" }
    }

This works in all three target mods because each deserializes its recipe input with vanilla
`Ingredient.CODEC`, which dispatches on `"type"` to NeoForge's ingredient registry and so accepts
`neoforge:components` transparently. Source-verified: Mekanism `ItemStackIngredient` =
`SizedIngredient.FLAT_CODEC`; IE `CrusherRecipe.input` = `DualCodecs.INGREDIENT`; EnderIO
`SagMillingRecipe.input` = `Ingredient.CODEC_NONEMPTY`.

## Recipe templates (per mod)

All recipes wrap a `neoforge:conditions -> mod_loaded` block and live under
`data/productivefrogs/recipe/<modid>/<variant>.json`.

**Mekanism** (Enrichment Chamber, `mekanism:enriching` - the canonical ore-doubling machine):

    {
      "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "mekanism" } ],
      "type": "mekanism:enriching",
      "input": {
        "type": "neoforge:components",
        "items": "productivefrogs:configurable_froglight",
        "components": { "productivefrogs:slime_variant": "productivefrogs:iron" }
      },
      "output": { "count": 2, "id": "mekanism:dust_iron" }
    }

**Immersive Engineering** (Crusher, `immersiveengineering:crusher`; `energy` is the RF cost,
3000 matches IE's own ingot-crush recipes):

    {
      "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "immersiveengineering" } ],
      "type": "immersiveengineering:crusher",
      "energy": 3000,
      "input": {
        "type": "neoforge:components",
        "items": "productivefrogs:configurable_froglight",
        "components": { "productivefrogs:slime_variant": "productivefrogs:iron" }
      },
      "result": { "id": "immersiveengineering:dust_iron", "count": 2 }
    }

**EnderIO** (SAG Mill, `enderio:sag_milling`; set `"bonus": "none"` - the default
`multiply_output` adds grinding-ball RNG we don't want; `outputs` is a list):

    {
      "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "enderio" } ],
      "type": "enderio:sag_milling",
      "energy": 2400,
      "bonus": "none",
      "input": {
        "type": "neoforge:components",
        "items": "productivefrogs:configurable_froglight",
        "components": { "productivefrogs:slime_variant": "productivefrogs:iron" }
      },
      "outputs": [ { "item": { "count": 2, "id": "enderio:powdered_iron" } } ]
    }

## Output resolution (the matrix)

For each (crusher mod, metal variant) pair, the generator picks the output by precedence:

1. **The crusher mod's own dust**, if it ships one for that metal -> gate on `mod_loaded: <crusher>` only.
2. **Else `alltheores:<metal>_dust`**, if ATO has it -> gate on `mod_loaded: <crusher>` **and** `mod_loaded: alltheores`.
3. **Else** no recipe for that pair.

Outputs must be **concrete item ids** (none of the three crushers accept a tag in the result), so the
generator pins the exact dust per (mod, metal). The smelt-back is never PF's job: Mekanism, IE, and
EnderIO each ship a `<their dust> -> ingot` furnace recipe, and ATO's `c:dusts/<metal> -> ingot` smelt
backstops anything tagged into `c:dusts/<metal>` (all four mods' dusts are).

### Per-mod native dust sets (confirm against each mod's `c:dusts/*` at generation time)

- **Mekanism** `mekanism:dust_<metal>`: iron, copper, gold, osmium, tin, lead, uranium.
- **Immersive Engineering** `immersiveengineering:dust_<metal>`: iron, copper, aluminum, lead, silver, nickel, uranium (+ alloy grits). *Re-verify IE's exact grit set before generating.*
- **EnderIO** `enderio:powdered_<metal>`: iron, gold, copper, tin (metals; also non-metal powders).
- **AllTheOres** `alltheores:<metal>_dust`: the full range - aluminum, copper, gold, iron, lead, nickel, osmium, platinum, silver, tin, uranium, zinc, iridium (+ alloy dusts). Widest set; the fallback.

The generator should hardcode a verified `{mod -> set-of-metals-with-dust}` map rather than guess; the
sets above are the research baseline.

## The crushable metals list

Metal-ness is **not** derivable from `Category` (CAVE also holds coal, redstone, lapis, diamond,
emerald, obsidian, etc.), so the crushable set is a **curated list in the generator**. It is the metal
subset of the shipped variants:

- **Vanilla:** iron, copper, gold.
- **Cross-mod:** aluminum, tin, lead, nickel, osmium, silver, zinc, uranium, mythril, orichalcum, brass, refined_obsidian.

(This is the metals among the shipped `slime_variant` set; non-metal CAVE variants and all GEODE/BOG/
TIDE/INFERNAL/VOID variants are excluded.) When a new metal variant is added, add it here too.

## Generator

A PowerShell script, sibling to the existing variant generators - `scripts/generate_crush_recipes.ps1`.

- **Inputs (hardcoded in the script):** the crushable metals list; the per-mod native-dust maps; the
  ATO dust set; per-mod recipe templates (Mekanism/IE/EnderIO field shapes + energy values).
- **Logic:** for each crusher mod, for each crushable metal, resolve the output per the precedence
  above and emit the recipe JSON (skip the pair if neither the crusher nor ATO has the dust).
- **Output path:** `data/productivefrogs/recipe/<modid>/<variant>.json` (singular `recipe/`, the
  1.21.1 convention).
- **Encoding:** write BOMless UTF-8 via `[System.IO.File]::WriteAllText` (PowerShell 5.1's
  `Set-Content -Encoding utf8` writes a BOM that breaks JSON parsing). No em-dashes / en-dashes in
  generated content.
- Re-run after adding a metal variant or changing a per-mod dust map.

## Gating

- Single-crusher recipes (native dust path): one `neoforge:mod_loaded` condition on the crusher.
- ATO-fallback recipes: two conditions - the crusher **and** `alltheores`. Both must hold.
- No `tag_empty` conditions (NeoForge forbids them in this context; we gate on `mod_loaded`, consistent
  with the rest of the cross-mod compat - see cross_mod_compat.md).

## Testing

**These recipes are NOT GameTest-covered.** CI runs without Mekanism/IE/EnderIO/ATO installed, so a
`mod_loaded`-gated recipe never loads there - there is nothing for an in-world GameTest to exercise, and
adding the mods as test deps would violate the no-hard-mod-dependency rule. Verification is therefore:

1. **JSON validity** - the generator emits well-formed JSON; a lightweight unit test can assert each
   generated file parses and has the expected top-level keys. (Optional but cheap.)
2. **Manual smoke test in a dev instance**, per mod, before release:
   - Drop the relevant crusher mod (+ optionally ATO) into a dev `runClient` instance.
   - Confirm the recipe loads with no datapack error in `latest.log`.
   - Crush an iron `configurable_froglight`; confirm 2x dust out; smelt to 2 ingots.
   - **Mekanism first**: Mekanism never uses a component ingredient in its own datagen, so confirm its
     recipe loader accepts the nested `neoforge:components` input (it should - standard `Ingredient`
     codec - but this is the one unverified runtime path). If Mekanism chokes, fall back to
     `mekanism:crushing` (Crusher), which has identical field names.
3. Spot-check one ATO-fallback recipe (e.g. IE crushing tin -> `alltheores:tin_dust`) with IE + ATO
   present.

Record the smoke-test results in the PR description (this is a visual/runtime-only feature, like the
client-tint work - GameTest is blind to it).

## File layout

    data/productivefrogs/recipe/mekanism/<metal>.json
    data/productivefrogs/recipe/immersiveengineering/<metal>.json
    data/productivefrogs/recipe/enderio/<metal>.json
    scripts/generate_crush_recipes.ps1

## Open items / decisions

- **Re-verify the IE grit set** (the exact metals IE ships `dust_<metal>` for) before generating - the
  research baseline above is partial.
- **EnderIO `bonus` semantics** - confirm `"bonus": "none"` yields a flat 2x with no grinding-ball
  interaction during the smoke test.
- **Create / Actually Additions** - revisit if we decide a flat "crush -> 2x ingot" exception (Create)
  or an ATO-paired path (AA) is worth the inconsistency. Not in v1.3.
- **JEI display** - if we later want JEI to show "crushable" on the metal Froglights, add a `crushable`
  boolean to `SlimeVariant`; not needed for recipe generation.
