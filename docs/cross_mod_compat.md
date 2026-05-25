# Cross-Mod Compatibility

Strategy for supporting other popular NeoForge 1.21.1 mods' resources as Resource
Slime variants, with no hard dependencies, no crashes when a mod is absent, and
(for the bulk of it) no per-mod Java or per-mod files.

> **Status:** plan of record (researched 2026-05-25). Supersedes the earlier
> per-mod-tables draft. The load-bearing change versus that draft: key cross-mod
> variants off **common tags** with a new `primer_tag`, not off one mod's exact
> item id. Builds directly on the data-driven variant refactor (PR #108) - see
> [refactor_data_driven_variants.md](./refactor_data_driven_variants.md). Nothing
> here is implemented yet.

## TL;DR

- The cross-mod "library" is the NeoForge **`c:` common tag convention**
  (`c:ingots/<metal>`, `c:gems/<gem>`, `c:dusts/<x>`). It is not a mod you depend
  on; it is a tag standard shipped in NeoForge core that every serious mod
  self-tags into. Key a variant off `c:ingots/tin` and it is satisfied by
  Mekanism OR Thermal OR AllTheOres OR anything else that provides tin, with zero
  per-mod code, including mods we have never heard of.
- **AllTheOres is not a unifying layer.** It is a parallel ore-set mod that tags
  cleanly into `c:`. Installing it does not hand us Mekanism's metals; rather,
  everyone's tin merges via the shared `c:ingots/tin` tag. So we do not target
  AllTheOres specifically - we target the tags, and ATO (plus Mekanism, Thermal,
  Create, ...) falls out for free.
- The approach is **curate a generous list keyed on common tags**, not
  auto-generate from whatever is installed. Per-variant colour/species/identity
  is always hand-authored (it cannot be derived from a tag); only the item
  matching is tag-driven. This is exactly how Productive Bees and Mystical
  Agriculture do it (see Prior art).

## The mechanism: `c:` common tags

- Namespace is `c:` (NeoForge + Fabric share it verbatim). `forge:` is legacy
  1.20.1-and-earlier; emit and read `c:` only on 1.21.x. Tag files live at the
  singular path `data/<ns>/tags/item/...`.
- Load-bearing resource tags (the "this item represents resource X" signals):
  `c:ingots/<metal>`, `c:gems/<gem>`, and secondarily `c:raw_materials/<metal>`,
  `c:storage_blocks/<x>`, `c:dusts/<x>`. Skip `c:nuggets`, `c:gears`, `c:plates`,
  `c:rods` for primers - they are sub-units or fabricated parts of an identity
  already captured by the ingot/gem (including them would double-count one metal
  as several "resources").
- A reference to a tag that no installed mod populates is simply an **empty
  tag** (harmless). A hard reference to a specific absent item (`mekanism:...`)
  errors - so keep `required: false` on item entries and `neoforge:conditions ->
  mod_loaded` wrappers for the bespoke cases.
- **Semantic-correctness caveat:** a few mods mis-tag (historically Mythic Metals
  put storage blocks in `c:ores`). Keying off the most-policed trees
  (`c:ingots/`, `c:gems/`) minimises exposure; do not key off `c:ores` /
  `c:storage_blocks` for resource identity.

## What the variant model needs (the only new engineering)

Thanks to PR #108, a cross-mod variant needs **no textures** (category-texture
fallback + tinted shell), **no lang** (title-cased name fallback), and **no
per-variant registration**. Three additions close the gap to "tag-driven,
datapack-only":

1. **`primer_tag` on `SlimeVariant`** (optional, item-or-tag like a recipe
   ingredient). `SlimeInfusionHandler` (and `EggPrimerHandler`) resolve via the
   tag when present, else fall back to the existing exact `primer_item`. A
   cross-mod `tin` variant keys on `c:ingots/tin` and is primed by any mod's tin
   ingot. Vanilla variants keep `primer_item` (one canonical item).
