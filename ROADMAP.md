# Productive Frogs roadmap

Where the mod is going. This is the canonical "what's shipped, what's coming, what's parked" document. The [CHANGELOG.md](./CHANGELOG.md) tracks each shipped release in detail; this doc tracks the runway. Engineering rationale and scope boundaries live in [docs/versioning.md](./docs/versioning.md).

Living document. Anything below a "shipped" version can move between tracks based on player feedback, modpack-author requests, or scope realities.

---

## v1.0 (shipped 2026-05-24)

The playable foundation. Hand-operated production loop, no automation. Targets Minecraft 1.21.1 / NeoForge 21.1.230 / Java 21.

**Frog species (one per biome):** Bog, Cave, Geode, Tide, Infernal, Void. Each spawns naturally in its themed biome and only eats slimes that match it.

**Resource variants:** iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma cream, ender pearl. Right-click any wild slime with the resource item to convert it.

**Production blocks:** Slime Milker (furnace-style appliance), Slime Milk source blocks (place to spawn more of the same variant slime nearby).

**Companion mod integration:** JEI Information pages and Jade in-world tooltips ship out of the box.

**Modpack authoring:** new variants are one JSON file, no Java required. Cross-mod variants gate behind NeoForge `mod_loaded` conditions and silently skip when the source mod isn't present.

---

## v1.1 (next): vanilla resource coverage

Expanding the variant roster to cover every vanilla resource that fits cleanly into one of the six species. JSON-only release: no new Java, no new species.

**Bog Slime gains:** bone, gunpowder, clay ball, rotten flesh, string, leather, feather, slime ball.

**Cave Slime gains:** glow ink sac, obsidian, echo shard.

**Geode Slime gains:** amethyst.

**Tide Slime gains:** ink sac.

**Infernal Slime gains:** netherite scrap, glowstone dust, soul sand, soul soil, netherrack, blaze, quartz.

**Void Slime gains:** chorus fruit, shulker shell.

Tier B candidates (prismarine crystals, nautilus shell, ghast tear) tracked in [docs/v1_1_scope.md](./docs/v1_1_scope.md) with default decisions if not resolved by the v1.1 freeze.

---

## v1.2 (designed): cross-mod variant pools

Modded variants gated by `mod_loaded` conditions for the staple tech mods. Drop the Productive Frogs jar into a modded pack and the relevant variant set activates automatically. No PF code touches the dependent mods' classes.

**Mekanism:** osmium, tin, lead, uranium.

**Create:** zinc, brass, bronze.

**Thermal Series:** silver, nickel, signalum.

**Mythic Metals:** full integration scope TBD.

Other mods land per modpack-author requests. The per-mod placement strategy lives in [docs/cross_mod_compat.md](./docs/cross_mod_compat.md).

---

## v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Hopper-fed Slime Milker:** auto-input slime buckets, auto-output milk buckets. Drop-in upgrade over the v1 Milker.

**Frog Terrarium / Habitat block:** placeable frog housing with input/output inventory. Houses one or more frogs in a contained system.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs. Alternative to milk-spawn proximity.

**Capacity / efficiency upgrades** for habitat blocks.

**Crush 2x recipes** for metallic Froglights via Create / Mekanism / Thermal crushers (cross-mod, optional). The `productivefrogs:crushable/metallic` item tag is already reserved in v1.

**Native crusher block** (optional in-house implementation so the 2x crush path works without external mods).

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

---

## Long-shot ideas (not committed)

Captured during v1 design. Not on the schedule; revisited when v2 ships and we know how much hook-into-vanilla the framework supports.

- **Alchemical slime category** tied to brewing. Infuse with a potion item → slime takes on that potion's effect → killing it drops the matching potion (or splashes its effect in an area). Parent could be a "Brew Slime" spawning near witch huts or nether wart.
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
