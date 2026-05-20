# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Productive Frogs is a Minecraft content mod targeting **NeoForge 21.11.42 on Minecraft 1.21.11, Java 21**. It is **NeoForge-only — no Fabric port, in V1 or ever**, so use NeoForge APIs directly (no Architectury layer, no multi-loader abstractions).

The mod is in early implementation (`feat/frog-breeding` branch). The full design specification is in `docs/` — `design_overview.md`, `architecture.md`, `categories_and_tiers.md`, `versioning.md` are the load-bearing ones. Read those before making non-trivial design changes; they encode decisions that have already been litigated.

## Common Commands

Use the Gradle wrapper (`./gradlew` on bash, `.\gradlew.bat` on PowerShell):

- **`./gradlew build`** — full build + tests (this is what CI runs; PRs are gated on it via the required `build` status check).
- **`./gradlew runClient`** — launch a dev Minecraft client with the mod loaded.
- **`./gradlew runServer`** — dev dedicated server.
- **`./gradlew runData`** — regenerate datagen output into `src/generated/resources` (wired into `processResources` via `sourceSets.main.resources.srcDir`, so generated assets ship in the jar automatically).

`build/` and `run/` are git-ignored; safe to nuke. The Gradle wrapper version is pinned via `gradle/wrapper/gradle-wrapper.properties`. NeoForge dev runs use the `net.neoforged.moddev` 2.0.141 plugin (configured in `build.gradle`).

## Architecture (the parts that aren't obvious from a single file)

### Category model — the single design axis

`com.flatts.productivefrogs.data.Category` is **the** spine of the mod. Six categories — `METALLIC, MINERAL, GEM, AQUATIC, INFERNAL, ARCANE` — each carrying a single ARGB tint. Per the V1 visual lock: **every category variant uses the same base texture + a per-category tint**, never bespoke per-category artwork. `Category.tintArgb()` / `tintRgb()` is the **single source of truth** for color; changing one enum constant repaints every Primed Frog Egg block, Frog Egg bottle, Resource Tadpole/Frog entity, and Resource Tadpole Bucket.

Frog = category (six "guilds"). Slime = species (many variants, one per resource). Frogs only eat slimes of their matching category. **Variety lives on the slime side; the frog roster stays at six.**

### Data-driven slime variants (planned)

Slime variants are JSON files at `data/<ns>/slime_variant/<name>.json`, modeled on vanilla 1.21's cat/wolf/frog variant registry. Adding a new resource = **one JSON + one tag entry, no Java**. Cross-mod variants are wrapped in `neoforge:conditions → mod_loaded`; cross-mod tag entries use `"required": false` so unresolved IDs silently skip when the source mod is absent. See `docs/architecture.md` for the schema. Do not add hard `compileOnly` mod dependencies — compat is JSON, the `compat/` package is intentionally empty.

### Two parallel category surfaces: Frog Egg item vs Primed Frog Egg blocks

There are **two** primer pathways and they look similar but are distinct:

1. **`FrogEggItem` (single item, registered once)** — a bottled glass-bottle-of-frogspawn. Carries category state via the **`productivefrogs:contained_category` data component** (registered in `PFDataComponents`). Absent component = "contains vanilla frogspawn"; present = primed bottle of that category. The item's display name, content-layer tint, and on-use placement behavior all read this component.
2. **`PrimedFrogEggBlock` (six block instances, one per category)** — placed in the world. Each category gets its own block ID + matching `BlockItem`. Selection happens by registry ID, not by block-state property. `PFBlocks.primedEgg(category)` returns the right block.

If you add a new category-aware item, decide upfront which model fits: data-component-on-one-item (like `FrogEggItem` and `ResourceTadpoleBucketItem`) or one-instance-per-category (like Primed Frog Egg blocks). Don't mix them on the same surface.

### Event hooks vs custom recipes

Two key interactions are **event-driven, not recipe-driven**:

- **`FrogspawnBottlingHandler`** — glass bottle + vanilla frogspawn → Frog Egg item (no recipe, hooked via `PlayerInteractEvent.RightClickBlock`).
- **`EggPrimerHandler`** — primer-tagged item + vanilla frogspawn block → Primed Frog Egg block (also `PlayerInteractEvent.RightClickBlock`; iterates `Category.values()` order on tag match).

This is the "stay close to vanilla" principle in practice: where vanilla already has the right UX (water-bottle semantics, fish-bucket semantics, slimeball love-mode), mirror it via event hooks rather than inventing a custom recipe type or tool item.

### Registry/lifecycle layout

Wiring is centralized in `ProductiveFrogs.java`'s constructor — `PFDataComponents`, `PFBlocks`, `PFItems`, `PFEntities`, `PFCreativeTabs` each have a `register(IEventBus)` that hands a `DeferredRegister` to the mod event bus. Game-event listeners (`EggPrimerHandler`, `FrogspawnBottlingHandler`, `PFModBusEvents`, `PFClientEvents`) self-register via `@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)`. Client-only code is gated with `value = Dist.CLIENT`.

Entities reuse vanilla hitboxes (`Tadpole.HITBOX_WIDTH`/`HITBOX_HEIGHT`) and vanilla attribute tables (`Tadpole.createAttributes()`) so vanilla renderers/AI work without per-mod tuning. Custom renderers (`ResourceTadpoleRenderer`, `ResourceFrogRenderer`) subclass the vanilla ones and override `getModelTint(state)` — they don't reimplement the model.

### Item tinting in 1.21.x — the non-obvious gotcha

The legacy `RegisterColorHandlersEvent.Item` event is **gone** in vanilla and NeoForge for 1.21.x. Per-item runtime tinting is now declared in the item model JSON via a `"tints"` array referencing a registered `ItemTintSource` codec. `PFClientEvents.onRegisterItemTintSources` registers `ContainedCategoryTint` and `TadpoleBucketCategoryTint` for that purpose. Block-item inventory icons still pick up tint via `BlockColor` (registered in the same class). If you add a new content-tinted item, register a new `ItemTintSource` rather than reaching for the removed item-color API.

## Project Conventions

- **Java 21, 4-space indent, no tabs, no wildcard imports.** Import order: **alphabetical, one block, no semantic groups** — matches both Mojang vanilla source style (e.g. `com.google.common.*` then `com.mojang.*` then `net.minecraft.*`) and what existing files in this repo do. Don't introduce semantic grouping; the IntelliJ / Google Java Format default is correct. Records for value types; `@Nullable` (JetBrains, shipped with NeoForge) on ambiguous returns.
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `ci:`, `perf:`). Body explains the *why*. One logical change per commit. `main` is protected; all changes via PR.
- **Docs filenames are snake_case** (e.g. `categories_and_tiers.md`). Design changes update the relevant `docs/*.md` in the same PR.
- **Line endings:** `.gitattributes` forces LF for `.java`/`.gradle`/`.json`/`.md`/`.yml` and CRLF for `.bat`/`.cmd`. Don't fight it.
- **No hard mod dependencies.** Cross-mod entries use common tags (`c:ingots/...`) + `"required": false` or `neoforge:conditions → mod_loaded`. The `compat/` Java package stays empty by design.

## Scope Discipline (V1 vs V2)

V1 is "playable foundation + appliance blocks" (hand-operated single-block stations like vanilla brewing stand/composter). **V2 is automation** — hopper integration, power, multiblocks, terrariums. When implementing, check `docs/versioning.md` — if a feature would add power/pipes/multiblocks, it's V2 and shouldn't land in V1 branches. The rule of thumb: if vanilla has a single-block appliance equivalent, it's V1.
