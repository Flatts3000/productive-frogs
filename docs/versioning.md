# Versioning & Scope

How features are split across releases, with the engineering rationale for the V1 / V2 split. V1 is the foundation; V2 layers automation on top without breaking V1.

For the player-facing roadmap (what's shipped, what's coming next, no engineering depth), see [`ROADMAP.md`](../ROADMAP.md) at the repo root. This doc is the source of truth for *why* the split exists; ROADMAP.md is the source of truth for *what* lands in each release.

## V1.0 — Base Mechanics + Appliances (SHIPPED 2026-05-24)

The "playable foundation" release. **Appliance blocks** (single-block hand-operated stations, like vanilla brewing stand or composter) are in scope. **Automation machinery** (power-fed, multi-block, hopper-integrated, pipe-fed) is not.

Targets MC 1.21.1 / NeoForge 21.1.230 / Java 21. Sky Frogs (the modpack PF was built to anchor) is locked to MC 1.21.1 because its load-bearing dependencies (Ex Deorum, Skyblock Builder) have no 1.21.4+ NeoForge builds.

**In scope (all shipped):**

- **Frogspawn bottling via vanilla glass bottle** (no custom tool item — vanilla `minecraft:glass_bottle` is consumed on use against frogspawn)
- **Frog Egg bottle** (item + block, placed on water by hand)
- **Slime Bucket** (item, transport mechanic — preserves variant)
- **Primed Frog Egg blocks** (6 blocks, one per species) — primed by right-clicking vanilla frogspawn with any variant primer item
- **Resource Tadpoles** and **Resource Frogs** (6 species: Bog, Cave, Geode, Tide, Infernal, Void)
- **Resource Slimes** (12 data-driven variants — iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl)
- **Six parent slime species** (Bog, Cave, Geode, Tide, Infernal, Void) — each spawns in its themed biome via `neoforge:add_spawns` biome modifiers
- **Species-locked slime infusion** — only PF parent species can be infused, and only into variants within their own species. Vanilla `minecraft:slime` and `minecraft:magma_cube` are hard-rejected as infusion targets (Q1=A baked in)
- **Slime Milker** (furnace-shaped GUI block, 100-tick cook, hopper I/O) — converts a Slime Bucket to the matching variant Slime Milk bucket
- **Slime Milk fluid** (lava-flow, source-block-spawns-slimes, configurable depletion)
- **Configurable Froglight** (single block stamped with `slime_variant` data component; species Froglight blocks were dropped in v1.0)
- **Smelting recipes** (per variant) — every variant smelts to its base resource via vanilla furnace
- **JEI Information pages** — dynamically generated from the SlimeVariant + ParentSpecies datapack registries
- **Cross-mod compat** via JSON for variant pools — `mod_loaded` neoforge conditions, no `compat/` Java package

**The V1 rule of thumb:** if vanilla has a single-block appliance equivalent (furnace, brewing stand, composter, cauldron) that's V1 scope. If we'd be adding power, pipes, or multiblocks, that's V2.

Full v1.0 design spec: [species_as_category_redesign.md](./species_as_category_redesign.md). Port history: [port_mc_1_21_1.md](./port_mc_1_21_1.md).

## V1.1 — Vanilla Resource Coverage (IMPLEMENTED, pending release)

Mostly-data release adding every vanilla item fitting cleanly into one of the six species. Each variant is a `slime_variant` JSON + recipe + lang, plus one one-line Java edit (the Slime Milk `VARIANTS` entry; fluids register at mod-init). The spawn egg is data-driven (CR-9) - one component item enumerated from the registry, no per-variant Java. The four templated JSON files per variant are emitted by `scripts/generate_v1_1_variants.ps1`.

**23 new variants** (35 total after v1.1):

| Species | New variants |
|---|---|
| Bog (+8) | bone, gunpowder, clay_ball, rotten_flesh, string, leather, feather, slime_ball |
| Cave (+3) | glow_ink_sac, obsidian, echo_shard |
| Geode (+1) | amethyst |
| Tide (+2) | ink_sac, prismarine_crystals |
| Infernal (+7) | netherite_scrap, glowstone_dust, soul_sand, soul_soil, netherrack, blaze, quartz |
| Void (+2) | chorus_fruit, shulker_shell |

`prismarine_crystals` (Tier B) was promoted into scope. Remaining Tier B candidates (`nautilus_shell`, `ghast_tear`) stay deferred per [v1_1_scope.md](./v1_1_scope.md). `wither_rose` and `end_stone` are dropped (the primer-tag-only fallback they depended on is gone in v1.0).

The 5 mob-drop variants that were previously deferred to a separate V1.2 category (bone, rotten_flesh, string, leather, feather) now fit cleanly under Bog Slime — no new species needed.

Full design lives in [v1_1_scope.md](./v1_1_scope.md).

## V1.2 — Cross-Mod Variant Pools (PLANNED)

Cross-mod variant entries (Mekanism osmium / tin / lead / uranium; Create zinc / brass / bronze; Thermal silver / nickel / signalum; Mythic Metals etc.) — all `mod_loaded`-gated additions to the appropriate species.

Most modded ores fit under Cave Slime (overworld mined ores) — see species_as_category_redesign.md §Cross-mod variants for the per-mod placement.

## V2 — Automation

Tools and blocks that let the player scale and automate the V1 loop. Built on top of V1; never replaces it.

**In scope:**

- **Auto-fed Slime Milker** — hopper-integrated variant of the V1 Milker, accepts slime buckets from a hopper and pushes milk buckets to an output side
- **Frog Terrarium / Habitat** block — placeable frog housing with input/output inventory
- **Auto-feeders** — hopper-fed slime delivery to nearby frogs (alternative to milk-spawn proximity)
- **Capacity / efficiency upgrades** for habitat blocks
- **Native crusher block** — optional in-house version (so the 2× crush path works without external mods)
- **Cross-mod crush 2× recipes** for metallic Froglights via Create / Mekanism / Thermal — conditional `mod_loaded` JSON recipes. The `productivefrogs:crushable/metallic` item tag is already reserved in V1; the recipes themselves wait on a multi-mod test environment that can validate each target mod's recipe shape.
- **Pipe/hopper-aware fluid handling** for Slime Milk
- Potentially: power compatibility (FE / NeoForge Energy)

**Why deferred:** these are all "scaling solutions" — they make V1 faster, denser, hands-off. They don't change *what the mod is*. Building V1 first ensures the design holds up without machines propping it up.

## V2 or V3 — Parked Ideas

Features captured during V1 design that aren't in scope yet but should be remembered:

- **Potion Slime / Alchemical category.** A 7th category (or a special variant within an existing one) tied to brewing. Possible mechanics: infuse with a potion item (instead of an ingot) → slime takes on that potion's effect → killing it drops the corresponding potion, or splashes its effect in an area. Parent could be a "Brew Slime" spawning near witch huts or nether wart. Worth scoping when V1 ships and we know how much hook-into-brewing the framework can support.

## V3+ — Speculative

Not committed. Possible future directions:

- Deep genetics / breeding tree on the frog side (Forestry-style)
- New dimensions (Frog Realm?)
- Tinkers-style upgradable frogs (gold trim, diamond skin)
- Quest/advancement integration

**Explicitly NOT planned:** Fabric port. Productive Frogs is NeoForge-only by design. See [architecture.md](./architecture.md).

## Compatibility Promise

- V2 datapacks must not break V1 worlds.
- V2 machines must remain optional — a player who never crafts a Slime Nursery in V2 still has every V1 capability.
- Cross-mod compat datapacks are independent of V1/V2 split.
