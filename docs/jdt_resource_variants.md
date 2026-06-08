# Just Dire Things - Resource Slime Variants

> Spec for adding [Just Dire Things](https://www.curseforge.com/minecraft/mc-mods/just-dire-things)
> (mod id `justdirethings`, 1.21.1 / NeoForge) resources as cross-mod Resource Slime variants.
> This is a **variant-pool addition** (the v1.2 cross-mod feature family - see
> [cross_mod_compat.md](./cross_mod_compat.md)), not a crush-recipe feature.

## Why variants, not crushing

Just Dire Things has **no crusher, grinder, or ore-doubling machine** (it is an automation/utility
mod - block placers, item/fluid movers, the Paradox duplicator, and the "Goo" resource-growing
system). So it is not a crush-recipe target (see [v1_3_crush_recipes.md](./v1_3_crush_recipes.md)).
What it *does* add is a set of renewable resources, and those map cleanly onto Resource Slime
variants - which is the meaningful way to "support Just Dire Things."

**Good thematic fit:** every JDT resource is renewable via the Goo system (a Goo Block converts an
adjacent vanilla resource block into a JDT ore over time, then the ore is mined for the resource).
None are crafting-table alloys - even "Eclipse Alloy" is a mined/grown ore in JDT - so "a slime farms
it" holds for all of them.

## Core variants (recommended)

Four resources spanning four categories. All gated by `neoforge:conditions -> mod_loaded:
justdirethings` (the entries reference JDT items/blocks, so the whole variant should only load when
JDT is present - same pattern as the other cross-mod variants).

| Variant | Category | Primer | Smelt-back result | Theme / color (approx, tune in `runClient`) |
|---|---|---|---|---|
| `ferricore` | CAVE | `primer_tag: c:ingots/ferricore` | `justdirethings:ferricore_ingot` | T1 base metal, pale steel-teal (`~#8FB6B0` / `~#5E7E7A`) |
| `blazegold` | INFERNAL | `primer_tag: c:ingots/blazegold` | `justdirethings:blazegold_ingot` | blaze+gold, fiery orange-gold (`~#E8902A` / `~#B94E18`) |
| `celestigem` | GEODE | `primer_item: justdirethings:celestigem` | `justdirethings:celestigem` | celestial gem, cyan-green (`~#39D7BE` / `~#1C8F7E`) |
| `eclipsealloy` | VOID | `primer_tag: c:ingots/eclipsealloy` | `justdirethings:eclipsealloy_ingot` | eclipse/endgame, dark navy-black (`~#2B2F58` / `~#12152E`) |

**Primer note:** ferricore / blazegold / eclipsealloy each have a clean per-resource common tag
(`c:ingots/<name>`), so use `primer_tag` (preferred). **Celestigem has no `c:gems/celestigem`
sub-tag** - it only sits in the shared `c:gems` tag - so use `primer_item:
justdirethings:celestigem` (an exact item; do not key off `c:gems`, which any mod's gem would match).

**`inner_block`** (the block rendered inside the slime): use each resource's storage block, which
resolves because the variant is JDT-gated -
`justdirethings:ferricore_block` / `blazegold_block` / `celestigem_block` / `eclipsealloy_block`.

## Optional 5th variant

| Variant | Category | Primer | Smelt-back | Notes |
|---|---|---|---|---|
| `time_crystal` | GEODE (or VOID) | `primer_item: justdirethings:time_crystal` | `justdirethings:time_crystal` | bright green crystal (`~#52DE5C` / `~#2BA53A`); renewable via a budding-amethyst-style cluster. Lower priority - different mechanic from the Goo ores, no `c:` tag. |

## Fuel slimes (shipped - maintainer rulings 2026-06-08)

Originally excluded ("a slime made of coal is a weak fit"), revisited after the core four shipped.
The fit that works is **the fuel fluid, not the coal**: each fuel slime's Froglight melts in the
Froglight Crucible directly into JDT's **refined** fuel fluid - skipping JDT's own production chain
(coal + Polymorphic Fluid -> unrefined -> Goo-refined) deliberately; the frog loop is the shortcut,
that is the mod's premise.

| Variant | Category | Primer | Crucible melt output | Yield |
|---|---|---|---|---|
| `blaze_ember` | INFERNAL | `primer_item: justdirethings:coal_t2` | `justdirethings:refined_t2_fluid_source` | 1,000 mB |
| `voidflame` | VOID | `primer_item: justdirethings:coal_t3` | `justdirethings:refined_t3_fluid_source` | 1,000 mB |
| `eclipse_ember` | VOID | `primer_item: justdirethings:coal_t4` | `justdirethings:refined_t4_fluid_source` | 1,000 mB |

Rulings, recorded:

- **Refined fuel only, no coal.** No furnace smelt recipe for any fuel slime - the fluid is the
  sole cash-out, same precedent as the water/lava variants (whose resource IS the fluid). The coal
  items stay JDT-side (renewable there via Goo).
- **No T1 slime.** JDT registers fuel fluids for tiers 2-4 only (verified in
  justdirethings-1.5.7: `refined_t2/t3/t4_fluid_source`); there is no T1 fluid, so Primal Coal
  gets no slime.
- **Categories:** T2 -> INFERNAL (fire lineage with blazegold), T3 + T4 -> VOID (End-origin
  default; the gems-under-Geode ruling that moved celestigem does not apply to coals).
- **Yield: 1,000 mB per Froglight** (one bucket) - matches the water/lava bulk-fluid precedent and
  JDT's own ratio (one coal converts one 1,000 mB Polymorphic fluid block into unrefined fuel).
  Bulk melt job (`~6,700 / heat` ticks), fits the 4,000 mB tank. Tunable if playtests say so.
- Primers are the coal items (exact `primer_item` - JDT coals carry no `c:` tag, same situation as
  celestigem). Colors are texture-faithful midtone/bright medians from the coal_t2/t3/t4 item
  textures (JDT tints a shared greyscale fluid texture in code, so the coals are the per-tier art).

## Excluded

- **Primal Coal** (`coal_t1`) - no JDT T1 fluid exists to melt into (see fuel slimes above).
- **Goo blocks / soil, storage blocks, dimensional fluids** - infrastructure, not farmable resources.

## Per-variant authoring

Each variant is the same shape as the shipped cross-mod variants (see any of
`data/productivefrogs/productivefrogs/slime_variant/*.json`):

    {
      "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "justdirethings" } ],
      "primer_tag": "c:ingots/ferricore",
      "category": "cave",
      "primary_color": 9418416,
      "secondary_color": 6192762,
      "inner_block": "justdirethings:ferricore_block"
    }

(`primary_color` / `secondary_color` are decimal `0xRRGGBB`; the table lists hex. Use `primer_item`
instead of `primer_tag` for celestigem and time_crystal.)

Plus, per variant, a smelt-back recipe mirroring the existing `configurable_froglight_<variant>.json`
(a `neoforge:components` ingredient matching `slime_variant: productivefrogs:<variant>` ->
the JDT ingot/gem), also `mod_loaded: justdirethings`-gated.

## Generation

Only 4-5 entries, so either hand-author them or add a small `justdirethings` block to the existing
cross-mod variant generator (`scripts/generate_cross_mod_variants.ps1`). Use BOMless UTF-8 writes
(PowerShell 5.1 `Set-Content -Encoding utf8` adds a BOM that breaks JSON).

## Dependency on the lang fallback

These variants are component-driven, so their display names rely on the variant-id title-case
fallback. That fallback fix already shipped (the JEI title-case fallback plus explicit `en_us.json`
keys, guarded by a lang-completeness test - see "Recently resolved" in
[known_issues.md](./known_issues.md)), so the raw-lang-key tooltip bug is no longer a blocker. JDT
variants only need their own explicit
`entity.productivefrogs.resource_slime.{ferricore,blazegold,celestigem,eclipsealloy}` lang entries
to satisfy the lang-completeness gate and read correctly in-game.

## Verification

GameTest can't cover this (JDT is absent in CI). Smoke-test in a `runClient` instance with Just Dire
Things installed: prime a slime with each resource, confirm the variant resolves, the slime renders
with the right tint + inner block, the frog drops the variant Froglight, and it smelts back to the
JDT ingot/gem. Confirm the variants silently absent themselves when JDT is not installed.
