# Productive Frogs

A Minecraft content mod for **NeoForge** on **Minecraft 1.21.11** (latest 1.21.x at time of scaffold).

## Concept

Productive Frogs is a predator/prey resource-generation mod inspired by the genre established by Productive Bees, but built on Minecraft's vanilla frog-eats-magma-cube-drops-froglight mechanic.

**Core loop:**

```
Find frogspawn → bottle with a glass bottle → prime egg with category material →
hatch into Resource Frog → feed it slime of the right category → drop resource
```

The design separates two roles:

- **Frog = category** (a "guild" or diet). Six frog types: Metallic, Mineral, Gem, Aquatic, Infernal, Arcane.
- **Slime = species**. Many slime variants — one per resource. Each has its own drop.
- **Frogs only eat slimes whose category matches their diet.** Mismatch = ignore.

Variety lives on the slime side; the frog roster stays small and manageable. This makes the mod inherently data-driven and trivially extensible — adding a new modded resource is one JSON file + one tag entry, no code.

## Status

**Design phase.** No code yet. See [`docs/`](./docs/) for the in-progress design specification.

## Documentation

| Doc | Purpose |
|---|---|
| [design_overview.md](./docs/design_overview.md) | The core model and gameplay loop |
| [categories_and_tiers.md](./docs/categories_and_tiers.md) | The 5 categories, tier ladder, primer system |
| [items_and_blocks.md](./docs/items_and_blocks.md) | Item and block roster: Frog Eggs, Resource Slimes, Slime Milker, etc. |
| [slime_sourcing.md](./docs/slime_sourcing.md) | How players obtain Resource Slimes (split discovery + infusion) |
| [farming.md](./docs/farming.md) | V1 farming loop: Slime Milker, Slime Milk fluid, Froglight processing |
| [progression.md](./docs/progression.md) | Player journey from tier 0 to tier 6 |
| [textures_and_models.md](./docs/textures_and_models.md) | Tint-based colorization, texture roster, model templates, dev pipeline |
| [texture_prompts.md](./docs/texture_prompts.md) | AI generation prompts for every texture, with tooling and post-processing tips |
| [architecture.md](./docs/architecture.md) | Data-driven slime variant system, tag layout, JSON registries |
| [cross_mod_compat.md](./docs/cross_mod_compat.md) | Strategy for Mekanism, Create, Thermal, Mythic Metals integration |
| [versioning.md](./docs/versioning.md) | V1 / V2 scope split (V1 = base mechanics, V2 = automation) |

| [open_source.md](./docs/open_source.md) | OSS structure, license, contributor conventions, CI |
| [infrastructure.md](./docs/infrastructure.md) | gh CLI setup script for the GitHub repo (sibling `infra/` folder) |
| [open_questions.md](./docs/open_questions.md) | Unresolved design decisions awaiting answers |

## Target Platform

- **Minecraft**: 1.21.11 (latest 1.21.x)
- **Loader**: NeoForge 21.11.42 **only** — no Fabric port planned, in V1 or ever
- **Java**: 21
- **Gradle**: 9.5.1 with `net.neoforged.moddev` 2.0.141
- **Cross-mod focus**: Mekanism, Create, Thermal (via common tags + conditional datapacks)

## Open Source

- **License**: MIT
- **Repo management**: gh CLI script in sibling `D:\minecraft-repos\infra\` folder (no PAT needed, uses existing `gh auth login` session)
- See [docs/open_source.md](./docs/open_source.md) and [docs/infrastructure.md](./docs/infrastructure.md) for details.
