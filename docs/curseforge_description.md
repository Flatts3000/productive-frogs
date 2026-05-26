<!--
This file is the canonical source for the CurseForge project Description.

Workflow:
  1. Edit this file when you want to update the CF page.
  2. Copy the body (everything below this comment) into the CF Description
     field at https://authors.curseforge.com/#/projects/1552728/edit
  3. Save on CF.

Why a separate doc instead of reusing README.md: the CF Description is a
self-contained sales / onboarding surface for players who land on the CF
page without ever visiting GitHub. README.md targets contributors and
existing players. They overlap in spirit, diverge in structure.

Guardrails (per project memory):
  - No hard counts of species / variants / features (changes every release)
  - No mod-internal jargon (species-matched, Resource Slime, datapack)
  - No em-dashes (use hyphens, commas, or restructure)
  - Lead with player benefit; concrete examples beat abstract claims
  - Links use absolute GitHub URLs (CF readers don't have repo context)

The Roadmap section here should stay in sync with the canonical
[ROADMAP.md](../ROADMAP.md) at repo root. When the roadmap shifts, edit
ROADMAP.md first, then mirror the changed lines into the Roadmap section
below, then paste this file's body into CF.
-->

**Renewable resources, farmed by frogs.** Prime slimes with iron, copper, diamonds, or whatever resource you want, feed them to your frogs, and harvest the Froglights they drop. Slime Milkers keep the loop running unattended.

## How it works

1. Find a slime (they spawn in themed biomes).
2. Right-click it with the resource you want: iron ingot, copper ingot, diamond, prismarine shard, ender pearl, and more.
3. Feed the slime to the matching frog.
4. The frog drops a Froglight tied to that resource.
5. Smelt the Froglight in a vanilla furnace. Get your resource back.

## Sustained farming

Stop running around hand-priming wild slimes. Build a **Slime Milker**:

* Drop a Slime Bucket of the variant you want into the input slot.
* Wait a short cook. A bucket of matching Slime Milk comes out.
* Place the Slime Milk in the world. It spawns more of the same slime nearby.

Hopper-compatible. No power, no pipes, no multiblocks. The whole loop runs unattended once you've built it.

## Modpack-friendly

Data-driven and extensible: adding a new resource variant is a single JSON file, no code or recompile needed. JEI Information pages auto-extend to teach the new entry. Cross-mod variants for Mekanism, Create, Thermal, and more are included; each gates behind a soft mod-loaded check and silently skips when the source mod is absent.

Full authoring guide: [docs on GitHub](https://github.com/Flatts3000/productive-frogs/tree/main/docs).

## Companion mods (recommended, optional)

* **[JEI](https://www.curseforge.com/minecraft/mc-mods/jei)**: Information pages on every Productive Frogs item explain the production role, who hunts what, what smelts to what. Hover an item, press **U** or **R**.
* **[Jade](https://www.curseforge.com/minecraft/mc-mods/jade)**: shows the species and variant in the in-world tooltip.

Both are optional. The mod works fine without them; you just lose the contextual UI.

## Platform

* **Minecraft:** 1.21.1
* **Loader:** NeoForge 21.1.230 (NeoForge only; no Fabric port planned)
* **Java:** 21

## Links

* [GitHub repository](https://github.com/Flatts3000/productive-frogs)
* [Issue tracker](https://github.com/Flatts3000/productive-frogs/issues)
* [Source + design docs](https://github.com/Flatts3000/productive-frogs/tree/main/docs)
* [Roadmap (canonical)](https://github.com/Flatts3000/productive-frogs/blob/main/ROADMAP.md)

## Roadmap

Where the mod is going. The [CHANGELOG](https://github.com/Flatts3000/productive-frogs/blob/main/CHANGELOG.md) tracks shipped releases in detail; this tracks the runway. Engineering rationale lives in the [versioning.md doc](https://github.com/Flatts3000/productive-frogs/blob/main/docs/versioning.md). The canonical source for this section is [ROADMAP.md](https://github.com/Flatts3000/productive-frogs/blob/main/ROADMAP.md).

Living document. Anything below a "shipped" version can move between tracks based on player feedback, modpack-author requests, or scope realities.

### Shipped

The mod is fully playable today. Everything below is live on CurseForge. Targets Minecraft 1.21.1 / NeoForge 21.1.230 / Java 21.

**v1.0 (shipped 2026-05-24): foundation + appliances.** Six frog species (Bog, Cave, Geode, Tide, Infernal, Void), each spawning in its themed biome and only eating the slimes that match it. The starter resource set: iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, ender pearl. The Slime Milker appliance and Slime Milk source blocks keep production going hands-off. JEI Information pages and Jade tooltips out of the box. Adding a new resource is a single JSON file, no Java.

**v1.1 (shipped 2026-05-25): vanilla resource coverage.** Every vanilla resource that fits cleanly under one of the six species became farmable. Bog frogs gained the mob drops (bone, gunpowder, clay, rotten flesh, string, leather, feather); Cave gained glow ink sac, obsidian, echo shard; Geode gained amethyst; Tide gained ink sac and prismarine crystals; Infernal gained netherite scrap, glowstone, soul sand, soul soil, netherrack, blaze, quartz; Void gained chorus fruit and shulker shell.

**v1.2 (shipped 2026-05-25): cross-mod variant pools.** Drop the jar into a modded pack and the relevant resource sets light up automatically, no configuration. Mekanism, Create, Thermal, Applied Energistics 2, AllTheOres, Mystical Agriculture, Powah, Industrial Foregoing, and more are covered out of the box. Each entry gates behind a soft mod-loaded check and silently skips when its source mod is absent, so the same jar is safe in any pack.

### v1.3 (next): cross-mod crush yields

With Create, Mekanism, or Thermal installed, crushing an ore or metal Froglight yields double the resource instead of the single unit you get from smelting, matching how those mods already reward ore processing. It activates only when one of those mods is present and changes nothing otherwise.

### v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Hopper-fed Slime Milker:** auto-input slime buckets, auto-output milk buckets.

**Frog Terrarium / Habitat block:** placeable frog housing with input/output inventory.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs.

**Capacity / efficiency upgrades** for habitat blocks.

**Native crusher block:** an optional in-house double-yield crush path that works without external mods.

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

### Long-shot ideas (not committed)

* **Alchemical slime category** tied to brewing. Infuse with a potion item, slime takes on that potion's effect, killing it drops the matching potion.
* **Deep genetics / breeding tree** on the frog side (Forestry-style).
* **New dimensions** (Frog Realm).
* **Tinkers-style upgradable frogs** (gold trim, diamond skin).
* **Quest / advancement integration.**

### Compatibility promise

* v2 won't break v1 worlds.
* v2 machines remain optional. Every v1 capability stays usable without them.
* Cross-mod compatibility add-ons are independent of the v1 / v2 split.

### Explicitly NOT planned

**Fabric port.** Productive Frogs is NeoForge-only by design and will remain so.