2. **`tag_empty` gating instead of `mod_loaded` OR-lists.** Wrap a cross-mod
   variant file in `NOT(neoforge:tag_empty: "c:ingots/tin")` so it activates for
   ANY providing mod and stays inert (out of JEI / creative) when none is
   installed. Reserve `mod_loaded` for files that reference a specific mod's
   block/item (e.g. a bespoke `inner_block` or a no-common-tag signature item).
3. **Output (smelt-back) item resolution.** A recipe must emit a concrete item,
   not a tag. Add an optional `result_item` to the variant (the preferred output,
   e.g. `alltheores:tin_ingot`) with a "first item in `c:ingots/tin`" fallback,
   so a pack can override which tin ingot the Froglight smelts to. The
   Froglight-to-resource recipe is generated per variant, gated the same way.

These are small: the infusion handler already does a registry scan; add a
tag-membership branch. Everything else is JSON.

## Authoring workflow

Generate the cross-mod pool from a compact data table (extend
`scripts/generate_v1_1_variants.ps1`), one row per
`{resource, primer_tag, species, primary_color, secondary_color, result_item}`,
emitting the `slime_variant` JSON + the smelting recipe + the `tag_empty`
condition wrapper. This mirrors Productive Bees' datagen builder list: the source
of truth stays a small table, not dozens of hand-written files.

## Curated shortlist (first pass), by species

Tier 1 is tag-driven (one entry each, auto-catches every providing mod). Tier 2
is bespoke signature materials that "sell" the feature but need explicit handling
(no clean common tag).

### CAVE (overworld metals) - the overlap winners, build first
| Resource | Tag | Providing mods |
|---|---|---|
| tin | `c:ingots/tin` | Mekanism, Thermal, AllTheOres |
| lead | `c:ingots/lead` | Mekanism, Thermal, AllTheOres |
| osmium | `c:ingots/osmium` | Mekanism, AllTheOres |
| nickel | `c:ingots/nickel` | Thermal, AllTheOres |
| silver | `c:ingots/silver` | Thermal, AllTheOres |
| zinc | `c:ingots/zinc` | Create, AllTheOres |
| aluminum | `c:ingots/aluminum` | AllTheOres, Thermal-adjacent |
| uranium | `c:ingots/uranium` | Mekanism, AllTheOres |

### GEODE (gems/crystals)
| Resource | Tag | Providing mods |
|---|---|---|
| certus quartz | `c:gems/certus_quartz` | AE2 |
| fluix | `c:gems/fluix` | AE2 (judgment call: GEODE vs VOID by lore) |
| fluorite | `c:gems/fluorite` | Mekanism (verify) |
| apatite | `c:gems/apatite` | Thermal |
| silicon | `c:silicon` | AE2 + Refined Storage (one tag covers both) |

### INFERNAL (nether/fire)
- sulfur (`c:dusts/sulfur`, broad overlap, brimstone flavour) - sleeper Tier-1 pick.
- signalum, lumium (Thermal alloys; energetic/glow read). Alloy-tag caveat applies.

### VOID (end/arcane)
- enderium (Thermal; marquee VOID pick - verify `c:ingots/enderium`).
- Powah crystals (blazing/niotic/spirited/nitro) - bespoke, recognisable endgame.
- Mythic Metals fantasy metals (orichalcum, mythril, ...) - bespoke.

### BOG (swamp + mob drops)
- **pink slime** (`industrialforegoing:pink_slime_ball`) - the must-have slime
  joke; top BOG pick. Bespoke (no common tag).
- Mystical Agriculture essences (inferium -> supremium tiers) - bespoke, iconic.

### TIDE (aquatic) - the weak species
Modded tech has almost no aquatic resources. The one strong pick is Mythic
Metals' Aquarium (`mythicmetals:aquarium_ingot`, ocean-only ore). TIDE otherwise
leans on vanilla aquatic content. Flagged as a design gap, not a blocker.

## Open decisions (recommendations baked in; confirm before authoring)

