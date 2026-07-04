# Versioning & Scope

How features are split across releases, with the engineering rationale. **The V1/V2 meaning evolved:** originally V1 = playable foundation and V2 = automation, but automation shipped in the 1.x line ("V2 is just a name, not a rule", 2026-06-08), and **2.0 now means the MC 26.1 era + mob predation** (see [predator_frogs.md](./predator_frogs.md) and [port_mc_26_1.md](./port_mc_26_1.md)). The V1.x sections below are the shipped-history record.

For the player-facing roadmap (what's shipped, what's coming next, no engineering depth), see [`ROADMAP.md`](../ROADMAP.md) at the repo root. This doc is the source of truth for *why* the split exists; ROADMAP.md is the source of truth for *what* lands in each release.

## V1.0 - Base Mechanics + Appliances (SHIPPED 2026-05-24)

The "playable foundation" release. **Appliance blocks** (single-block hand-operated stations, like vanilla brewing stand or composter) are in scope. **Automation machinery** (power-fed, multi-block, hopper-integrated, pipe-fed) is not.

Targets MC 1.21.1 / NeoForge 21.1.230 / Java 21. Sky Frogs (the modpack PF was built to anchor) is locked to MC 1.21.1 because its load-bearing dependencies (Ex Deorum, Skyblock Builder) have no 1.21.4+ NeoForge builds.

**In scope (all shipped):**

- **Frogspawn bottling via vanilla glass bottle** (no custom tool item - vanilla `minecraft:glass_bottle` is consumed on use against frogspawn)
- **Frog Egg bottle** (item + block, placed on water by hand)
- **Slime Bucket** (item, transport mechanic - preserves variant)
- **Primed Frog Egg blocks** (6 blocks, one per species) - primed by right-clicking vanilla frogspawn with any variant primer item
- **Resource Tadpoles** and **Resource Frogs** (6 species: Bog, Cave, Geode, Tide, Infernal, Void)
- **Resource Slimes** (12 data-driven variants - iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl)
- **Six parent slime species** (Bog, Cave, Geode, Tide, Infernal, Void) - each spawns in its themed biome via `neoforge:add_spawns` biome modifiers
- **Species-locked slime infusion** - only PF parent species can be infused, and only into variants within their own species. Vanilla `minecraft:slime` and `minecraft:magma_cube` are hard-rejected as infusion targets (Q1=A baked in)
- **Slime Milker** (furnace-shaped GUI block, 100-tick cook, hopper I/O) - converts a Slime Bucket to the matching variant Slime Milk bucket
- **Slime Milk fluid** (lava-flow, source-block-spawns-slimes, configurable depletion)
- **Configurable Froglight** (single block stamped with `slime_variant` data component; species Froglight blocks were dropped in v1.0)
- **Smelting recipes** (per variant) - every variant smelts to its base resource via vanilla furnace
- **JEI Information pages** - dynamically generated from the SlimeVariant + ParentSpecies datapack registries
- **Cross-mod compat** via JSON for variant pools - `mod_loaded` neoforge conditions, no `compat/` Java package

**The V1 rule of thumb:** if vanilla has a single-block appliance equivalent (furnace, brewing stand, composter, cauldron) that's V1 scope. If we'd be adding power, pipes, or multiblocks, that's V2.

Full v1.0 design spec: [species_as_category_redesign.md](./species_as_category_redesign.md). Port history: [port_mc_1_21_1.md](./port_mc_1_21_1.md).

## V1.1 - Vanilla Resource Coverage (SHIPPED v1.1.0, 2026-05-25)

Mostly-data release adding every vanilla item fitting cleanly into one of the six species. Each variant is a `slime_variant` JSON + recipe + lang, plus one one-line Java edit (the Slime Milk `VARIANTS` entry; fluids register at mod-init - superseded in v1.8: the per-variant milk fluids now mint from the bundled `productivefrogs/variants_index.json`, kept in sync by `scripts/generate_variants_index.py`, no Java edit). The spawn egg is data-driven (CR-9) - one component item enumerated from the registry, no per-variant Java. The four templated JSON files per variant are emitted by `scripts/generate_v1_1_variants.ps1`.

