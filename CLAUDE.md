# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Productive Frogs is a Minecraft content mod targeting **NeoForge 21.1.230 on Minecraft 1.21.1, Java 21**. It is **NeoForge-only - no Fabric port, ever**, so use NeoForge APIs directly (no Architectury layer, no multi-loader abstractions).

The mod was originally built on 1.21.11 and **backported to 1.21.1** to match the Sky Frogs modpack (history in `docs/port_mc_1_21_1.md`). Several APIs differ from the newer line: the per-item tint pipeline, GameTest registration, and item/block registration shape all use the **older 1.21.1 forms** documented below. When in doubt, copy the pattern from an existing sibling file rather than reaching for a newer-MC API.

V1 has shipped through v1.10 (beta on CurseForge since v1.9.1): foundation + appliances (v1.0), vanilla resource coverage (v1.1), cross-mod variant pools + observability (v1.2), cross-mod crush yields (v1.3), the Spawnery + Jade tooltips (v1.4), frog stat breeding (v1.5), the organic Bog rework (v1.6), Slime Milk catalysts (v1.7), per-variant automatable milk (v1.8), Refined Storage support (v1.9), and the obsidian -> Infernal recategorization (v1.10). The per-release detail lives in `ROADMAP.md`'s Shipped section - don't re-enumerate it here on every release. **Automation is V2** and has not started. The load-bearing design docs are `docs/architecture.md`, `docs/versioning.md`, `docs/species_as_category_redesign.md` (the **current** category model - but its per-variant tables are the v1.5-era record, kept as history; the `slime_variant` JSONs are authoritative for which variant sits in which species, and later recategorizations update README/`docs/canonical_ordering.md` instead), and `ROADMAP.md`. Read those before non-trivial design changes; they encode decisions already litigated.

## Common Commands

Use the Gradle wrapper (`./gradlew` on bash, `.\gradlew.bat` on PowerShell):

- **`./gradlew build`** - full build + JUnit unit tests. CI runs this as the `build` job; `main` branch protection requires it.
- **`./gradlew runGameTestServer`** - boots a headless server and runs every in-world GameTest (`PFGameTests`). CI runs this as a **separate** `gameTest` job, also required for merge - `build` does **not** invoke it. Run it locally before pushing if you've touched `gametest/`, the event handlers, entity AI, or block tick behavior.
- **`./gradlew runClient`** / **`./gradlew runServer`** - dev client / dedicated server with the mod loaded.
- **`./gradlew runData`** - regenerate datagen output into `src/generated/resources` (wired into `processResources`, so generated assets ship in the jar automatically).
- **`./gradlew publishCurseForge`** - upload the built jar to CurseForge (project 1552728). Reads `CURSEFORGE_API_KEY` from `.env`; the changelog body is extracted from `CHANGELOG.md` by matching `## v<mod_version>`. **Not run by CI** - it's a manual release step.

**Run a single test:** JUnit - `./gradlew test --tests '*SlimeVariantTest'`. A single in-world GameTest - launch `runClient`, then `/test run productivefrogs:<test_name>`.

`build/` and `run/` are git-ignored; safe to nuke. Versions are pinned in `gradle.properties` (`minecraft_version=1.21.1`, `neoforge_version=21.1.230`, `mod_version`). NeoForge dev runs use the `net.neoforged.moddev` 2.0.141 plugin (`build.gradle`).

## Architecture (the parts that aren't obvious from a single file)

### Category model - the single design axis (species-as-category)

`com.flatts.productivefrogs.data.Category` is **the** spine of the mod. Six constants - `CAVE, GEODE, BOG, TIDE, INFERNAL, VOID` - **named for the parent slime species, not abstract categories**. This is the V1.5 "species-as-category" redesign (`docs/species_as_category_redesign.md`): the core insight is *species IS the category*. The enum stays under the hood as the join key that wires the datapack pipeline together, but the player-facing identity is the species (Cave Slime, Cave Frog, Cave Froglight). The old abstract names (METALLIC/MINERAL/GEM/AQUATIC/INFERNAL/ARCANE) are gone.

Each constant carries one ARGB tint. **Every variant uses the same base texture + a per-species tint**, never bespoke per-species artwork. `Category.tintArgb()` / `tintRgb()` is the **single source of truth** for color; changing one enum constant repaints every Primed Frog Egg block, Frog Egg bottle, Resource Tadpole/Frog entity, and bucket.