- **Alloys** (bronze, brass, electrum, invar, constantan, signalum, lumium,
  enderium, steel): tag inconsistently across mods. *Recommendation:* include
  them keyed on `c:ingots/<alloy>` with `required: false` so they catch packs
  that tag them, and accept that some will not resolve (the variant stays inert).
- **Output-item resolution:** *Recommendation:* per-variant `result_item` with a
  "first in tag" fallback (pack-overridable), not bare "first in tag"
  (nondeterministic when multiple mods are present).
- **Scope of the first PR:** *Recommendation:* ship the 8-ish Tier-1 CAVE metals
  plus the GEODE gems first (cheap, maximum coverage), and treat the bespoke
  signature items (pink slime, enderium, Powah crystals, MA essences) as a
  fast-follow once `primer_tag` is in.
- **TIDE gap:** accept TIDE as the smallest species (vanilla-leaning) for now.

## Datapack override path for modpack authors

No code touch required for any of this:

1. **Add a variant** - drop a JSON at
   `data/<modpack>/productivefrogs/slime_variant/<name>.json` with `primer_tag`
   (or `primer_item`), `category`, the two colours, and optionally `result_item`
   / `spawn_entity`. Add a matching Froglight smelting recipe JSON.
2. **Disable a variant** - override its file with
   `{ "neoforge:conditions": [{ "type": "neoforge:false" }] }`.
3. **Re-point the canonical output** - override the variant's `result_item` (e.g.
   force `c:ingots/tin` to smelt to a specific mod's ingot).
4. **Wire a modded slime mob into discovery** - drop a `parent_species` JSON.

## Crushing compat - Froglight to 2x powder (separate feature)

V1 does not ship a crusher. The 2x path needs a processing mod. We ship
conditional `mod_loaded` compat recipes for the popular options:

| Mod | Block | Recipe |
|---|---|---|
| Create | Crushing Wheels / Millstone | 1 metallic Froglight to 2 crushed (Create's tag) |
| Mekanism | Crusher / Enrichment Chamber | 1 metallic Froglight to 2 dust |
| Thermal | Pulverizer | 1 metallic Froglight to 2 pulverized |

Recipes ship as JSON under `data/productivefrogs/recipe/<modid>/...` wrapped in
`neoforge:conditions -> mod_loaded`. Tag we publish for crusher mods to target:
`productivefrogs:crushable/metallic` (all metallic Froglight items, including
modded ones added by datapack). Metals only - gems/organics have no "crushed to
ingot" pipeline.

## Prior art (why this approach)

- **Productive Bees** (the mod PF is modeled on) hand-authors one entry per
  resource via a compact datagen builder list, and gates cross-mod entries with
  `requireTag("c:...")` (emits `NOT(tag_empty)`) for mod-agnostic resources and
  `requireMod(...)` only when it references a specific mod's block. We mirror that
  split exactly.
- **Mystical Agriculture** curates ~130 crops (hardcoded), keys their ingredients
  off item-or-tag (`c:ingots/iron`), and resolves tag-to-concrete-item outputs
  through a runtime, pack-editable map. Confirms: curate the list, tag-drive the
  edges, never auto-mint.
- Neither does runtime tag-scanning to invent variants, because colour/species
  cannot be derived from a tag. "Support as much as possible natively" = a
  generous curated list keyed on common tags.

## Sources

- NeoForge Tags (1.21.1): https://docs.neoforged.net/docs/1.21.1/resources/server/tags/
- NeoForge `Tags.Items` javadoc (the standard `c:` tree)
- AllTheOres (CurseForge "ATO") + its GitHub `c:` tag generation (parallel ore set)
- Productive Bees `BeeProvider` (requireTag / requireMod builders)
- Mystical Agriculture `ModCrops` + Cucumber tags-config (tag-to-item resolver)
- AE2 tag system (certus_quartz / fluix / silicon), Thermal Foundation materials,
  Mekanism wiki (osmium / fluorite), Create wiki (zinc / brass), Mythic Metals
  ore list, Industrial Foregoing pink slime tag issue #1516