**22 new variants** (33 total after v1.1):

> The Bog row below is the v1.1-era roster. The Bog pool was recategorized in **v1.6** (organic/swamp); `bone`/`gunpowder`/`rotten_flesh`/`string` were cut and `dirt`/`mud`/`moss`/`mycelium`/`lily_pad`/`plastic` added. See the V1.6 section below.

| Species | New variants |
|---|---|
| Bog (+7) | bone, gunpowder, clay_ball, rotten_flesh, string, leather, feather |
| Cave (+3) | glow_ink_sac, obsidian, echo_shard |
| Geode (+1) | amethyst |
| Tide (+2) | ink_sac, prismarine_crystals |
| Infernal (+7) | netherite_scrap, glowstone_dust, soul_sand, soul_soil, netherrack, blaze, quartz |
| Void (+2) | chorus_fruit, shulker_shell |

`prismarine_crystals` (Tier B) was promoted into scope. Remaining Tier B candidates (`nautilus_shell`, `ghast_tear`) stay deferred per [v1_1_scope.md](./v1_1_scope.md). `wither_rose` and `end_stone` are dropped (the primer-tag-only fallback they depended on is gone in v1.0). Two variants were cut as redundant: `slime_ball` and the v1.0 `magma_cream` variant (the latter is a breaking removal of a shipped variant; v1.0's Infernal pool drops to zero base variants).

The interior of each Resource Slime is a downscaled copy of its resource block baked onto the slime's inner-cube faces (`scripts/generate_resource_slime_textures.py`), with the per-variant tint on the translucent exterior shell. (This replaced the v1.0.1 live-block-render layer, which was depth-culled by the translucent shell and never visible.)

The 5 mob-drop variants that were previously deferred to a separate V1.2 category (bone, rotten_flesh, string, leather, feather) now fit cleanly under Bog Slime - no new species needed.

Full design lives in [v1_1_scope.md](./v1_1_scope.md).

## V1.2 - Cross-Mod Variant Pools (SHIPPED v1.2.0, 2026-05-25)

Cross-mod variant entries for the staple ATM10 tech mods, all `mod_loaded`-gated additions to the appropriate species. ~24 variants ship: Mekanism (osmium, tin, lead, uranium, refined_obsidian), Create / Thermal-style alloys (zinc, brass, silver, nickel), AllTheOres metals (aluminum, mythril, orichalcum, and the shared `c:` metals), Applied Energistics 2 (certus_quartz, fluix, fluorite, silicon), Mystical Agriculture (inferium, supremium), Powah (niotic, spirited, nitro), and Industrial Foregoing (pink_slime). Most modded ores fit under Cave Slime; gems under Geode.

The enabling mechanism shipped in the same release: the `SlimeVariant` codec gained a `primer_tag` field (item-or-tag primer), and cross-mod entries key off the NeoForge `c:` common tags (`c:ingots/tin`, etc.) so one entry covers every mod that provides the tag. Each variant's smelt-back result is encoded in its own generated recipe JSON (`mod_loaded`-gated for cross-mod variants), not on the variant. See [cross_mod_compat.md](./cross_mod_compat.md) and species_as_category_redesign.md §Cross-mod variants for the per-mod placement. A cross-cutting debug-logging framework (`PFDebug`) also landed in this release.

## V1.3 - Cross-Mod Crush Yields (SHIPPED v1.3.0, 2026-05-26)

The cross-mod piece that completed the V1 line. With Mekanism, Immersive Engineering, or EnderIO installed, crushing a metal Froglight yields 2x the resource (matching how those mods already reward ore processing) instead of the single unit a vanilla smelt gives. Shipped as optional `mod_loaded` JSON recipes (33 generated recipes under `data/productivefrogs/recipe/<modid>/`, pinned by `CrushRecipeTest`), so it activates only when one of those crusher mods is present and is a no-op otherwise. AllTheOres broadens the metals covered. Per-variant `neoforge:components` recipes (no `crushable` tag, all variants are one item distinguished by component); each outputs the crusher's own dust, and the dust-to-ingot smelt closes the loop so PF ships no smelt-back. Build spec: [v1_3_crush_recipes.md](./v1_3_crush_recipes.md).

## V1.4 - The Spawnery + Jade tooltips (SHIPPED v1.4.0 / v1.4.1, 2026-05-26)

**v1.4.0 - the Spawnery.** A V1 appliance for skyblock and other restricted-biome packs: the frog-side analogue of the Slime Milker. A furnace-style block that turns glass bottles into bottled frogspawn, fueled by slime balls; an empty primer slot yields plain vanilla frogspawn, a species primer (cobblestone, mud, kelp, amethyst shard, netherrack, ender pearl) bottles that species' eggs. **Disabled by default** (a normal world has swamps); a pack flips one config flag to turn it on, and the primer set is pack-overridable via item tags. Like the Milker, it exposes basic hopper I/O (`Capabilities.ItemHandler.BLOCK`). Spec: [spawnery.md](./spawnery.md).

**v1.4.1 - Jade tooltips + tinted milk.** Optional Jade look-at tooltips for the appliances (Slime Milk source spawn count; Slime Milker / Spawnery cook progress), plus flowing/spread Slime Milk now tints per-variant instead of falling back to the base hue.

## V1.5 - Frog Stat Breeding (SHIPPED v1.5.0 - v1.5.3, 2026-05-27 / 05-28)

**v1.5.0 - stat breeding.** The headline V1 progression system. Resource Frogs carry three stats - Appetite (eat cadence), Bounty (Froglight drops per slime), Reach (prey-scan radius), each `1..10`. Breeding two same-species frogs on a Sweetslime treat (slime ball + sugar) runs vanilla frog breeding; the offspring inherits a hi-biased blend with an improvement/regression roll. Stats carry conception -> egg BE -> tadpole -> frog, surface via Jade, and are fully config-tunable. Frog-only, no Terrarium dependency. Spec: [frog_breeding.md](./frog_breeding.md). (Cosmetic maxed-frog visuals deferred.)

**v1.5.1 - v1.5.3 - data/polish.** Lapis recategorized Cave -> Geode (v1.5.1); steel added to the Cave pool (v1.5.2); JEI recipe-category pages for the Spawnery + Slime Milker (v1.5.3).

## V1.6 - Bog Recategorization + Reliability (SHIPPED v1.6.0, 2026-05-28)

A data + reliability release, still firmly V1 (no power/pipes/multiblocks).

**Bog recategorization.** The Bog species was retightened from a mob-drop catch-all to **organic / swamp** matter: added `dirt`, `mud`, `moss`, `mycelium`, `lily_pad`, and Industrial Foregoing `plastic`; moved the Mystical Agriculture essences (`inferium`, `supremium`) to **Void**; and cut `bone`, `gunpowder`, `rotten_flesh`, `string` (a breaking registry removal - see the save-compat note in [species_as_category_redesign.md](./species_as_category_redesign.md)). The Bog Spawnery primer moved `bone` -> `clay_ball`.

**Slime Milk reliability.** Flowing milk no longer displaces frogspawn / water sources / other milk sources (custom `SlimeMilkFluid` `canSpreadTo` override); frogs no longer drown in milk (`FluidType.canDrown(false)`); re-bucketing a source preserves its depletion count (new `spawns_remaining` component); the Jade spawns-left readout updates live (server-data provider).

**Deterministic life-cycle timings.** New `lifecycle.*` config: fixed primed-frogspawn hatch delay, config-exposed tadpole growth, and re-breed cooldown (vanilla frogs/tadpoles keep stock pacing). Non-bred frogs now start at baseline (`1/1/1`) stats - breeding is the only climb.

## V1.7 - Slime Milk Catalysts (SHIPPED v1.7.0, 2026-05-29)

The early-game **stopgap** toward hands-off production, ahead of the V2 Frog
Habitat that closes the loop properly. Four hand-crafted **catalysts** dropped
into a placed Slime Milk source buff it in place: **Count** (more spawns,
uncapped), **Speed** (faster cadence), **Quantity** (more slimes per spawn), and
**Infinite Count** (never depletes; built from Count catalysts). All four buffs
live on the source's BlockEntity and round-trip through the bucket. Config-gated
(`slime_milk_catalysts.enabled`, on by default) via the same
`ConfigEnabledCondition` machinery as the Spawnery.

Still firmly **V1**: a hand-applied buff to a single block, no power / pipes /
multiblocks. The one structural change is that the depletion counter moved from a
blockstate property (capped at 16) onto the BlockEntity, so uncapped Count is
representable. Spec: [slime_milk_catalysts.md](./slime_milk_catalysts.md).

## V2 - Automation

> **Superseded framing.** This section captured the *automation era*, which shipped in the **1.x line** (the Terrarium at v1.16.0) per "V2 is just a name, not a rule" (2026-06-08) - automation was never a release gate. **2.0.0 now means something different:** the MC 26.1 platform port + the **mob-predation system** (all mob drops come from a frog eating the mob). See [predator_frogs.md](./predator_frogs.md) (delivery roadmap), [port_mc_26_1.md](./port_mc_26_1.md) (port plan), and [ROADMAP.md](../ROADMAP.md) "2.0". The automation items below remain **2.x** candidates on the 26.1 base; "built on top of the foundation, never replaces it" still holds.

Tools and blocks that let the player scale and automate the loop. Built on top of the V1 foundation; never replaces it.

**In scope:**

- **Buffered / auto-upgrading Slime Milker**: basic hopper I/O on the V1 Milker (and Spawnery) already shipped (`Capabilities.ItemHandler.BLOCK`, V1.0 above). The V2 layer is the *buffered* upgrade: internal slime/milk buffers and auto-cycling so a line runs without per-item hopper handoff, not the basic hopper hookup
- **Frog Terrarium / Habitat** - **shipped v1.16.0** (the flagship multiblock): a 5x4x5-interior multiblock that contains the whole milk-in / froglight-out loop (Controller / Sprinklers / Incubators / Hatch). Full build spec: [terrarium.md](./terrarium.md) (issue #185)
- **Auto-feeders** - hopper-fed slime delivery to nearby frogs (alternative to milk-spawn proximity)
- **Capacity / efficiency upgrades** for habitat blocks
- **Pipe/hopper-aware fluid handling** for Slime Milk
- Potentially: power compatibility (FE / NeoForge Energy)

> **Not planned** (moved out of V2 scope): a **native crusher block** (the 2x crush is delegated to external crusher mods - Mekanism / IE / EnderIO; the broader "no first-party 2x" guideline behind it was retired 2026-06-06 by the v1.12 Crucible + Casting Mold melt-and-cast lane, but a literal crusher stays not planned) and a **drop-collection block** (vanilla hoppers suffice). See [ROADMAP.md](../ROADMAP.md) "Explicitly NOT planned".

**Why deferred:** these are all "scaling solutions" - they make V1 faster, denser, hands-off. They don't change *what the mod is*. Building V1 first ensures the design holds up without machines propping it up.

## V2 or V3 - Parked Ideas

Features captured during V1 design that aren't in scope yet but should be remembered:

- **Potion Slime / Alchemical category.** A 7th category (or a special variant within an existing one) tied to brewing. Possible mechanics: infuse with a potion item (instead of an ingot) → slime takes on that potion's effect → killing it drops the corresponding potion, or splashes its effect in an area. Parent could be a "Brew Slime" spawning near witch huts or nether wart. Worth scoping when V1 ships and we know how much hook-into-brewing the framework can support.

## V3+ - Speculative

Not committed. Possible future directions:

- Deep genetics / breeding tree on the frog side (Forestry-style)
- New dimensions (Frog Realm?)
- Tinkers-style upgradable frogs (gold trim, diamond skin)
- Quest/advancement integration

**Explicitly NOT planned:** Fabric port. Productive Frogs is NeoForge-only by design. See [architecture.md](./architecture.md).

## Compatibility Promise

- V2 datapacks must not break V1 worlds.
- V2 machines must remain optional - a player who never crafts a Frog Terrarium / Habitat in V2 still has every V1 capability.
- Cross-mod compat datapacks are independent of V1/V2 split.
