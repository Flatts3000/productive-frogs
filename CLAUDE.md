# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Productive Frogs is a Minecraft content mod targeting **NeoForge 26.1.2.76 on Minecraft 26.1.2, Java 25** (toolchain `JavaLanguageVersion.of(25)`; on this machine set `JAVA_HOME="C:/Program Files/Java/jdk-25"` for every gradlew invocation). It is **NeoForge-only - no Fabric port, ever**, so use NeoForge APIs directly (no Architectury layer, no multi-loader abstractions). The mod is **standalone** - it was decoupled from the Sky Frogs modpack when it left 1.21.1. The **1.21.1 line is frozen** on branch `mc-1.21.1` (hotfix-only, versions `1.24.x`; see `docs/maintenance_1_21_1.md` on that branch). `main` is the active **2.x / 26.1** line.

**2.0 is the mob-drop redesign line** (`mod_version=2.0.0-alpha.1`). Its defining feature is the **predation system** (epic #281, `docs/predator_frogs.md`): all mob drops now come from a frog eating the mob - bred Predator Frogs eat vanilla mobs in the world for player-credited loot, and Apex Frogs arm the four **boss altars** (Wither / Dragon / Warden / Elder Guardian) that pay raw boss drops. The 21 mob-derived slime variants and the v1.14 catalyst-altar mechanism are retired. The 26.1 port itself was a deliberate **re-implementation, not a 1:1 migration**: `docs/port_mc_26_1.md` is the phase plan and `docs/port_mc_26_1_reimplementation.md` records the R-numbered redesign decisions (R-1 single-fluid milk, R-3 transactional transfer, R-6 registry GameTests, ...). Read those plus `docs/architecture.md` and `ROADMAP.md` before non-trivial design changes; they encode decisions already litigated. Do NOT re-enumerate the 1.x release history - it lives on the `mc-1.21.1` branch's docs.

## Common Commands

Use the Gradle wrapper (`./gradlew` on bash, `.\gradlew.bat` on PowerShell), with `JAVA_HOME` pointed at JDK 25:

- **`./gradlew build`** - full build + JUnit unit tests (moddev's JUnit integration boots a real mod context: registries populated, tags resolvable). CI runs this as the `build` job; `main` branch protection requires it.
- **`./gradlew runGameTestServer`** - boots a headless server and runs every registered in-world GameTest. CI runs this as a **separate** `gameTest` job, also required for merge - `build` does **not** invoke it. Run it locally before pushing if you've touched `gametest/`, the event handlers, entity AI, or block tick behavior.
- **`./gradlew runClient`** / **`./gradlew runServer`** - dev client / dedicated server with the mod loaded. Both force the EE lane on via `-Dproductivefrogs.equivalence=true` (dev-only).
- **`./gradlew runClientData`** / **`./gradlew runServerData`** - datagen into `src/generated/resources` (wired into `processResources`). The moddev 2.0 plugin **split the old `data` run into `clientData`/`serverData`** - there is no `runData`. Note `prepareAllRuns` deliberately excludes the data runs (moddev 2.0.141 prepare-side bug; see build.gradle).
- **`./gradlew prepareAllRuns`** - regenerates the run VM-args files IntelliJ launches need after a `clean`.
- **`./gradlew publishCurseForge`** - upload the built jar to CurseForge (project 1552728) as **releaseType `alpha`, game version `26.1.2`, Java 25**. Reads `CURSEFORGE_API_KEY` from `.env`; the changelog body is extracted from `CHANGELOG.md` by matching `## v<mod_version>`. **Not run by CI** - manual release step.

**Run a single test:** JUnit - `./gradlew test --tests '*SlimeVariantTest'`. A single in-world GameTest - launch `runClient`, then `/test run productivefrogs:<test_name>`.

`build/` and `run/` are git-ignored; safe to nuke. Versions are pinned in `gradle.properties` (`minecraft_version=26.1.2`, `neoforge_version=26.1.2.76`, `mod_version`). Dev runs use the `net.neoforged.moddev` 2.0.141 plugin. Artifact names carry the MC version (`productivefrogs-26.1.2-2.0.0-alpha.1.jar`) so the two lines' jars are unmistakable.

## Architecture (the parts that aren't obvious from a single file)

### Category model - the species axis (species-as-category)

`com.flatts.productivefrogs.data.Category` is the spine of the resource side. Six constants - `CAVE, GEODE, BOG, TIDE, INFERNAL, VOID` - **named for the parent slime species** (`docs/species_as_category_redesign.md`): species IS the category. Each constant carries one ARGB tint; **every variant uses the same base texture + a per-species tint**, never bespoke per-species artwork, and `Category.tintArgb()` is the single source of truth for color. **Persistence is by name** (`Category.CODEC` / StringRepresentable); `ordinal()` is only for transient network sync. Reordering the enum is save-safe. Slime variety lives on the slime side (one variant per resource); the resource-frog roster stays at six.

### FrogKind - the unified frog identity (2.0)

`data/FrogKind` is a **sealed interface** (`Resource` / `Midas` / `Predator` / `Apex`) that replaced the pre-2.0 Category-ordinal + Midas-boolean pair. Identity is one synced/saved string id (`"resource/bog"`, `"midas"`, `"predator/prowler"`); branch sites use exhaustive `switch` pattern matching, so adding a kind fails compilation at every decision site instead of falling through an if-chain. `canMateWith`/`offspringWith` hold the **pure** pairing rules (same-kind breeds true; four designated resource crosses produce predators, four predator crosses produce Apexes); config gating (`predators.enabled`, `sameSpeciesOnly`) is applied by the caller (`ResourceFrog#canMate`). `fallbackCategory()` keeps legacy category-reading surfaces (tints, Jade, the egg carrier) working for non-species kinds. On read, the legacy `"Midas"`/`"Category"` NBT keys **win over** `"Kind"` (26.1's `TypedEntityData.loadInto` merges egg NBT onto a defaulted entity - reading Kind first hatched everything BOG). Don't clone new flags beside it - extend the sealed hierarchy.

### Data-driven slime variants - datapack registry

Slime variants are a NeoForge datapack registry (`PFRegistries.SLIME_VARIANT`, created in `PFDataPackRegistryEvents`). Entries live at `data/<ns>/productivefrogs/slime_variant/<name>.json` (double-namespace path is NeoForge convention). Codec fields: `primer_item` / `primer_tag` (at least one required - decode error otherwise), `category`, `primary_color`, `secondary_color`, `weight` (**`weight 0` = prime-only**, excluded from split discovery - the boss-gated tier), `inner_block`, `spawn_entity`. **`spawn_catalyst` is RETIRED in 2.0**: the codec decodes it only to reject with a datapack-load error pointing at `docs/boss_catalyst_altar.md` (historical) - boss gating is now weight 0 + the boss altars. The shipped roster is **vanilla-only (~39 variants)**; the 21 mob-derived variants (bone, string, leather, ...) were retired in favor of predation (kept by ruling: `armadillo_scute`, `turtle_scute`, `honeycomb` - husbandry, not kill loot).

Three lookups: `findByPrimer` (exact `primer_item` beats `primer_tag`, deterministically - used by `SlimeInfusionHandler`), `findByPrimerItem` (exact-only), `pickWeighted` (split discovery). Adding a resource = **one JSON, no Java** - but **run `scripts/generate_resource_slime_textures.py` after adding an `inner_block`** (the baked inner-cube texture is NOT build-validated; skipping silently renders the slime hollow), and the milk-asset + lang scripts likewise (see `scripts/`). Cross-mod variants via `c:` tags + `neoforge:conditions` still work mechanically, but see Scope Discipline - cross-mod content is on hold.

### Predation - the 2.0 centerpiece (`docs/predator_frogs.md`, #281)

- **Predator tier** - Prowler (overworld), Cinder (nether), Gulper (aquatic), Rift (end), bred from designated resource-species crosses; **Apex tier** - Wither/Dragon/Elder/Warden, bred from designated predator crosses, each keyed to its boss via `bossEntityId()`.
- **Prey eligibility is a datapack registry** (`PFRegistries.PREDATOR_PREY`, `data/<ns>/productivefrogs/predator_prey/<mob>.json`) - deliberately NOT the vanilla `frog_food` tag (tagging prey would make vanilla frogs hunt it); targeting goes through the PF `PFShootTongue` behavior + `ResourceFrogAttackablesSensor`.
- **The eat kill is a fake-player kill**: a server FakePlayer wielding a looting-N sword (N = the frog's Bounty tier) deals lethal damage, so player-gated drops, looting, and XP orbs all flow from the vanilla death pipeline - zero loot-table emulation. Loot drops on the ground; XP as orbs in the open.
- **Supply chain**: `EntityNetItem` is the shared capture base (whole-entity `saveWithoutId` round-trip - the #210 lesson) with Frog Net (frogs only) and **Ender Net** (any mob) subclasses; the **Slurry Press** appliance turns filled net + empty bucket into a **Mob Slurry** bucket (one fluid + `SLURRIED_ENTITY` component; boss mobs rejected via `c:bosses` at insert AND tick); the waterloggable **Mob Slurry Basin** / **Slime Milk Basin** (shared `AbstractBasinBlockEntity`) spawn from the `MilkSpawnEconomy`. Slurry-spawned mobs get the `TELEPORT_DISABLED` attachment (`PFAttachments`) so Basin-farmed endermen/shulkers can't escape.
- **Liquid Experience** - the `c:experience` fluid at the NeoForge-standard **20 mB = 1 XP point** (`LiquidExperienceFluid`, pinned by unit test). Deliberately the simplest fluid shape in the mod: no components, no block form, stock `BucketResourceHandler`; drinking the bucket absorbs exactly 50 points.
- Master switches: `predators.enabled` and `boss.enabled` (`bossVariantsEnabled` was folded into the latter). The `predation_milestone` custom criterion (`PFCriterionTriggers`) drives the predation advancements alongside `frog_produced`.

### Boss altars - four multiblocks on one base (`content/multiblock/` + `BossAltarHatchBlockEntity`)

Multiblocks are **validator-driven, not a single oversized BE**: a validator scans the world layout anchored on one key block. The **Terrarium** (`TerrariumValidator` / `TerrariumManager` / `MilkCharge`, `docs/terrarium.md`) is the habitat multiblock; the **four boss altars** (Wither #247, Dragon #249, Warden #279, Elder Guardian #280 - `docs/wither_altar.md` etc.) share facing-aware validators built on **`AltarGeometry`**, wall-mounted Hatch blocks, and the extracted **`BossAltarHatchBlockEntity`** base: the summon state machine runs in the hatch BE's `serverTick`, pays **raw boss drops** (not Froglights - the settled 2.0 ruling) plus **Liquid Experience** into the hatch's 27-slot chest / tank, with data-driven supplemental `altarLootTable(name)` JSONs. **`AltarApexDock`** (owned by each hatch BE by composition) holds the installed Apex Frog: shift-right-click with a net holding the MATCHING Apex arms the altar, the oversized display frog (Dragonsbane/Witherbane/Wardenbane/Elderbane) renders only while armed, and breaking the hatch respawns the real frog with stats intact. When `predators.enabled` is off the Apex requirement is waived. Convention: **lock each canonical layout with a GameTest** - the altar layouts ship as `data/<ns>/structure/<name>.nbt` and `BossAltarTests` asserts the validators pass on them, so a layout/offset drift fails CI.

### Slime Milk - ONE component-carrying fluid (R-1; reverses v1.8)

The v1.8 per-variant-fluid design is **gone**. There is a single `slime_milk` fluid (+ the EE lane's `mimic_slime_milk`); the variant and catalyst buffs ride the **`SLIME_VARIANT` data component** on the `FluidResource`/bucket, which the 26.1 transfer API preserves through any tank/pipe (`FluidResource` = fluid + components, network-synced). `PFFluidTypes.ComponentCarryingFluidType` covers the one stock leg that dropped components - `FluidType.getBucket` minting a bare bucket - by copying the whole component patch onto minted buckets. **Milk does not spread as a world fluid in 2.0**; it exists as a resource (buckets/tanks/pipes) and the placed `SlimeMilkSourceBlock` + BE (which carries the variant and spawns slimes). Remaining documented gap: a **fluid-placer machine placing the block form loses the variant** (placement happens below where components live) - `docs/known_issues.md`. Render color is resolved per-instance from the BE / component at render time; one greyscale texture set serves every variant. Mob Slurry and Mimic Milk follow the same single-fluid + component model. **Molten metals** (`PFMoltenFluids`, Crucible -> Casting Mold lane) are minted at mod-init as FluidType + source/flowing only (no block, no bucket). **PF mints all molten itself** - the 1.21.1 defer-to-AllTheOres rules are gone (ATO 4.x dropped its whole fluid system on 26.1, so the defer would break the lane in any pack with ATO installed); iron/copper/gold mint unconditionally, the wave-2 metals mint when their provider mod loads. Casting Mold recipes take the **`c:molten_<metal>` tags** PF ships, not concrete fluids.

### Transfer API discipline (R-3) - transactional capabilities

The block capabilities are **`Capabilities.Item.BLOCK` / `Capabilities.Fluid.BLOCK` / `Capabilities.Energy.BLOCK`** over `ResourceHandler<ItemResource/FluidResource>` / `EnergyHandler` - the legacy `ItemHandler`/`FluidHandler`/`EnergyStorage` holders don't exist on 26.1. Registration lives in `PFModBusEvents.onRegisterCapabilities`; PF's adapters live in **`content/transfer/`** (`RestrictedItemResourceHandler`, `FluidTankResourceHandler`, `ReceiveOnlyEnergyHandler`). Three repo rules from the 2.0 review (PR #315):

1. **One CACHED handler per BE/state - never mint a handler per capability lookup** (per-lookup handlers break snapshot semantics and identity checks).
2. **World writes happen only in `SnapshotJournal.onRootCommit`** - a change callback that fires mid-transaction must guard with `Transaction.getCurrentOpenedTransaction() != null` and defer the side effect to commit. Mutations go through `insert`/`extract` inside `try (var tx = Transaction.openRoot()) { ...; tx.commit(); }`; fire `onContentsChanged -> setChanged` on commit, not mid-transaction.
3. **Component fluids need `ComponentCarryingFluidType`** (see the milk section) or the bucket leg silently strips identity.

Gotcha: **`ItemAccess.forStack` can never swap the underlying Item** - a bucket drain/fill through it silently moves 0 mB; use a player-slot / handler-slot access when the item must change. Side routing is unchanged in spirit: DOWN face -> output slice, other faces -> input slice.

### Appliance blocks (furnace-style GUI stations)

The hand-operated stations - **Slime Milker**, **Slime Churn**, **Spawnery**, **Casting Mold**, **Slurry Press** - share one shape; copy it for any new appliance: `content/block/<Name>Block` (LIT-style state, wires the BE ticker), `content/block/entity/<Name>BlockEntity` (`implements MenuProvider`; inventory + `ContainerData` + a **`static serverTick`** burn/cook loop), `content/block/entity/<Name>Inventory` (slot model with side-aware input/output views), `content/menu/<Name>Menu` + `client/screen/<Name>Screen`. The **Froglight Crucible** is the deliberate GUI-less exception (heat-from-below + a BER; `docs/froglight_crucible.md`); the EE **Alembic**/**Distiller** are the RF-powered variants (receive-only `ReceiveOnlyEnergyHandler` on `Capabilities.Energy.BLOCK`). Hopper/pipe I/O is a capability, not a hook - see Transfer API discipline above.

### Two parallel category surfaces: Frog Egg item vs Primed Frog Egg blocks

1. **`FrogEggItem` (one item)** - a bottled frogspawn carrying species via the `productivefrogs:contained_category` data component; absent = vanilla frogspawn.
2. **`PrimedFrogEggBlock` (six instances, one per species)** - placed blocks selected by registry id (`PFBlocks.primedEgg(category)`).

When adding a category-aware surface, pick one model up front - data-component-on-one-item (`FrogEggItem`, `ResourceTadpoleBucketItem`, `ConfigurableFroglightItem`) or one-instance-per-species (Primed Frog Egg blocks). Don't mix them on one surface.

### Event hooks vs custom recipes

Key interactions are **event-driven, not recipe-driven** (`PlayerInteractEvent.RightClickBlock`): `FrogspawnBottlingHandler` (glass bottle + frogspawn -> Frog Egg) and `EggPrimerHandler` (primer + frogspawn -> Primed Frog Egg block). Where vanilla already has the right UX (water-bottle, fish-bucket, slimeball love-mode), mirror it via an event hook rather than inventing a custom recipe type.

### Custom brain sensor - two-layer prey gating

"A frog eats only its own kind's prey" is enforced in two independent places - keep both: (1) **AI layer** - `ResourceFrogAttackablesSensor` (`PFSensors`) writes only kind-eligible targets into `NEAREST_ATTACKABLE` (same-category slimes for a Resource frog, Mimic Slimes for Midas, `predator_prey` mobs for a predator - mutual exclusion in both directions); (2) **Drop layer** - `FrogTongueDropHandler` (and `MidasTongueDropHandler`) re-check the pairing on `LivingDeathEvent` before emitting. The slime path emits a variant-stamped `ConfigurableFroglightItem`; a slime carrying a potion effect also stamps `stored_effect` (Brewed Froglights: placed aura + held self-buff; the Curios worn-charm form is out of 2.0 scope). **Brain gotcha:** use `addActivityWithConditions` and re-supply vanilla's requirement set - bare `addActivity` wipes the activity gating.

### The Equivalence (EE) lane (#253) - opt-in, default OFF (survived the port)

The RF-powered transmutation lane for off-roster items: Alembic -> Mimic Slime -> Milker -> Mimic Milk -> Midas frog (Kiss-primed) -> Prismatic Froglight -> Distiller -> original item (`docs/equivalence_lane.md`). Still walled off from the six-species machinery: `SYNTHESIZED_ITEM` is the lane's join key (mutually exclusive with `SLIME_VARIANT`); `MimicSlime` is a **sibling** of vanilla Slime, invisible to the species path by construction; Midas is `FrogKind.MIDAS` (folded in from the old flag); Mimic Milk is the R-1 single-fluid model. The whole lane gates on `PFConfig.equivalenceEnabled()` (fail-closed, default OFF); dev runs force it on via `-Dproductivefrogs.equivalence=true`, GameTests via the `PFConfig.equivalenceEnabledOverride` hook.

### Config-gated content - the `config_enabled` datapack condition

The Spawnery ships off by default: its recipe carries `{"type":"productivefrogs:config_enabled","config":"spawnery"}`. `ConfigEnabledCondition` (registered via `PFConditions`) reads `PFConfig` guarded by `SPEC.isLoaded()` and **fails closed**. Gateable flags are a closed enum (`ConfigEnabledCondition.Key`) so a JSON typo fails at decode time. The flag gates crafting/JEI/creative only - a placed block still works. The Spawnery's per-species `spawnery_primer/<species>` item tags (`PFItemTags`) are the pack-override surface (`docs/spawnery.md`).

### Registry / lifecycle layout

Wiring is centralized in `ProductiveFrogs.java`'s constructor; each `PF*` registry class exposes `register(IEventBus)`. Game-event listeners under `event/` self-register via `@EventBusSubscriber(modid = MOD_ID)`; client-only adds `value = Dist.CLIENT`. **Constructor ordering is load-bearing** (the comments in `ProductiveFrogs.java` call each out - don't reorder): `PFMoltenFluids.bootstrap()` before the fluid registers; `PFFluidTypes` before `PFFluids` before `PFBlocks` (the milk source block factory resolves the fluid at block-build time); `PFEffects` before `PFPotions`; `PFBlockEntities`/`PFMenuTypes` after `PFBlocks`. Six parent species map to categories via the `parent_species` datapack registry (`data/productivefrogs/productivefrogs/parent_species/`); vanilla `minecraft:slime`/`magma_cube` are NOT parent species, and `SlimeSplitDiscoveryHandler` resolves by EntityType-id lookup, no `instanceof` chain. **A PF EntityType subclassing a vanilla mob is NOT in its parent's entity-type tags** - PF ships its own `can_breathe_under_water` etc. entries (`PFEntityTags`); audit parent tags on every new entity.

### GameTest layer - registry-based (R-6; the annotation form is GONE)

In-world tests live under `gametest/`, **split across per-domain files** (`SpeciesCategoryTests`, `PredatorEatPathTests`, `BossAltarTests`, ...), run via `./gradlew runGameTestServer` (required CI job). A test is two halves: its body (a `Consumer<GameTestHelper>` in a `DeferredRegister` on **`Registries.TEST_FUNCTION`**) and its metadata (a `TestData` carried by a `FunctionGameTestInstance` registered through `RegisterGameTestsEvent`, plus a shared `TestEnvironmentDefinition`). `PFGameTests.test(name, [structure, rotation,] maxTicks, body)` hides the two-step - each domain class is bodies + one `register()` line in `PFGameTests.register`. **Do not reach for `@GameTestHolder`/`@GameTest` - that 1.21.1 form no longer exists.** Keep `required = true` / `manualOnly = false` or the CI job silently skips the test. Structure NBT stays singular (`data/<ns>/structure/<name>.nbt`; shared plot `empty_5x5x5`), as do all datapack dirs (`recipe/`, `loot_table/`, `tags/item/`). GameTest is **blind to client visuals** (tints, models, lang) - those need a manual `runClient` pass (`docs/testing.md`).

### Item/block tinting on 26.1 - data-driven tint sources

The legacy `RegisterColorHandlersEvent.Item`/`.Block` ItemColor/BlockColor lambda form is **gone**. Item tints are JSON-adjacent: `PFClientEvents.onRegisterItemColors` registers **`ItemTintSource` codec types** via `RegisterColorHandlersEvent.ItemTintSources` (the sources live in `client/color/` - `VariantColorTint`, `ContainedCategoryTint`, `SlimeBucketTint`, ...), and each tinted item's **model JSON `tints` array** references a type by id to actually apply it - registering the codec alone tints nothing. Block tints go through `RegisterColorHandlersEvent.BlockTintSources` (still bound per `Block`, list index = tint index). Fluid render (milk/molten) registers per-fluid `FluidModel`s (`RegisterFluidModelsEvent`) with per-instance color resolved from the BE/component. Client caches drop on resource reload via `AddClientReloadListenersEvent`.

### Container screens on 26.1

GUIs draw through the two-phase `GuiGraphicsExtractor` pipeline: screens contribute their background in `extractBackground` and the base `AbstractContainerScreen` renders slots, labels, and **item tooltips itself - the 1.21.1 `renderTooltip` workaround is gone**. `client/screen/PFContainerScreen` remains the shared base (slot-frame drawing helper + the ctor plumbing: `imageWidth`/`imageHeight` are `final` on 26.1, so non-default panel sizes pass through the 5-arg constructor). Extend it for new container screens. Non-slot widgets (the Casting Mold's fluid gauge) still need their own hover hit-test.

### Integrations - JEI + Jade only (cross-mod ON HOLD)

- **JEI** (`client/jei/ProductiveFrogsJeiPlugin`, `@JeiPlugin`; 29.6.x line) - per-component subtype interpreters (Slime Bucket / Milk Bucket / Frog Egg / Froglight / Mob Slurry) + recipe categories + info pages. `compileOnly` API + `runtimeOnly` jar in dev.
- **Jade** (`client/jade/ProductiveFrogsJadePlugin`, `@WailaPlugin`) - **`compileOnly` only**; Jade is a manual `run/mods` drop-in (a `runtimeOnly` dep would double-load and trip the duplicate-modid check). Two gotchas: every plugin UID needs a `config.jade.plugin_<modid>.<uid>` lang key in `en_us.json` or the client resource reload fails, and on Jade 26.1+ a server-data provider must **not** also implement `IComponentProvider` - split via single-interface delegate classes (see the plugin).
- **Cross-mod content and Curios are ON HOLD by maintainer ruling (2026-07-05)** until closer to release: the shipped tree has no partner-mod variants and no `integration/curios` (the `setup/VariantIntegrations` mechanism itself remains in-tree, deriving providers from bundled JSON conditions - a no-op with none bundled). Wave-1 parity work is parked on `feat/crossmod-wave1` and Curios on `feat/curios-26.1` - **do not merge or extend those without maintainer say-so.** The `c:` tag + `neoforge:conditions -> mod_loaded` JSON mechanism itself still works and remains the rule when content compat returns (no `compat/` Java package, no hard mod deps).
- **In-game guide: none shipped in 2.0 yet.** Patchouli's 1.x book was not ported; the 2.0 guide is a content rewrite on **Modonomicon** (`26.1.2-neoforge:2.1.0`, soft-dep, book id `productivefrogs:guide`). Engine, scope, chapter map, and voice are specced in `docs/guidebook.md`; skeleton proof-of-fit is the first build step (#318).

### Observability - PFDebug

Opt-in debug logger spanning all layers: `lifecycle, registry, config, infusion, split, tongue, egg, sensor, milker, milk_source, churn, alembic, distiller, render, tint, spawnery`. Enable with `-Dproductivefrogs.debug=<areas>` or `/pf debug <area> on`; greppable `[PF/<area>]` prefix in `latest.log`. Use it instead of ad-hoc logging - client-render bugs in particular are invisible to GameTest.

## Project Conventions

- **Java 25, 4-space indent, no tabs, no wildcard imports.** Import order: alphabetical, one block, no semantic groups. Records for value types; `@Nullable` (JetBrains) on ambiguous returns. Sealed hierarchies + exhaustive `switch` for closed identity sets (the `FrogKind` precedent - prefer the unifying refactor over cloning an ad-hoc flag).
- **`Identifier`, not `ResourceLocation`.** 26.1 renamed the class (`net.minecraft.resources.Identifier`; construct via `Identifier.fromNamespaceAndPath(...)`); `ResourceKey#location()` became `identifier()`. The tree uses `Identifier` consistently - don't reintroduce the old name.
- **BE/entity save-load is `ValueInput`/`ValueOutput`** (typed/codec I/O), not raw `CompoundTag` methods. Any path serializing a captured entity carries the **whole entity** (`saveWithoutId` - stats included, the #210 lesson).
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `ci:`, `perf:`). One logical change per commit; body explains the why. `main` is protected; changes go via PR (squash-merged); protection requires the `build` and `gameTest` jobs and resolved review conversations.
- **Recipes never hardcode a wood species** - use `#minecraft:planks` (maintainer ruling 2026-06-07); same instinct for other material families with a vanilla tag.
- **Docs filenames are snake_case.** Design changes update the relevant `docs/*.md` in the same PR.
- **Line endings:** `.gitattributes` forces LF for `.java`/`.gradle`/`.json`/`.md`/`.yml`, CRLF for `.bat`/`.cmd`. Don't fight it.
- **No hard mod dependencies.** API soft-deps that genuinely need Java (JEI, Jade) live under `client/` as `compileOnly` and load only when present; content compat is JSON conditions (currently on hold - see Integrations).

## Scope Discipline (the two lines)

- **`main` = the 2.x / 26.1 active line.** All feature work lands here. 2.0.0's release gate is the predation system (#281 - delivered; release packaging in progress), not partner mods.
- **`mc-1.21.1` = the frozen 1.x line** (versions `1.24.x`, hotfix-only, `docs/maintenance_1_21_1.md` there). **Never merge across the lines.** A bug present in both gets fixed on `mc-1.21.1` and **cherry-picked forward** to `main` (fixes flow old -> new only). One CurseForge project (1552728), two game versions; per-branch `gradle.properties`/`CHANGELOG.md`/CI.
- **Cross-mod integrations and the guidebook are held until closer to release** (maintainer ruling 2026-07-05). They return as additive 2.x minors; do not resurrect the parked branches or add partner-mod content without an explicit go-ahead. 26.1 is a fresh save target - no cross-version world migration is promised.