**Persistence is by name** (StringRepresentable / `Category.CODEC`; entity NBT writes `getCategory().name()`; the `contained_category` data component is `.persistent(Category.CODEC)`). `ordinal()` is used **only** for transient network sync (`STREAM_CODEC` + synced entity data), regenerated each session. So reordering the enum is save-safe.

Frog = one per species (six). Slime = that species' variants (many, one per resource). A frog eats only slimes of its own species. **Variety lives on the slime side; the frog roster stays at six.**

### Data-driven slime variants - datapack registry

Slime variants are a **NeoForge datapack registry** (`PFRegistries.SLIME_VARIANT`), created via `DataPackRegistryEvent.NewRegistry` in `PFDataPackRegistryEvents`. Entries live at `data/<ns>/productivefrogs/slime_variant/<name>.json` - the double-namespace path is NeoForge convention, not a typo. `SlimeVariant`'s codec decodes them at server boot; lookups go through `level.registryAccess().lookupOrThrow(PFRegistries.SLIME_VARIANT)`.

`SlimeVariant` codec fields: `primer_item` (optional, exact item id), `primer_tag` (optional item tag), `category`, `primary_color`, `secondary_color`, `weight`, `inner_block` (optional - the vanilla block rendered inside the slime), `spawn_entity` (optional). The codec **rejects any variant declaring neither `primer_item` nor `primer_tag`** (a variant nothing can prime would still pollute the random discovery pool). Note there is no `result_item` field - the smelt-back resource lives in each variant's generated smelting recipe JSON, not on the variant.

Three lookups matter:
- `findByPrimer(registry, stack)` - resolves a held stack to a variant by exact `primer_item` OR `primer_tag` membership. **An exact item match always wins over a tag match**, deterministically. Used by `SlimeInfusionHandler`.
- `findByPrimerItem(registry, itemId)` - exact `primer_item`-only match (legacy callers / tests).
- `pickWeighted(registry, category, random)` - weighted random pick within a category, used by `SlimeSplitDiscoveryHandler` on a split that converts without a specific primer.

Adding a resource = **one JSON, no Java**. Cross-mod variants gate behind `neoforge:conditions → mod_loaded` and key off the NeoForge `c:` common tags via `primer_tag` (e.g. `primer_tag: c:ingots/tin`), so one entry covers every mod that provides that tag. There is intentionally **no `compat/` Java package** and no hard `compileOnly` mod deps - compat is JSON. Scripts under `scripts/` template the cross-mod and v1.1 variant JSONs. See `docs/cross_mod_compat.md`.

### Two parallel category surfaces: Frog Egg item vs Primed Frog Egg blocks

Two primer pathways, similar-looking but distinct:

1. **`FrogEggItem` (one item)** - a bottled glass-bottle-of-frogspawn. Carries species via the **`productivefrogs:contained_category` data component** (`PFDataComponents`). Absent = vanilla frogspawn; present = primed bottle. Display name, content-layer tint, and placement behavior all read this component.
2. **`PrimedFrogEggBlock` (six instances, one per species)** - placed in the world. Each species gets its own block id + `BlockItem`; selection is by registry id, not block-state. `PFBlocks.primedEgg(category)` returns the right one.

When adding a category-aware surface, pick one model up front - data-component-on-one-item (like `FrogEggItem`, `ResourceTadpoleBucketItem`, `ConfigurableFroglightItem`) or one-instance-per-species (like Primed Frog Egg blocks). Don't mix them on one surface.

### Event hooks vs custom recipes

Two key interactions are **event-driven, not recipe-driven** (both `PlayerInteractEvent.RightClickBlock`):
- **`FrogspawnBottlingHandler`** - glass bottle + vanilla frogspawn -> Frog Egg item.
- **`EggPrimerHandler`** - primer item + vanilla frogspawn block -> Primed Frog Egg block.

This is the "stay close to vanilla" principle: where vanilla already has the right UX (water-bottle, fish-bucket, slimeball love-mode), mirror it via an event hook rather than inventing a custom recipe type or tool item.

### Appliance blocks (furnace-style GUI stations)

The hand-operated processing blocks - the **Slime Milker** and the **Spawnery** - share one shape; copy it for any new appliance rather than inventing a layout:

