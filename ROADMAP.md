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

The one cross-mod piece still missing from the v1 line. With Create, Mekanism, or Thermal installed, crushing an ore or metal Froglight (the Cave-species resources) yields double the resource instead of the single unit you get from smelting, matching how those mods already reward ore processing. It ships as optional `mod_loaded` recipes, so it activates only when one of those mods is present and changes nothing otherwise. A `crushable` item tag is created alongside the recipes to mark which Froglights qualify.

---

## v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Hopper-fed Slime Milker:** auto-input slime buckets, auto-output milk buckets. Drop-in upgrade over the v1 Milker.

**Frog Terrarium / Habitat block:** placeable frog housing with input/output inventory. Houses one or more frogs in a contained system.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs. Alternative to milk-spawn proximity.

**Capacity / efficiency upgrades** for habitat blocks.

**Native crusher block:** an optional in-house implementation so the double-yield crush path works even without Create / Mekanism / Thermal installed.

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

---

## Long-shot ideas (not committed)

Captured during v1 design. Not on the schedule; revisited when v2 ships and we know how much hook-into-vanilla the framework supports.

- **Alchemical slime category** tied to brewing. Infuse with a potion item, the slime takes on that potion's effect, and killing it drops the matching potion (or splashes its effect in an area). Parent could be a "Brew Slime" spawning near witch huts or nether wart.
- **Deep genetics / breeding tree** on the frog side (Forestry-style).
- **New dimensions** (Frog Realm).
- **Tinkers-style upgradable frogs** (gold trim, diamond skin).
- **Quest / advancement integration.**

---

## Compatibility promise

- v2 datapacks won't break v1 worlds.
- v2 machines remain optional. Every v1 capability stays usable without them.
- Cross-mod compat datapacks are independent of the v1 / v2 split.

---

## Explicitly NOT planned

**Fabric port.** Productive Frogs is NeoForge-only by design and will remain so. See [docs/architecture.md](./docs/architecture.md) for the technical reasoning.
