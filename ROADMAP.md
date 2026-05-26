# Productive Frogs roadmap

Where the mod is going. This is the canonical "what's shipped, what's coming, what's parked" document. The [CHANGELOG.md](./CHANGELOG.md) tracks each shipped release in detail; this doc tracks the runway. Engineering rationale and scope boundaries live in [docs/versioning.md](./docs/versioning.md).

Living document. Anything below a "shipped" version can move between tracks based on player feedback, modpack-author requests, or scope realities.

Targets Minecraft 1.21.1 / NeoForge 21.1.230 / Java 21.

---

## Shipped

The mod is fully playable today. Find a slime in its themed biome, prime it with the resource you want, feed it to the matching frog, and smelt the Froglight it drops back into that resource. Slime Milkers and Slime Milk source blocks keep the loop running without you standing over it. Everything in this section is live on CurseForge.

### v1.0 (shipped 2026-05-24): foundation + appliances

The playable core. Six frog species (Bog, Cave, Geode, Tide, Infernal, Void), each spawning in its themed biome and eating only the slimes that match it. The starter resource set: iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, ender pearl. Right-click a wild slime with the resource to convert it. The Slime Milker (a furnace-style appliance) and Slime Milk source blocks keep production going hands-off. JEI Information pages and Jade in-world tooltips ship out of the box. Adding a new resource is a single JSON file, no Java.

### v1.1 (shipped 2026-05-25): vanilla resource coverage

Every vanilla resource that fits cleanly under one of the six species became farmable, with no new species and no new code. Bog frogs gained the mob drops (bone, gunpowder, clay, rotten flesh, string, leather, feather); Cave gained glow ink sac, obsidian, echo shard; Geode gained amethyst; Tide gained ink sac and prismarine crystals; Infernal gained netherite scrap, glowstone, soul sand, soul soil, netherrack, blaze, quartz; Void gained chorus fruit and shulker shell.

### v1.2 (shipped 2026-05-25): cross-mod variant pools

Drop Productive Frogs into a modded pack and the relevant resource sets light up automatically, no configuration. Tech-mod metals and gems are covered out of the box: Mekanism, Create, Thermal, Applied Energistics 2, AllTheOres, Mystical Agriculture, Powah, Industrial Foregoing, and more. Each entry gates behind a soft mod-loaded check and silently skips when its source mod is absent, so the same jar is safe to drop into any pack. This release also added a built-in, opt-in debug-logging framework that makes troubleshooting a misbehaving setup far easier.

---

## v1.3 (next): cross-mod crush yields

The one cross-mod piece still missing from the v1 line. With Mekanism, Immersive Engineering, or EnderIO installed, crushing a metal Froglight yields double the resource instead of the single unit you get from smelting, matching how those mods already reward ore processing. It ships as optional `mod_loaded` recipes, so it activates only when one of those mods is present and changes nothing otherwise. AllTheOres, when present, broadens the metals covered.

**Implemented (in-tree, pre-release):** 33 generated recipes under `data/productivefrogs/recipe/<modid>/`, pinned by `CrushRecipeTest`. The per-mod `runClient` smoke test is the remaining pre-release gate - see [docs/v1_3_crush_recipes.md](./docs/v1_3_crush_recipes.md).

---

## v1.4 (in progress): the Spawnery (skyblock bootstrap)

A new V1 appliance for skyblock and other restricted-biome packs: the **Spawnery**.
It is the frog-side analogue of the Slime Milker - a furnace-style block that turns
glass bottles into bottled frogspawn, fueled by slime balls. Leave the primer slot
empty for plain vanilla frogspawn, or add a species primer (cobblestone, mud, kelp,
amethyst shard, netherrack, or an ender pearl) to bottle that species' eggs. It is
**disabled by default** - a normal world has swamps and never needs it - and a pack
flips one config flag to turn it on. The primer set is pack-overridable via item
tags. This unblocks the frog side of the loop where vanilla frogspawn is
unreachable. Spec + design: [docs/spawnery.md](./docs/spawnery.md).

## v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Hopper-fed Slime Milker:** auto-input slime buckets, auto-output milk buckets. Drop-in upgrade over the v1 Milker.

**Frog Terrarium / Habitat block:** placeable frog housing with input/output inventory. Houses one or more frogs in a contained system.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs. Alternative to milk-spawn proximity.

**Capacity / efficiency upgrades** for habitat blocks.

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

---

## Proposed: slime-to-fluid processing block

A new processing block that converts buckets of slimes into fluids - a way to generate fluids from farmed slimes. This generalizes the Slime Milker's slime-bucket-to-Slime-Milk conversion into a broader fluid-generation mechanic. Which fluids it can produce, and whether it ships as a hand-operated V1.x appliance or an automated V2 block, is not yet scoped.

---

## Long-shot ideas (not committed)

Captured during v1 design. Not on the schedule; revisited when v2 ships and we know how much hook-into-vanilla the framework supports.

- **Stat upgrades for slimes and frogs.** A consumable buff item that raises a creature's stats toward a 10/10/10 cap. Stats could cover things like production rate, yield, and reach; the grind to max a creature out is the core loop, in the spirit of Productive Bees' gene maxing. Maxed creatures could earn cosmetic tiers (gold trim, diamond skin). This is the headline long-shot.
- **Deep genetics / breeding tree** on the frog side (Forestry-style), feeding the same stat system as the upgrade item.
- **Alchemical slime category** tied to brewing. Infuse with a potion item, the slime takes on that potion's effect, and killing it drops the matching potion (or splashes its effect in an area). Parent could be a "Brew Slime" spawning near witch huts or nether wart.
- **New dimensions** (Frog Realm).
- **Quest / advancement integration.**

---

## Compatibility promise

- v2 datapacks won't break v1 worlds.
- v2 machines remain optional. Every v1 capability stays usable without them.
- Cross-mod compat datapacks are independent of the v1 / v2 split.

---

## Explicitly NOT planned

**Fabric port.** Productive Frogs is NeoForge-only by design and will remain so. See [docs/architecture.md](./docs/architecture.md) for the technical reasoning.

**Native crusher block.** The 2x metal-Froglight crush yield is delegated to external crusher mods (Mekanism / Immersive Engineering / EnderIO, via the optional `mod_loaded` recipes shipped in v1.3.0). The mod will not ship its own crusher; packs that want the 2x payoff install one of those mods.

**Drop-collection block.** Vanilla hoppers under the frog pen already collect dropped Froglights. The mod will not ship a custom collector.
