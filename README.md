# Productive Frogs

A content mod for **Minecraft 1.21.1 / NeoForge 21.1.230**. The latest release is whatever sits at the top of the [CHANGELOG](CHANGELOG.md).

**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/productive-frogs) (preferred) | [GitHub Releases](https://github.com/Flatts3000/productive-frogs/releases) (mirror)

## Concept

Productive Frogs is a predator/prey resource-generation mod inspired by Productive Bees, built on Minecraft's vanilla *frog eats magma cube drops froglight* mechanic.

**Core production loop:**

```
Find a parent slime in the world  →  Right-click with a variant primer item
                                  ↓
                              Variant Resource Slime  →  Frog eats it
                                                      ↓
                                          Variant-stamped Configurable Froglight
                                                      ↓
                                          Smelt in furnace  →  Resource
```

Or sustainably, via the Slime Milker:

```
Variant Slime Bucket → Slime Milker → Slime Milk bucket
                                    ↓
                  Place in world → spawns more of the same variant slime
```

## Six species

| Species | Biome | Matching frog | Example variants |
|---|---|---|---|
| **Bog Slime** | swamps, mangrove swamps | Bog Frog | clay, dirt, mud, moss, mycelium, lily pad, leather |
| **Cave Slime** | dripstone caves, lush caves | Cave Frog | iron, copper, gold, redstone, coal |
| **Geode Slime** | mountain peaks (stony / jagged / frozen) | Geode Frog | emerald, diamond, amethyst, lapis, tuff, calcite |
| **Tide Slime** | deep oceans, warm + lukewarm oceans | Tide Frog | prismarine, sponge, sea pickle, nautilus shell |
| **Infernal Slime** | nether wastes, basalt deltas, soul sand valley | Infernal Frog | blaze, quartz, glowstone, soul sand, obsidian, netherite scrap |
| **Void Slime** | end islands | Void Frog | ender pearl, echo shard, sculk, end stone |

Six parent species spawn naturally in their biomes. Vanilla `minecraft:slime` and `minecraft:magma_cube` are NOT part of the production system - only the PF parent species can be infused into Resource Slimes.

**Variety lives on the slime side.** Adding a new variant for a future modpack is one JSON (texture and lang fall back automatically for cross-mod variants), no code change.

## Install

1. Install [NeoForge **21.1.230**](https://neoforged.net/) for Minecraft 1.21.1.
2. Download the Productive Frogs jar from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/productive-frogs) (or [GitHub Releases](https://github.com/Flatts3000/productive-frogs/releases) as a mirror, used while the CF project clears initial moderation) and drop it into `mods/`.
3. **Recommended companion mods:**
   - [JEI 19.21.0.247+](https://www.curseforge.com/minecraft/mc-mods/jei) - surfaces Information pages on every PF item (open inventory, hover, press **U** or **R**).
   - [Jade 15.10.5+](https://www.curseforge.com/minecraft/mc-mods/jade) - shows species + variant in the in-world entity tooltip.

Both are optional. Without them, the mod still works - just no JEI sidebar and no tooltip overlay.

## Quick-start

1. Find a swamp, cave, mountain peak, ocean, nether biome, or end island. A Bog/Cave/Geode/Tide/Infernal/Void Slime will eventually spawn (or `/give @s productivefrogs:cave_slime_spawn_egg` in creative).
2. Hold an iron ingot. Right-click the Cave Slime → it becomes an **Iron Slime**.
3. Bottle a vanilla frogspawn block with a glass bottle → unprimed Frog Egg.
4. Hold an iron ingot, right-click vanilla frogspawn → Cave Primed Frog Egg block.
5. Wait for the egg to hatch into Cave Tadpoles. They mature into Cave Frogs.
6. The Cave Frog will tongue-eat your Iron Slime → drops an Iron-stamped Configurable Froglight.
7. Smelt the Froglight → iron ingot.

For sustained production, feed an Iron Slime to a Slime Milker → Iron Slime Milk. Place the milk source block; it spawns more Iron Slimes nearby.

## Status

**Latest release: v1.8.3.** The full V1 design (hand-operated production loop, six species, the data-driven variant roster) is live, plus cross-mod variant pools, cross-mod crush yields, the Spawnery, frog stat breeding, and Slime Milk catalysts that buff a placed source. As of v1.8, a variant's Slime Milk can be collected, piped, and placed back by fluid-automation mods, so a chosen variant can be farmed fully hands-off. Coal and Blaze Froglights also double as furnace fuel (v1.8.3), burning like the resource they are made of.

| Version line | Scope | Status |
|---|---|---|
| v1.0 | Base mechanics + Slime Milker | ✅ shipped |
| v1.1 | Vanilla resource coverage | ✅ shipped |
| v1.2 | Cross-mod variant pools + observability | ✅ shipped |
| v1.3 | Cross-mod crush yields | ✅ shipped |
| v1.4 | The Spawnery + Jade look-at tooltips | ✅ shipped |
| v1.5 | Frog stat breeding | ✅ shipped |
| v1.6 | Bog recategorization + Slime Milk reliability | ✅ shipped |
| v1.7 | Slime Milk catalysts | ✅ shipped |
| v1.8 | Slime Milk automation (per-variant fluids) + Coal/Blaze Froglight fuel | ✅ shipped |
| v2 | Automation: power/multiblocks/terrariums | 🔭 future |

Full roadmap (player-facing): [`ROADMAP.md`](./ROADMAP.md). Engineering scope rationale: [`docs/versioning.md`](./docs/versioning.md).

## Documentation

| Doc | Purpose |
|---|---|
| [species_as_category_redesign.md](./docs/species_as_category_redesign.md) | The v1.0 design - six species, infusion rules, JEI integration |
| [design_overview.md](./docs/design_overview.md) | The core model and gameplay loop |
| [items_and_blocks.md](./docs/items_and_blocks.md) | Item and block roster |
| [slime_sourcing.md](./docs/slime_sourcing.md) | How players obtain Resource Slimes |
| [farming.md](./docs/farming.md) | Slime Milker production loop |
| [slime_milk_catalysts.md](./docs/slime_milk_catalysts.md) | Catalysts that buff a placed Slime Milk source (count / speed / quantity / infinite) |
| [architecture.md](./docs/architecture.md) | Data-driven variant system, tag layout, JSON registries |
| [cross_mod_compat.md](./docs/cross_mod_compat.md) | Strategy for Mekanism, Create, Mythic Metals, and more |
| [versioning.md](./docs/versioning.md) | V1 / V2 scope split |
| [textures_and_models.md](./docs/textures_and_models.md) | Tint pipeline + texture roster |
| [dev_setup.md](./docs/dev_setup.md) | Local dev environment + companion mod install |
| [curseforge_description.md](./docs/curseforge_description.md) | Canonical source for the CurseForge project Description (mirror manually when changed) |

## Target platform

- **Minecraft**: 1.21.1
- **Loader**: NeoForge 21.1.230 (NeoForge-only - no Fabric port planned)
- **Java**: 21
- **Gradle**: 9.5.1 with `net.neoforged.moddev` 2.0.141

## Building from source

```bash
git clone https://github.com/Flatts3000/productive-frogs.git
cd productive-frogs
./gradlew build              # full build + unit tests
./gradlew runGameTestServer  # in-world GameTests headless
./gradlew runClient          # launch a dev Minecraft client with the mod loaded
```

Build output: `build/libs/productivefrogs-<version>.jar`.

## License

MIT. See `LICENSE`.
