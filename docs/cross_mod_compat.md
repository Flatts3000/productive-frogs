# Cross-Mod Compatibility

Strategy for supporting other popular NeoForge 1.21.1 mods' resources as Resource
Slime variants, with no hard dependencies, no crashes when a mod is absent, and
(for the bulk of it) no per-mod Java or per-mod files.

> **Status:** IMPLEMENTED (2026-05-25). 24 cross-mod variants ship, primed off
> `primer_tag` (common tags) where one exists, else `primer_item`. Two findings
> changed the plan during implementation: (1) NeoForge forbids `tag_empty`
> conditions on datapack-registry entries, so gating is `mod_loaded(provider)`
> not `tag_empty` (see the mechanism note below); (2) **Thermal Series has no
> 1.21.1 release and is not in ATM10**, so its picks (apatite, sulfur, signalum,
> lumium, enderium) are deferred until it ports. Builds on the data-driven
> variant refactor (PR #108).

> **Modpack authors:** [modpack_integration.md](./modpack_integration.md) is the consolidated, pack-facing entry point (config flags, variants, Spawnery primers, crush yields, the drop-in guarantee). This doc is the deep dive on the cross-mod variant strategy specifically.

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
2. **`mod_loaded(provider)` gating** on the variant JSON + its smelt recipe.
   (This was planned as `tag_empty`, but NeoForge **forbids tag-based conditions
   when loading datapack-registry entries** - registries load before tags, so
   `tag_empty` on a `slime_variant` entry throws "tag-based conditions not
   permitted in this context". Verified at boot.) So each cross-mod variant +
   its smelt recipe is gated `mod_loaded` on the provider mod whose verified item
   the smelt outputs (e.g. `alltheores` for the metals). The trade-off: a variant
   loads only when its canonical provider is present, not for any tin-providing
   mod. For the ATM10 target that is fine. The **primer is still tag-driven**:
   `primer_tag` resolves at infusion time (after tags load), so once a variant is
   loaded it is primed by ANY mod's item in that tag.
3. **Output (smelt-back) item resolution.** A recipe must emit a concrete item,
   not a tag. Add an optional `result_item` to the variant (the preferred output,
   e.g. `alltheores:tin_ingot`) with a "first item in `c:ingots/tin`" fallback,
   so a pack can override which tin ingot the Froglight smelts to. The
   Froglight-to-resource recipe is generated per variant, gated the same way.

These are small: the infusion handler already does a registry scan; add a
tag-membership branch. Everything else is JSON.

## Known limitations (and the V1.2 resolution path)

- **Canonical-provider gap.** Because `tag_empty` is forbidden on datapack-registry
  entries, each variant is `mod_loaded(provider)`-gated on ONE canonical mod, while
  its primer is tag-driven. So a pack with, say, Mekanism tin but **not** AllTheOres
  gets no `tin` variant - the variant never loads, even though `c:ingots/tin` is
  populated and the `primer_tag` would have matched. For the ATM10 target this never
  bites (ATM10 ships AllTheOres, so every canonical provider is present). The clean
  fix for the broader "any NeoForge pack" audience is a `neoforge:or` of providers
  per resource (load if `alltheores` OR `mekanism` OR ... is present) plus the same
  OR on the output item; deferred to V1.2 as it is unnecessary for the launch target.
- **Smelt-back picks one provider's item.** A `tin` Froglight always smelts to
  `alltheores:tin_ingot` even if you primed with Mekanism tin. Harmless (both are in
  `c:ingots/tin`), and a pack can re-point the recipe (see override path below).
- **Discovery weight is uniform.** Cross-mod variants all use `weight = 1`, so a heavy
  pack widens each category's discovery pool and lowers any single resource's odds
  proportionally. This is intentional (more mods installed = more variety), not a bug;
  the `weight` field is available per variant if a pack wants to bias the pool.
- **Primer precedence.** When one stack satisfies both an exact `primer_item` variant
  and a `primer_tag` variant, the exact item wins deterministically (see
  `SlimeVariant.findByPrimer`). Two variants colliding purely on overlapping
  `primer_tag` resolve to the first in registry order - a tag-vs-tag collision is a
  datapack authoring conflict the resolver cannot disambiguate.
- **Boundary validation.** A variant JSON with neither `primer_item` nor `primer_tag`
  is rejected by the codec at datapack load (it could never be primed yet would still
  enter the discovery pool). The error names the offending file.

## Authoring workflow

`scripts/generate_cross_mod_variants.ps1` generates the pool from a compact data
table, one row per `{name, category, tag|primer_item, provider mod, result item,
colors}`, emitting the `slime_variant` JSON + the Froglight smelt recipe, both
wrapped in a `mod_loaded(provider)` condition. This mirrors Productive Bees'
datagen builder list: the source of truth stays a small table, not dozens of
hand-written files. Re-run it after editing the table.

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

Bespoke CAVE additions (exact-item primers, no common tag; added 2026-06-06,
ids + texture-faithful colors verified against Powah-6.2.10):
- **energized steel** (`powah:steel_energized`) - Powah's entry-tier material,
  placed CAVE on the steel lineage (iron + gold, energized), same call as
  Mekanism steel.
- **uraninite** (`powah:uraninite`, the post-smelt crystal item) - Powah's mined
  base ore and the skyblock-critical resource (#146): no ore gen in a skyblock
  pack means Powah cannot bootstrap without it.
- **ferricore** (`c:ingots/ferricore` -> `justdirethings:ferricore_ingot`) - Just
  Dire Things' T1 overworld metal, iron-adjacent (#188; tag verified against
  justdirethings-1.5.7, colors texture-faithful).

### GEODE (gems/crystals)
| Resource | Tag | Providing mods |
|---|---|---|
| certus quartz | `c:gems/certus_quartz` | AE2 |
| fluix | `c:gems/fluix` | AE2 (judgment call: GEODE vs VOID by lore) |
| fluorite | `c:gems/fluorite` | Mekanism |
| silicon | `c:silicon` | AE2 + Refined Storage (one tag covers both) |
| basic / improved / advanced processor | `refinedstorage:*_processor` (exact item) | Refined Storage |

(apatite, `c:gems/apatite`, is Thermal-only - deferred until Thermal ports to 1.21.1.)

The three Refined Storage **processors** (Basic / Improved / Advanced) are crafted
components with no `c:` tag, so each is primed by its exact processor item and
smelts back to it - the same self-seeding loop as any other variant (sacrifice one
processor to start the line, then the frog/milk loop multiplies it). Gated
`mod_loaded refinedstorage`. GEODE keeps them with the silicon/certus/fluix
crystal-tech family.

All Refined Storage ids are source-verified against `refinedmods/refinedstorage2`
(`refinedstorage-common` generated data): `refinedstorage:{basic,improved,advanced}_processor`,
`refinedstorage:quartz_enriched_iron` (also in `c:ingots`), and `refinedstorage:silicon`
(RS ships the `c:silicon` tag, so the shared silicon primer matches RS silicon too).

**silicon is shared by AE2 and Refined Storage** (both populate `c:silicon`). The
variant therefore gates on `neoforge:or(mod_loaded ae2, mod_loaded refinedstorage)`
so a Refined-Storage-only pack gets it too (it used to be AE2-only). Because a
smelt recipe outputs a concrete item, not a tag, silicon ships **two** smelt
recipes: one to `ae2:silicon` gated `ae2`, and one to `refinedstorage:silicon`
gated `refinedstorage` AND `not(ae2)` so they never both fire (AE2 wins when both
are installed). The mod-init milk-fluid discovery (`VariantFluidDiscovery`)
evaluates the same `or`/`and`/`not`/`mod_loaded` conditions, so the silicon milk
fluid mints iff the variant loads - no orphan fluid on a vanilla-only pack.

### INFERNAL (nether/fire)
- blazing crystal (`powah:crystal_blazing`) - bespoke, shipped.
- **quartz enriched iron** (`refinedstorage:quartz_enriched_iron`) - Refined Storage's
  flagship metal, bespoke (primed by the exact item; it sits in the broad `c:ingots`
  tag, which is too wide to use as a primer). Placed in INFERNAL on its quartz
  lineage - quartz is already an Infernal resource here.
- **refined obsidian** (`c:ingots/refined_obsidian` -> `mekanism:ingot_refined_obsidian`) -
  shipped as a CAVE alloy, moved here in the 2026-06-06 obsidian recategorization
  (#142): obsidian is an Infernal resource (it gates behind a diamond pickaxe), and
  refined obsidian follows its obsidian lineage.
- **flux dust** (`fluxnetworks:flux_dust`) - Flux Networks' base resource, bespoke
  (no common tag). Source-verified 2026-06-06 against the mod's `1.21` branch
  (v8.0.0, `mod.minecraft=1.21.1`). Obsidian lineage: flux dust is born from
  redstone dropped on obsidian, so it shares obsidian's Infernal tier (#145).
  One dust variant covers the whole Flux chain (ingots/blocks/cores all craft
  from dust). Colors are texture-faithful near-black with a magenta cast (the
  item texture averages 0x0A0A0A; the mod's magenta is GUI/glow identity, not
  pixel data).
- **blazegold** (`c:ingots/blazegold` -> `justdirethings:blazegold_ingot`) - Just
  Dire Things' T2 Nether metal, gold-adjacent (#188).
- **celestigem** (`justdirethings:celestigem`, exact item - JDT registers only the
  aggregate `c:gems`, no per-gem tag) - Just Dire Things' T3 gem. Placed INFERNAL
  by maintainer ruling 2026-06-08 (over Geode/Void), keeping the JDT progression
  metals together on the fire lineage with blazegold.
- Deferred (Thermal has no 1.21.1 release): sulfur (`c:dusts/sulfur`), signalum, lumium.

### VOID (end/arcane)
- Powah crystals niotic / spirited / nitro (`powah:crystal_*`) - bespoke, shipped.
  (2026-06-06: niotic and nitro colors were swapped at authoring - niotic is the
  CYAN crystal, nitro the RED one; both now carry texture-faithful averages from
  Powah-6.2.10, niotic 0x119EB6 / nitro 0xA12928.)
- Mythic Metals orichalcum, mythril (`c:ingots/*`) - shipped.
- Mystical Agriculture essences (inferium, supremium) - bespoke, iconic. (Moved
  here from BOG in the 2026-05-28 Bog recategorization: essences are a magic line,
  not organic/swamp.)
- **eclipsealloy** (`c:ingots/eclipsealloy` -> `justdirethings:eclipsealloy_ingot`) -
  Just Dire Things' T4 capstone alloy (#188). Time Crystals deliberately excluded
  (machine fuel, not a metal/gem).
- Deferred (Thermal has no 1.21.1 release): enderium.

### BOG (swamp / organic)
- **pink slime** (`industrialforegoing:pink_slime`) - the must-have slime
  joke; top BOG pick. Bespoke (no common tag). v1.12: its Froglight melts in
  the Crucible to 2,000 mB of IF's pink slime fluid (melts only by decision -
  fluid -> item stays IF's machinery).
- **plastic** (`industrialforegoing:plastic`) - latex-derived synthetic, fits the
  organic Bog identity. Bespoke (no common tag). Added 2026-05-28. v1.12: its
  Froglight melts DOWN to 1,350 mB of IF latex (675 mB/plastic per IF's
  75 mB x 9 constant, doubled).

### TIDE (aquatic) - the weak species
Modded tech has almost no aquatic resources. The one strong pick is Mythic
Metals' Aquarium (`mythicmetals:aquarium_ingot`, ocean-only ore). TIDE otherwise
leans on vanilla aquatic content. Flagged as a design gap, not a blocker.
- **dry ice** (`powah:dry_ice`) - added 2026-06-06 on the frozen-water theme,
  deliberately placed TIDE to fatten this thin roster. Bespoke (no common tag);
  texture-faithful pale-ice colors from Powah-6.2.10.

## Decisions (confirmed 2026-05-25) and how they shipped

- **Alloys:** decision was "include keyed on the common tag." Shipped the ones with
  a verified 1.21.1 provider - brass (`c:ingots/brass` -> `create:brass_ingot`),
  refined obsidian (`c:ingots/refined_obsidian` -> `mekanism:ingot_refined_obsidian`),
  and steel (`c:ingots/steel` -> `mekanism:ingot_steel`; Mekanism is the verified
  present provider, same as refined obsidian). Brass and steel are CAVE; refined
  obsidian moved CAVE -> INFERNAL on 2026-06-06 (#142, obsidian lineage). The rest
  (electrum, invar, constantan,
  signalum, lumium, enderium) stay deferred: no verified 1.21.1 provider id, and
  shipping an unverified id silently fails. (`required: false` was for tag-file
  entries; we don't ship tag files, we reference the mods' own.)
- **Output-item resolution:** the smelt-back is the per-variant recipe's output -
  the provider's verified item, gated `mod_loaded(provider)`. A pack overrides by
  replacing the recipe. (There is no runtime "first in tag" fallback - a furnace
  recipe must name a concrete item; not a codec field.)
- **Scope:** shipped ALL in one pass - the `primer_tag` mechanism + the cross-mod
  variants (CAVE metals + GEODE gems + Powah/Mythic/MA-essence VOID + Powah INFERNAL
  + BOG pink slime / plastic + TIDE aquarium), minus the Thermal-dependent picks
  (no 1.21.1 release). (MA essences moved CAVE-adjacent BOG -> VOID on 2026-05-28.)
- **TIDE:** accepted as the small, vanilla-leaning species (vanilla aquatic +
  Aquarium where Mythic Metals is present).

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

## Crushing compat - Froglight to 2x dust (v1.3, plan of record)

V1 ships no crusher; the 2x yield path needs an installed processing mod. We ship
`mod_loaded`-gated compat recipes for the 1.21.1 mods that have a crusher with a clean dust
pipeline. This is **broad-audience compat**: each recipe activates for whoever has the mod,
independent of any particular pack's mod list.

### Target mods (1.21.1, source-verified 2026-05)

**Crushers we ship recipes for** - all three deserialize their recipe input with vanilla
`Ingredient.CODEC`, ship their own per-metal dust, and ship the dust -> ingot smelt:

| Mod | Build | Recipe type | Dust item | Notes |
|---|---|---|---|---|
| Mekanism | 10.7.x | `mekanism:enriching` (Enrichment Chamber) | `mekanism:dust_<metal>` | input codec is `SizedIngredient.FLAT_CODEC` (wraps vanilla `Ingredient`) |
| Immersive Engineering | 12.x | `immersiveengineering:crusher` | `immersiveengineering:dust_<metal>` ("grit") | requires `energy` field |
| EnderIO | 8.x | `enderio:sag_milling` | `enderio:powdered_<metal>` | requires `energy`; set `"bonus": "none"` (default `multiply_output` adds grinding-ball RNG) |

**AllTheOres (ATO) - the dust + smelt-back layer, not a crusher.** ATO registers no machine
of its own; it is a parallel metal set. Its value here is twofold: (1) it ships
`alltheores:<metal>_dust` for the **full** metal range (tin, lead, osmium, nickel, silver,
zinc, aluminum, uranium, platinum, iridium, ...), far wider than any single crusher's dust set;
(2) its dust -> ingot furnace recipes key off the **`c:dusts/<metal>` tag**, so they smelt back
*any* mod's dust in that tag, not just ATO's own. So ATO both fills dust gaps for metals a
crusher lacks and backstops the smelt-back loop. Output `alltheores:<metal>_dust` (gated on the
crusher mod + `alltheores`) for any (crusher, metal) where the crusher has no native dust.

**Deferred / excluded:**
- **Create** - deferred. Has a 1.21.1 build and accepts the ingredient, but ships **no metal
  dust** (its model is ore -> crushed -> wash -> nuggets), so it can't join the native-dust
  design cleanly. Revisit only if we decide a flat "crush -> 2x ingot" exception for Create is
  worth the inconsistency.
- **Actually Additions** - deferred. `actuallyadditions:crushing` accepts the ingredient
  (vanilla `Ingredient.CODEC_NONEMPTY`), but AA ships **no dust ecosystem** (its crushing does
  ore -> 2x raw ore), so it would only work paired with ATO for the output. Low marginal value;
  add later if requested (same recipe family).
- **Just Dire Things** - excluded. It is an automation/utility mod (block placers, fluid/item
  movers, "Goo" resource-growing) with **no crusher, grinder, or ore-doubling machine** - a
  source search for `crush` returns zero hits and it ships no dust items. Nothing to target.
- **Thermal Series** - excluded. No 1.21.1 release exists (CoFH stalled at 1.20.1, last build
  2024-06); a `mod_loaded("thermal")` recipe would never resolve.

### Matching the Froglight (the key mechanism)

There is **one** `configurable_froglight` item; the resource rides in its `slime_variant`
data component. An item tag therefore **cannot** select "metal variants" - every variant shares
the same item - so the old `productivefrogs:crushable/metallic` tag plan is dead. Each recipe
instead matches per-variant with NeoForge's data-component ingredient. **On 1.21.1 the key is
`"type": "neoforge:components"`** (the `neoforge:ingredient_type` key is 1.21.4+); `strict`
defaults to `false` (partial match), which is what we want.

This works because every crusher we target deserializes its input with vanilla `Ingredient.CODEC`,
which dispatches on `"type"` to NeoForge's ingredient registry and so accepts `neoforge:components`
transparently. Verified at source: Mekanism `ItemStackIngredient` = `SizedIngredient.FLAT_CODEC`;
IE `CrusherRecipe.input` = `DualCodecs.INGREDIENT`; EnderIO `SagMillingRecipe.input` =
`Ingredient.CODEC_NONEMPTY`. **Smoke-test one Mekanism recipe in a dev run before generating the
full set** - Mekanism never uses a component ingredient in its own datagen, so confirm its loader
accepts the nested type (it should; the codec is the standard one).

Mekanism (Enrichment Chamber):

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

Immersive Engineering (Crusher - `energy` is the RF cost; 3000 matches IE's own ingot recipes):

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

EnderIO (SAG Mill - `outputs` is a list; `"bonus": "none"` pins a flat 2x, no grinding-ball RNG):

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

ATO fallback for a metal the crusher lacks a native dust for (here IE crushing tin -> ATO tin dust):

    {
      "neoforge:conditions": [
        { "type": "neoforge:mod_loaded", "modid": "immersiveengineering" },
        { "type": "neoforge:mod_loaded", "modid": "alltheores" }
      ],
      "type": "immersiveengineering:crusher",
      "energy": 3000,
      "input": {
        "type": "neoforge:components",
        "items": "productivefrogs:configurable_froglight",
        "components": { "productivefrogs:slime_variant": "productivefrogs:tin" }
      },
      "result": { "id": "alltheores:tin_dust", "count": 2 }
    }

### Closing the loop to 2x

Output 2x the dust; the player smelts it to 2 ingots. **PF ships no smelt-back recipe** - the
producing mod already does (Mekanism, IE, and EnderIO each ship a `<dust> -> ingot` furnace
recipe for every metal they have a dust for), and ATO's `c:dusts/<metal> -> ingot` smelt
backstops anything tagged. Outputs must be **concrete item ids** (none of the three crushers
accept a tag in the result), so the generator pins the exact dust per (mod, metal).

### Which variants, and how they're generated

Crushable = **metals only**. Metal-ness is not derivable from `category` (CAVE also holds coal,
redstone, lapis, diamond, emerald), so the metal set is a **curated list in the crush-recipe
generator** (`scripts/`), not a tag and not a `SlimeVariant` field. The script iterates
(crusher mod x metal variant) and, per pair, picks the output by precedence:

1. The crusher mod's own dust, if it ships one for that metal -> gate on `mod_loaded: <crusher>`.
2. Else `alltheores:<metal>_dust`, if ATO has it -> gate on `mod_loaded: <crusher>` **and** `mod_loaded: alltheores`.
3. Else no recipe for that pair.

Recipes are written under `data/productivefrogs/recipe/<modid>/<variant>.json`. (A `crushable`
flag on `SlimeVariant` is a possible future add if JEI needs to surface "crushable" in-game;
not needed for generation.)

## Storage automation (AE2 / Refined Storage)

Item and fluid automation from storage mods is **capability-based and needs no
bespoke code** - PF exposes the standard NeoForge capabilities and AE2 / Refined
Storage interact through them:

- **Appliance item I/O.** The Slime Milker and Spawnery register
  `Capabilities.ItemHandler.BLOCK` (side-aware: the down face is the output view,
  other faces are input). So an RS/AE2 Importer, Exporter, or External Storage
  bus on a Milker face pulls finished milk buckets from the bottom and pushes
  slime buckets into the inputs, exactly like a vanilla hopper does.
- **Milk-bucket fluid I/O.** Each per-variant `<variant>_slime_milk_bucket`
  registers `Capabilities.FluidHandler.ITEM` (vanilla `FluidBucketWrapper`), so
  RS/AE2 (and any tank mod) can fill and drain it.
- **Per-variant fluids preserve the variant through fluid storage.** Because each
  variant's milk is its own `Fluid` (v1.8), AE2/RS fluid storage, fluid importers,
  and fluid exporters round-trip a variant's milk with the variant intact - the
  whole point of the v1.8 per-variant-fluid refactor. (A single shared fluid would
  have come back variant-less; see `docs/automated_milk_variants.md`.)

So a fully hands-off RS/AE2 line is: External Storage / Importer on the Milker
output -> network -> Exporter places a chosen variant's milk source -> it spawns
that variant's slime for the matching frog -> the frog's Froglight drops are
imported back. No PF-side integration code; nothing to gate on `mod_loaded`.

These caps are exercised generically by existing GameTests (the Milker hopper
push/pull on `ItemHandler.BLOCK`, and `slimeMilkBucketExposesFluidHandler`); AE2
and Refined Storage are not dev dependencies, so the RS/AE2-specific round-trip is
a manual `runClient` check (drop the mod into `run/mods`).

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