- `content/block/<Name>Block` - the placed block (carries a `LIT`-style blockstate for the active glow), wires the BE ticker.
- `content/block/entity/<Name>BlockEntity` - `implements MenuProvider`; owns the inventory, a `ContainerData` (syncs cook/burn progress to the open screen), and a **`static serverTick`** running a vanilla-furnace-style burn+cook loop (consume fuel to ignite a burn, advance `cookProgress`, `complete()` on the tick that fills the output).
- `content/block/entity/<Name>Inventory` - the slot model behind a slot-bounded `ItemStackHandler`, exposing side-aware `inputView()` / `outputView()` slot views.
- `content/menu/<Name>Menu` + `client/screen/<Name>Screen` - the container menu (registered via `PFMenuTypes`, screen bound in `PFClientEvents`) and its furnace-shaped GUI.

**Hopper I/O is a capability, not a hook:** `PFModBusEvents.onRegisterCapabilities` registers `Capabilities.ItemHandler.BLOCK` (the **1.21.1** id - *not* the newer `Capabilities.Item.BLOCK` / `ResourceHandler<ItemResource>`) for each appliance BE, routing the down face to `outputView()` and other faces to `inputView()`. Mutate inventory through `extractItem` / `setStackInSlot` (never a raw shrink on a returned stack) so each change fires `onContentsChanged -> setChanged` independently. Appliances are **V1** (single-block); multiblocks/power are V2.

### Config-gated content - the `config_enabled` datapack condition

The Spawnery ships **off by default**: its crafting recipe carries `{"type":"productivefrogs:config_enabled","config":"spawnery"}`. `ConfigEnabledCondition` (`data/condition/`) is a NeoForge `ICondition` registered to `NeoForgeRegistries.Keys.CONDITION_CODECS` via `PFConditions`. When the gated flag is off the recipe drops at datapack load, so the block is uncraftable and JEI shows no recipe; the **placed block still works** (the flag gates crafting / JEI / creative visibility only). The condition reads `PFConfig` guarded by `SPEC.isLoaded()` and **fails closed** (disabled) if the spec isn't loaded yet, rather than throwing. The gateable flags are a closed enum (`ConfigEnabledCondition.Key`) so a JSON typo fails at decode time - add a `Key` to wire a new gated feature.

A separate pack-override surface: the Spawnery's six `spawnery_primer/<species>` **item tags** (`PFItemTags`) decide which held item primes which species' frogspawn. This is deliberately *not* `SlimeVariant.findByPrimer` (that maps the whole resource pool); the Spawnery wants exactly one primer per species and a clean per-species override. A primer is always required - a slime ball primes vanilla frogspawn; an empty/untagged slot produces nothing. See `docs/spawnery.md` and `docs/modpack_integration.md`.

### Registry / lifecycle layout

Wiring is centralized in `ProductiveFrogs.java`'s constructor: `PFDataComponents`, `PFFluidTypes`, `PFFluids`, `PFBlocks`, `PFItems`, `PFBlockEntities`, `PFMenuTypes`, `PFEntities`, `PFSensors`, `PFCreativeTabs`, `PFConditions` each expose a `register(IEventBus)` that hands a `DeferredRegister` to the mod bus. Game-event listeners (`EggPrimerHandler`, `FrogspawnBottlingHandler`, `SlimeInfusionHandler`, `SlimeSplitDiscoveryHandler`, `FrogTongueDropHandler`, `PFModBusEvents`, `PFClientEvents`, `PFDataPackRegistryEvents`) self-register via `@EventBusSubscriber(modid = MOD_ID)`; client-only code adds `value = Dist.CLIENT`.

**Constructor ordering is load-bearing in two places:** `PFFluidTypes` before `PFFluids` (`BaseFlowingFluid.Properties` resolves its FluidType holder at fluid-build time), and `PFBlocks` before `PFBlockEntities`/`PFMenuTypes` (each appliance `BlockEntityType.Builder.of` references its block at BE-registration time). The comments in `ProductiveFrogs.java` call both out - don't reorder. `PFConditions` has no ordering dependency.

