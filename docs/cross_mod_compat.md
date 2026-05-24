# Cross-Mod Compatibility

Strategy for supporting other popular NeoForge 1.21 mods without hard dependencies, runtime crashes when a mod is absent, or per-mod Java code.

## Core Approach

**Everything is JSON, conditionally loaded.** Productive Frogs doesn't link against any other mod's classes. It references their content by ID through:

1. **Common tags** (`c:ingots/osmium`) where the cross-mod tag convention exists.
2. **Direct IDs marked `required: false`** in tag JSON, so missing items silently skip.
3. **`neoforge:conditions → mod_loaded`** wrappers on entire JSON files (slime variants, loot tables, recipes) so they only register when the target mod is present.

Result: a player with just Productive Frogs and no other mods gets the base content. A player with Productive Frogs + Mekanism gets the base content + Mekanism slimes. Adding/removing mods between sessions is safe — registry just changes.

## Supported Mods (planned for v0.1)

### Mekanism

Adds to **Cave** species:

| Slime | Resource | Source tag |
|---|---|---|
| Osmium Slime | Osmium nuggets | `c:ingots/osmium` |
| Tin Slime | Tin nuggets | `c:ingots/tin` |
| Lead Slime | Lead nuggets | `c:ingots/lead` |
| Uranium Slime | Uranium nuggets | `c:ingots/uranium` |

Adds to **Geode** species:

| Slime | Resource | Source tag |
|---|---|---|
| Fluorite Slime | Fluorite gems | `c:gems/fluorite` |

### Create

Adds to **Cave** species:

| Slime | Resource | Source tag |
|---|---|---|
| Zinc Slime | Zinc nuggets | `c:ingots/zinc` |
| Brass Slime | Brass nuggets | `c:ingots/brass` |
| Bronze Slime | Bronze nuggets | `c:ingots/bronze` |

Create's bronze is the most commonly requested cross-mod addition.

### Thermal Series

Adds to **Cave** species:

| Slime | Resource | Source tag |
|---|---|---|
| Tin Slime | (shared with Mekanism — first-loader-wins or merged) | `c:ingots/tin` |
| Lead Slime | (shared with Mekanism) | `c:ingots/lead` |
| Silver Slime | Silver nuggets | `c:ingots/silver` |
| Nickel Slime | Nickel nuggets | `c:ingots/nickel` |
| Signalum Slime | Signalum ingots | (Thermal-only) |
| Lumium Slime | Lumium ingots | (Thermal-only) |
| Enderium Slime | Enderium ingots | (Thermal-only — may move to Arcane) |

Note that **Tin and Lead are shared** between Mekanism and Thermal. With both mods present, the same slime species feeds one output tag and that tag is satisfied by either mod's ingot. The slime variant JSON for "tin" is loaded if **either** Mekanism or Thermal is present (`neoforge:conditions` with an OR of `mod_loaded` clauses).

### Mythic Metals

Adds to **Cave** and **Geode** species:

| Slime | Species | Resource |
|---|---|---|
| Adamantite Slime | Cave | Adamantite |
| Orichalcum Slime | Cave | Orichalcum |
| Mythril Slime | Cave | Mythril |
| Ruby Slime | Geode | Ruby |
| Sapphire Slime | Geode | Sapphire |
| Topaz Slime | Geode | Topaz |

Mythic Metals is Fabric-only historically, but a NeoForge fork or equivalent (Mythic Metals on Forge, or a different "many metals" mod) may exist. Status TBD.

## Conflict Handling

### Shared resources across multiple mods

When two mods (e.g. Mekanism + Thermal) both add tin:

- One slime variant JSON: `data/productivefrogs/slime_variant/tin.json`
- Conditions: `mod_loaded(mekanism) OR mod_loaded(thermal)`
- Loot table outputs `#c:nuggets/tin` (both mods register to this tag)
- Drop block: a single "Tin Froglight" model used regardless of source mod

The slime is the same entity, the drop is the same item, the tag is the same. No conflict.

### Species collisions

Currently none anticipated. If a future mod adds a resource that could fit two species (e.g. a metal-gem hybrid), assign it at design time per the *biome-source over form-source* rule in [species_as_category_redesign.md](./species_as_category_redesign.md).

## Datapack Override Path for Modpack Authors

Modpack authors can:

1. **Disable a slime variant** — override its JSON file with `{ "neoforge:conditions": [{ "type": "neoforge:false" }] }`.
2. **Add their own slime variant** — drop a JSON in their pack's `data/<modpack>/productivefrogs/slime_variant/<name>.json`. The `primer_item` field is the exact 1:1 match for infusion; the `category` field assigns the variant to one of the six species (`bog`, `cave`, `geode`, `tide`, `infernal`, `void`).
3. **Re-balance drops** — override the per-slime loot tables.
4. **Add a custom parent species** — drop a JSON in `data/<modpack>/productivefrogs/parent_species/<name>.json` mapping a modded slime mob to a Category for split-discovery routing.

No code touch required for any of this. v1.0 deleted the `productivefrogs:primer/<category>` tag system — variant resolution is exact match on `SlimeVariant.primer_item` (see [species_as_category_redesign.md](./species_as_category_redesign.md) §Slime infusion).

## Crushing Compat — Froglight → 2× powder

V1 does **not** ship its own crusher block or pestle. The 2× resource path via crushing requires an installed processing mod. We ship conditional `mod_loaded` compat recipes for the popular options:

| Mod | Block | Recipe |
|---|---|---|
| Create | Crushing Wheels + Millstone | 1 Iron Froglight → 2 Crushed Raw Iron (Create's tag) |
| Mekanism | Crusher / Enrichment Chamber | 1 Iron Froglight → 2 Iron Dust |
| Thermal Series | Pulverizer | 1 Iron Froglight → 2 Pulverized Iron |
| (Any future) | (their crusher) | 1 Froglight → 2 of their dust output |

Recipes ship as JSON in `data/productivefrogs/recipes/<modid>/...` wrapped in `neoforge:conditions → mod_loaded`. If none of the listed mods is installed, players smelt Froglights directly for 1× resource. Crushing is a soft incentive to install a processing mod — never required.

**Tags we publish for other mods to reference:**

- `productivefrogs:crushable/metallic` — set of all metallic-category Froglight items. A crusher-mod recipe can target this tag to handle every metallic variant in one recipe definition (even modded ones added by datapack).

**Why metal variants only:** crushing produces "powder" which only makes sense for materials that get smelted into ingots. Gems, sponges, kelp, and ender pearls don't have an equivalent "crushed → ingot" pipeline. Smelt is the universal path; crush is the metal-variant-only optimization (iron, copper, gold, plus modded metallic Cave variants).

## Out of Scope (v0.1)

- **Applied Energistics 2** — certus quartz could be a Gem-category slime, but AE2's deep crafting integration would be a tangent.
- **Botania** — manasteel/elementium fit Metallic, but Botania's mana economy is its own world.
- **Industrial Foregoing / RF Tools** — fits the tech-mod ecosystem but no priority gem materials.
- **Tinkers' Construct** — its many alloys would fit Metallic, but TConstruct's part-builder system is its own progression.

All of these can be added in later versions or as community compat addon datapacks. Priority for v0.1 is **Mekanism + Create**, which are the two most-installed mods in the All-the-Mods-10 ecosystem.
