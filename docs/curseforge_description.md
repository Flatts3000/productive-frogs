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
2. Right-click it with the resource you want: iron ingot, copper ingot, diamond, prismarine shard, magma cream, ender pearl, and more.
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

Adding a new resource is a single JSON file. Drop one entry under `data/<pack>/productivefrogs/slime_variant/<name>.json`, point it at a primer item and a smelting recipe, and the variant is live. JEI Information pages auto-extend to teach the new entry. Cross-mod variants (Mekanism, Create, Thermal, and more) gate behind NeoForge `mod_loaded` conditions so they silently skip when the source mod is absent.

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

### v1.0 (shipped 2026-05-24)

The playable foundation. Hand-operated production loop, no automation. Targets Minecraft 1.21.1 / NeoForge 21.1.230 / Java 21.

**Frog species (one per biome):** Bog, Cave, Geode, Tide, Infernal, Void. Each spawns naturally in its themed biome and only eats slimes that match it.

**Resource variants:** iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma cream, ender pearl. Right-click any wild slime with the resource item to convert it.

**Production blocks:** Slime Milker (furnace-style appliance), Slime Milk source blocks (place to spawn more of the same variant slime nearby).

**Companion mod integration:** JEI Information pages and Jade in-world tooltips ship out of the box.

**Modpack authoring:** new variants are one JSON file, no Java required. Cross-mod variants gate behind NeoForge `mod_loaded` conditions and silently skip when the source mod isn't present.

### v1.1 (next): vanilla resource coverage

Expanding the variant roster to cover every vanilla resource that fits cleanly into one of the six species. JSON-only release: no new Java, no new species.

**Bog Slime gains:** bone, gunpowder, clay ball, rotten flesh, string, leather, feather, slime ball.

**Cave Slime gains:** glow ink sac, obsidian, echo shard.

**Geode Slime gains:** amethyst.

**Tide Slime gains:** ink sac.

**Infernal Slime gains:** netherite scrap, glowstone dust, soul sand, soul soil, netherrack, blaze, quartz.

**Void Slime gains:** chorus fruit, shulker shell.

### v1.2 (designed): cross-mod variant pools

Modded variants gated by `mod_loaded` conditions for the staple tech mods. Drop the Productive Frogs jar into a modded pack and the relevant variant set activates automatically. No PF code touches the dependent mods' classes.

**Mekanism:** osmium, tin, lead, uranium.

**Create:** zinc, brass, bronze.

**Thermal Series:** silver, nickel, signalum.

**Mythic Metals:** full integration scope TBD.

### v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Hopper-fed Slime Milker:** auto-input slime buckets, auto-output milk buckets.

**Frog Terrarium / Habitat block:** placeable frog housing with input/output inventory.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs.

**Capacity / efficiency upgrades** for habitat blocks.

**Crush 2x recipes** for metallic Froglights via Create / Mekanism / Thermal crushers.

**Native crusher block** (optional in-house implementation).

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

### Long-shot ideas (not committed)

* **Alchemical slime category** tied to brewing. Infuse with a potion item, slime takes on that potion's effect, killing it drops the matching potion.
* **Deep genetics / breeding tree** on the frog side (Forestry-style).
* **New dimensions** (Frog Realm).
* **Tinkers-style upgradable frogs** (gold trim, diamond skin).
* **Quest / advancement integration.**

### Compatibility promise

* v2 datapacks won't break v1 worlds.
* v2 machines remain optional. Every v1 capability stays usable without them.
* Cross-mod compat datapacks are independent of the v1 / v2 split.

### Explicitly NOT planned

**Fabric port.** Productive Frogs is NeoForge-only by design and will remain so.