**Slime Milk is per-variant (v1.8+).** Each variant has its own `<variant>_slime_milk` fluid (source + flowing), source block, and bucket, minted dynamically at mod-init by `PFVariantMilk.bootstrap(...)` from the variant list `VariantFluidDiscovery.discover()` reads off the bundled `productivefrogs/variants_index.json` (cross-mod variants whose mod is absent are skipped). The variant is baked into the source block at registration, so a tank-mod raw `setBlock` placement still spawns the right variant. **This is a deliberate reversal of the v1.1-v1.7 single-fluid model**: tank/pipe mods key on the `Fluid` registry object and never read a component, so per-variant fluids are the only way automated milk preserves its variant (issue #129, shipped v1.8.0). Restart-to-apply is expected and accepted - a variant only gets milk if it's known at mod-init (in the bundled index); a runtime world-datapack-only variant gets no milk. The earlier "~35 milk objects, collapse them" guidance is obsolete - per-variant milk is the intended design now. See `docs/automated_milk_variants.md`.

Entities reuse vanilla hitboxes (`Tadpole.HITBOX_WIDTH/HEIGHT`) and attribute tables (`Tadpole.createAttributes()`) so vanilla renderers/AI work without per-mod tuning. There is **one** `ResourceFrog`, **one** `ResourceTadpole`, **one** `ResourceSlime` class, each category/variant-parameterized via synced data. Custom renderers subclass the vanilla ones and override the model tint - they don't reimplement the model.

### Slime species -> category mapping (datapack registry)

Six parent species, one per category, mapped via the `productivefrogs:parent_species` datapack registry (`PFRegistries.PARENT_SPECIES`). Defaults at `data/productivefrogs/productivefrogs/parent_species/`:

- `productivefrogs:cave_slime` -> `cave`
- `productivefrogs:geode_slime` -> `geode`
- `productivefrogs:bog_slime` -> `bog`
- `productivefrogs:tide_slime` -> `tide`
- `productivefrogs:infernal_slime` -> `infernal`
- `productivefrogs:void_slime` -> `void`

**Vanilla `minecraft:slime` and `minecraft:magma_cube` are NOT parent species** - each PF species is its own EntityType, and `SlimeInfusionHandler` explicitly rejects vanilla slimes as infusion targets (V1.5 decision Q1). `SlimeSplitDiscoveryHandler.categoryForParent` does an EntityType-id lookup against the registry (no `instanceof` chain, no subclass-ordering footgun). Add a modded slime to the discovery loop by dropping a `parent_species` JSON with `{ "entity_type": "...", "category": "..." }`.

### Custom brain sensor for species-filtered tongue targeting

"Frogs eat only their own species' slimes" is enforced in two independent places - keep both:
1. **AI layer** - `ResourceFrogAttackablesSensor` (`PFSensors`) replaces vanilla's `FROG_ATTACKABLES` in `ResourceFrog`'s brain provider, writing only same-category slimes into `NEAREST_ATTACKABLE`. Off-species prey is never targeted.
2. **Drop layer** - `FrogTongueDropHandler`'s `LivingDeathEvent` listener re-checks `frog.getCategory() == slime.getCategory()` before emitting a drop. Backstop for kills the sensor didn't gate (commands, environmental damage credited to the frog).

The variant-aware path emits a `ConfigurableFroglightItem` stamped with the `SLIME_VARIANT` component; the fallback emits the species' `<category>_froglight` decorative block item.

### GameTest layer

In-world tests live in `PFGameTests.java`, run via `./gradlew runGameTestServer` (a required CI job). Two non-obvious things, **both 1.21.1-specific**:
- **Registration is annotation-based.** The class is `@GameTestHolder(MOD_ID)`; each test method is `@GameTest(template = "...", timeoutTicks = ...)`; the holder is registered through `RegisterGameTestsEvent`. (This is the **opposite** of the newer-MC `DeferredRegister<Consumer<GameTestHelper>>` on `BuiltInRegistries.TEST_FUNCTION` - don't use that pattern here.)
- **Structure NBT is singular**: `data/<ns>/structure/<name>.nbt`. The shared empty plot is `data/productivefrogs/structure/empty_5x5x5.nbt`. (All datapack dirs are singular on 1.21.1: `recipe/`, `loot_table/`, `tags/item/`, `tags/entity_type/`.)

Use `helper.succeedWhen` / `runAfterDelay` / `onEachTick` rather than busy-waiting. **GameTest is blind to client visuals** (tints, render types, textures, lang fallbacks) - those need a manual `runClient` pass; see `docs/testing.md`.

### Item tinting on 1.21.1 - the non-obvious gotcha

Per-item runtime tinting uses the **legacy `RegisterColorHandlersEvent.Item` event** with `ItemColor` lambdas, registered in `client/PFClientEvents.java` (`onRegisterItemColors`). The newer JSON-driven `ItemTintSource` pipeline (a `"tints"` array in the item model) **does not exist on 1.21.1** - do not reach for it, and there are no `ItemTintSource` classes in the tree. Block-item inventory icons tint via `BlockColor` (registered in the same class). Add a content-tinted item by adding an `ItemColor` lambda to that event.

**Fluid tint is per-variant FluidType.** Each `<variant>_slime_milk` fluid has its own `FluidType` that knows its variant id, so `IClientFluidTypeExtensions.getTintColor(state, getter, pos)` reads `registry.get(thatVariant).primaryColor()` directly (registered per-variant in `PFClientEvents`). Both source and flowing blocks of a variant are that variant's own fluid, so the whole poured pool tints correctly with **no source walk-back** (the pre-v1.8 single-fluid design needed a walk-back because flowing milk had no variant BE; per-variant fluids removed that). One shared greyscale `slime_milk_still/flow` texture set serves every variant; only the tint differs.

### Container screens on 1.21.1 - the renderTooltip gotcha

`AbstractContainerScreen#render` on **1.21.1 NeoForge does not call `renderTooltip`** - a screen that overrides only `renderBg` shows **no item tooltips** on slot hover. Every appliance screen must override `render(...)` to call `super.render(...)` then `this.renderTooltip(gui, mouseX, mouseY)` (see `SlimeMilkerScreen` / `SpawneryScreen`). `super.render` does not draw the tooltip, so there's no double-draw.

### Client integrations - JEI and Jade (both `compileOnly`)

Two optional integrations live under `client/` (**not** `compat/` - the same no-`compat/`-package rule applies):
- **JEI** (`client/jei/ProductiveFrogsJeiPlugin`, `@JeiPlugin`) - registers per-component **subtype interpreters** (so Slime Bucket / Slime Milk Bucket / Frog Egg / Configurable Froglight variants show as distinct entries instead of collapsing into one) and per-item **info pages** walked from the `SlimeVariant` registry. `compileOnly` API + `runtimeOnly` jar in dev.
- **Jade** (`client/jade/ProductiveFrogsJadePlugin`, `@WailaPlugin`) - in-world look-at tooltips for the appliances. **`compileOnly` only** - Jade stays a manual `run/mods` drop-in at runtime (adding it `runtimeOnly` double-loads against the drop-in and trips NeoForge's duplicate-modid check); when absent the plugin class never loads. **Gotcha:** every registered plugin UID needs a `config.jade.plugin_<modid>.<uid>` lang key in `en_us.json`, or Jade throws an `AssertionError` and the client resource reload fails.

### Observability - PFDebug

A cross-cutting, opt-in debug logger (`PFDebug`) spans all layers (lifecycle, registry, config, infusion, split, tongue, egg, sensor, milker, milk_source, render, tint). Off by default; enable with `-Dproductivefrogs.debug=<areas>` or the `/pf debug <area> on` command. It logs each layer's resolution decisions to `latest.log` with a greppable `[PF/<area>]` prefix. Use it instead of adding ad-hoc logging when chasing a layer's behavior - client-render bugs in particular are invisible to GameTest, and this is how you trace them.

## Project Conventions

- **Java 21, 4-space indent, no tabs, no wildcard imports.** Import order: **alphabetical, one block, no semantic groups** - matches Mojang vanilla style and existing files here. Records for value types; `@Nullable` (JetBrains, ships with NeoForge) on ambiguous returns.
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `ci:`, `perf:`). Body explains the *why*. One logical change per commit. `main` is protected; changes go via PR (squash-merged). Branch protection requires the `build` and `gameTest` CI jobs **and resolved review conversations** before merge.
- **Docs filenames are snake_case** (`categories_and_tiers.md`). Design changes update the relevant `docs/*.md` in the same PR.
- **Line endings:** `.gitattributes` forces LF for `.java`/`.gradle`/`.json`/`.md`/`.yml`, CRLF for `.bat`/`.cmd`. Don't fight it.
- **No hard mod dependencies.** Cross-mod entries use `c:` common tags + `neoforge:conditions → mod_loaded`. No `compat/` Java package - if you reach for one, stop and use a JSON condition instead.

## Scope Discipline (V1 vs V2)

V1 is "playable foundation + appliance blocks" (hand-operated single-block stations, like a vanilla brewing stand or composter) and has shipped through v1.4.1. **V2 is automation** - power, multiblocks, terrariums. Check `docs/versioning.md` and `ROADMAP.md`: if a feature adds power/pipes/multiblocks it's V2 and shouldn't land in a V1.x branch. Rule of thumb: if vanilla has a single-block appliance equivalent, it's V1.
