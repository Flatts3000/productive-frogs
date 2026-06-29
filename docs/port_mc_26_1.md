# Forward Port to Minecraft 26.1 / NeoForge 26.1

> **STATUS: PLANNED (not started).** This document scopes the forward port of Productive Frogs from MC 1.21.1 / NeoForge 21.1.x to **MC 26.1 / NeoForge 26.1.x**, and defines the GitHub branch + release model that lets the 1.21.1 line live on as a maintenance branch. Authored 2026-06-29. The companion historical doc is [`port_mc_1_21_1.md`](./port_mc_1_21_1.md) (the original 1.21.11 -> 1.21.1 backport), which this port partially **reverses**.

## Why this is happening

PF is feature-complete on the 1.21.1 line (v1.24.2, stable on CurseForge). Two decisions drive the move (2026-06-29):

1. **Decouple from Sky Frogs.** PF was on 1.21.1 *only* to anchor the Sky Frogs modpack, which was itself pinned there by Ex Deorum + Skyblock Builder having no 1.21.4+ builds. PF now becomes a **standalone** CurseForge content mod on the modern line. Sky Frogs stays on 1.21.1, pinned to the last-compatible PF jar (the `1.24.x` line) - no further PF action needed for the pack.
2. **Target where the audience is heading.** The mod follows **All The Mods 11 (ATM11)**, the NeoForge kitchen-sink bellwether where every tech mod PF integrates with lives. ATM10 was 1.21.1; **ATM11 is MC 26.1.2 / NeoForge 26.1**.

### Ecosystem reality (researched 2026-06-29)

- Minecraft dropped the `1.21.x` scheme in Dec 2025 for **year-based numbering**. There is **no 1.22**. The line reads `1.21.11 -> 26.1 (Mar 2026) -> 26.2 "Chaos Cubed" (Jun 2026)`.
- **NeoForge signals 26.1 is the next stable modding baseline** (their 26.1 release notes), the explicit successor to 1.21.1.
- As of late June 2026, **ATM11 is a "super-early alpha"** and the heavy tech mods (Mekanism, AE2, Create) are still mid-port - Mekanism was still shipping 1.21.1 builds in April 2026. Library soft-deps (JEI, Jade) port fast and lead; the big tech mods are the long pole.
- **PF's integrations are all optional / JSON `mod_loaded`-gated,** so PF compiles and runs against bare NeoForge 26.1 today. Partner variants stay dormant and light up automatically as those mods arrive. Being early costs *integration test coverage*, not *shippability*.

**Strategic timing:** start the port now alongside ATM11's alpha; ship `2.0.0` as PF's primary release when ATM11 exits alpha and the partner ecosystem lands on 26.1. Go **straight to 26.1** - never stop at an intermediate 1.21.x (those are an ecosystem dead zone the packs skipped, and they cost the same API rewrites).

## Target versions

| Setting | Current (1.21.1) | Target (26.1) | Notes |
|---|---|---|---|
| Minecraft | 1.21.1 | **26.1** (match ATM11) | Verify against ATM11's exact pin (26.1.2 as of research). |
| NeoForge | 21.1.230 | **26.1.x** | Pin the latest stable 26.1 build in Phase 0. |
| Java | 21 | **21** (verify) | No known Java bump for 26.x; confirm in Phase 0. |
| moddev plugin | 2.0.141 | **TBD** | Verify `net.neoforged.moddev` supports 26.1; bump if needed. |
| `minecraft_version_range` | `[1.21.1,1.21.5)` | `[26.1,26.2)` (or per ATM11) | |
| Distribution | CurseForge project 1552728 | **same project** | Multi-game-version on one project (see Release model). |

## GitHub branch + release model

The standard **moving-trunk + maintenance-branch** pattern (how Mekanism et al. run parallel MC lines).

| Branch | Role | MC / NeoForge | Version line |
|---|---|---|---|
| `main` | Active development; moves forward | 26.1 / 26.1.x (after the port merges) | **`2.x`** |
| `mc-1.21.1` | Frozen 1.21.1, **hotfix-only** | 1.21.1 / 21.1.x | **`1.24.x`** |
| `port/mc-26.1` | Where the port happens; merges to `main` when green | 26.1 | becomes `2.0.0` |

**Versioning:** the 26.1 line starts at **`2.0.0`** - a SemVer major bump that signals the platform break. Mental model: `1.x` = legacy 1.21.1 (hotfix-only), `2.x` = 26.1+ (active). The two never interleave.

**One CurseForge project (1552728), two game versions.** CF lists multiple MC versions per project; players filter to theirs. Keeps downloads, comments, and follows consolidated. `build.gradle` currently **hardcodes** `addGameVersion('1.21.1')` - the port branch changes that single line to `'26.1'`. Nothing else in the publish path needs to know about the split.

**No shared mutable state = no collisions.** `mod_version` (gradle.properties), `CHANGELOG.md`, the game-version line, and `ci.yml` all live *per branch*. A `1.24.3` hotfix and a `2.0.0` release are independent branch states.

**MC-version in the artifact name** (Mekanism convention): `productivefrogs-1.21.1-1.24.3.jar` vs `productivefrogs-26.1-2.0.0.jar`. Currently jars are `productivefrogs-<version>.jar` with no MC tag - fix the `archivesName` as part of this so the two lines are unmistakable.

**CI: no matrix.** Each branch's `ci.yml` builds its own target (maintenance = NeoForge 21.1, `main` = 26.1, both Java 21). Apply the same `build` + `gameTest` + resolved-conversations protection to **both** release branches.

**Hotfix flow:** branch off `mc-1.21.1` -> fix -> PR into `mc-1.21.1` -> bump `1.24.x` -> tag -> `./gradlew publishCurseForge` (uploads under the 1.21.1 game tag). If the same bug exists in 26.1, **cherry-pick the fix forward to `main` as a separate PR** - fixes flow old -> new, never the reverse.

### Cut-over sequencing

1. Cut `mc-1.21.1` off current `main` HEAD (`0daf00f`, the v1.24.2 release commit - note the `v1.24.2` *tag* sits one commit behind at `0d39998`, so freeze on HEAD not the tag). Push, apply branch protection. 1.21.1 now has a permanent home.
2. Cut `port/mc-26.1` off `main`. The phases below drive it.
3. Merge `port/mc-26.1` -> `main` when fully green. `main` is now 26.1; the lines have diverged on purpose.

## The forward-port asset

PF was **originally built on 1.21.11 and backported down** to 1.21.1 (project scaffolded on 1.21.11 at commit `f225afd`; backport landed via PR #80 + #81). The modern API forms this port needs are forms this codebase **used to have**. Two reference sources:

- **`port_mc_1_21_1.md` read in reverse** - its delta table is the canonical 1.21.1 <-> modern mapping for each API category.
- **Pre-backport git history** - the actual modern-form code for the early (v1.0-era) features.

**Caveat:** the git-history asset only covers features that existed *before* the backport (roughly v1.0). Everything from v1.1 through v1.24 (the full variant roster, all four appliances, the Crucible/Mold melt lane, the Churn, the Terrarium multiblock, the boss tier + altars, the Equivalence lane) was built **on the 1.21.1 codebase in 1.21.1-form** and never existed in modern form. So the asset gives the *pattern per API category*; we apply that pattern forward across a codebase several times larger than the one that was backported. The surface counts below quantify that growth.

> **Important:** the API forms below are anchored to where each change landed in the 1.21.x line (1.21.4 / 1.21.5 / 1.21.6, per the NeoForge per-version primers). The deltas from 1.21.6 through 26.1 are **not yet fully enumerated** - Phase 0 must read every NeoForge primer from `docs.neoforged.net/primer/docs/1.21.2` up through the 26.1 primer and fold any further changes into the affected phase. Do not treat the phase list as exhaustive until that primer sweep is done.

## Phase breakdown

Sized against the **actual current codebase** (measured 2026-06-29), not the much smaller codebase the original port touched.

### Phase 0 - Branch, gradle pins, primer sweep (1 PR)

- Cut `mc-1.21.1` and `port/mc-26.1` per the sequencing above.
- `gradle.properties`: `minecraft_version`, `neoforge_version`, version ranges to 26.1. Bump `mod_version` toward `2.0.0` (use a `2.0.0-alpha` style during the port).
- Verify `net.neoforged.moddev` plugin supports 26.1; bump if needed. Confirm Java target.
- `build.gradle`: `addGameVersion('1.21.1')` -> `'26.1'`; add MC version to `archivesName`/jar name.
- **Read every NeoForge primer 1.21.2 -> 26.1** and append any deltas not already covered below to the relevant phase. The build will fail spectacularly from here; Phases 1+ fix it.

### Phase 1 - ResourceLocation / identifier check (small)

1.21.1 already uses `ResourceLocation` (the backport did the `Identifier` -> `ResourceLocation` sweep). Verify the modern line didn't reintroduce an `Identifier` alias that's now preferred; likely a near no-op, but confirm the static factory shapes (`ResourceLocation.fromNamespaceAndPath`) still hold.

### Phase 2 - BlockEntity save/load -> ValueInput/ValueOutput (large)

**35 BlockEntity classes, ~20 with save/load sites** (vs 5 in the original backport). Each `saveAdditional(CompoundTag)` / `loadAdditional(CompoundTag)` moves to `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` (the 1.21.5 form). Covers the appliances (Milker, Churn, Spawnery, Casting Mold), the Crucible, the Terrarium blocks (Controller, Sprinkler, Incubator, Hatch), the altar hatches, the per-variant milk sources, the EE machines (Alembic, Distiller, Mimic source), and the Froglight/egg BEs. Lift the `ValueInput`/`ValueOutput` pattern from pre-backport history; apply to the full set.

### Phase 3 - Capability + transfer API -> new resource-handler form (large)

**48 capability-registration sites.** NeoForge 21.4+ replaced the legacy `IItemHandler`/`ItemStackHandler` transfer API with `ResourceHandler<ItemResource>`, and the capability ids changed (`Capabilities.ItemHandler.BLOCK` -> `Capabilities.Item.BLOCK`; `Capabilities.EnergyStorage.BLOCK` and fluid equivalents similarly). This touches every appliance's hopper I/O, the EE machines' RF `EnergyStorage`, and the per-variant milk fluid handlers. This is the single largest behavioural-surface phase - budget accordingly and re-verify side-aware input/output routing per machine.

### Phase 4 - Item tint -> JSON ItemTintSource / Client Items (large)

**18 tint/color registration sites.** 1.21.4 moved per-item tinting from Java `RegisterColorHandlersEvent.Item` + `ItemColor` to the JSON `ItemTintSource` "Client Items" pipeline (`assets/<ns>/items/*.json` with a `tints` array). PF *had* this pre-backport (the `ContainedCategoryTint` / `BucketedCategoryTint` / `SlimeVariantTint` classes) - re-introduce it, but across the far larger current set: every variant Configurable Froglight, Slime/Milk buckets, tadpole bucket, Frog Egg bottle, all spawn eggs, plus the EE-lane `SynthesizedTint`/Mimic items. Re-create the `assets/productivefrogs/items/*.json` files (none exist today) and the `ItemTintSource` classes. Confirm per-variant **fluid** tint (`IClientFluidTypeExtensions.getTintColor`) still works as-is or moves with it.

### Phase 5 - GameTest registration -> registry-driven TestData (large)

**158 `@GameTest` methods.** 1.21.5 replaced annotation-based `@GameTestHolder` / `@GameTest` with the registry-driven `TestData` / `GameTestInstance` form. The bodies stay ~95% the same; the registration shape changes for all 158. Lift the modern pattern from pre-backport history / vanilla, and confirm `./gradlew runGameTestServer` discovers and runs the full set. This is the highest method-count phase.

### Phase 6 - GUI rendering -> RenderPipelines (medium)

**10 screen classes.** 1.21.4+ moved GUI rendering to the pipeline-based `RenderPipelines.GUI_TEXTURED` form (from the older `RenderType` / `GuiGraphics.blit` overloads). Update `PFContainerScreen` (the shared base) and the 9 concrete screens (Milker, Spawnery, Churn, Casting Mold, Alembic, Distiller, Terrarium Controller, etc.). Re-verify the 1.21.1 `renderTooltip` gotcha against 26.1 behaviour - the base-class workaround may no longer be needed, or may need a different shape.

### Phase 7 - Asset / data path conventions (medium)

Current datapack dirs are **singular** (1.21.1 form): `recipe/`, `loot_table/`, `tags/item/`, `tags/entity_type/`, `structure/`, `advancement/`. Modern MC uses **plural** for several (`recipes/`, `loot_tables/`, `tags/items/`, etc.). Reverse the backport's path renames per the modern convention (verify exact set against the primers). Bump `pack.mcmeta` `pack_format` to the 26.1 value. The `assets/.../items/*.json` set returns here (tied to Phase 4). Also check `data_maps/`, `neoforge/biome_modifier/`, `curios/`, `patchouli_books/` for schema/path shifts.

### Phase 8 - Datapack registries + codecs (small/medium)

`PFDataPackRegistryEvents` (`SlimeVariant`, `ParentSpecies` registries) - verify `DataPackRegistryEvent.NewRegistry` and the codec/`StreamCodec` APIs against 26.1. The codecs themselves are largely version-stable; the registration-event shape may differ.

### Phase 9 - Fluids + biome modifiers (medium)

**16 fluid/FluidType classes** (per-variant Slime Milk + Mimic Milk). Verify `BaseFlowingFluid.Properties`, `FluidType`, and the per-variant minting in `PFVariantMilk.bootstrap` against 26.1's fluid API. Verify the 4 parent-species `biome_modifier` JSONs (`add_spawns` schema) and the `data_maps` (Crucible heat ladder) against 26.1.

### Phase 10 - Soft-dep integrations + version bumps (medium, ecosystem-gated)

Bump and re-wire the library soft-deps to their 26.1 builds: **JEI, Jade, Patchouli, Curios.** Two real risks here:

- **Curios may be Accessories** on the modern line (Curios' successor) - if so, the `productivefrogs:brewed` slot validator and the Curios `compileOnly` glue need migrating to the Accessories API. Confirm which exists on 26.1.
- **Partner tech mods (Mekanism, AE2, Create, Powah, etc.) may not have 26.1 builds yet.** Their cross-mod variant JSONs are `mod_loaded`-gated and harmless when absent, so PF builds and ships without them - but the integrations can't be *tested* until those mods land. Track availability; this gates the `2.0.0` release timing, not the build.

### Phase 11 - Compile sweep + manual playtest + gametest green (large)

Whatever the primer sweep and earlier phases didn't catch: `Component` overloads, `BlockBehaviour.Properties` / `EntityType.Builder` / `BlockEntityType.Builder` shapes, sensor/brain API (`ResourceFrogAttackablesSensor`), vanilla `Frog`/`Tadpole`/`Slime` superclass deltas, menu registration. Then the full manual sweep (GameTest is blind to client visuals): creative-tab pass (every item, every tint, every display name), the full milk pipeline per variant, tongue-kill -> variant Froglight -> smelt, the Crucible/Mold lane, the Terrarium loop, the boss altars, the EE lane (dev sysprop on). All 158 GameTests green.

### Phase 12 - Merge, docs, release (1 PR + release)

- Squash-merge `port/mc-26.1` -> `main`. `main` is now 26.1.
- Update `CLAUDE.md`, `ROADMAP.md`, `versioning.md` to reflect the 26.1 target + the 1.x/2.x line split. Mark this doc DONE.
- Confirm `mc-1.21.1` branch protection is live.
- Cut `2.0.0` when ATM11 / the partner ecosystem is ready (see timing above).

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| 1.21.6 -> 26.1 deltas not yet enumerated | High | Phase 0 primer sweep is mandatory before trusting the phase list. |
| Codebase is far larger than the original port (158 tests, 48 cap sites, 35 BEs) | High | Phases 2/3/4/5 are each multi-day; do not batch. The original 3-4 week estimate does **not** apply - this is bigger. |
| Curios -> Accessories migration on modern line | Medium | Confirm in Phase 10; isolate to the `integration/curios` glue + the brewed slot. |
| Partner mods (Mekanism/AE2/Create) not on 26.1 yet | Medium | JSON-gating means PF still builds/ships; integrations untestable until they land - gates release timing only. |
| Patchouli has no 26.1 build (guide content) | Medium | Guide is `compileOnly` + inert when absent (same as today); ship without it if needed, add when it lands. |
| moddev plugin 2.0.141 doesn't support 26.1 | Low | Verify Phase 0; bump plugin. |
| New-resource-handler transfer API semantics differ subtly from legacy | Medium | Phase 3 re-verifies side-aware I/O per machine via runClient + GameTest. |

## Success criteria

- [ ] `mc-1.21.1` cut, pushed, protected; 1.21.1 hotfixes can ship independently.
- [ ] `./gradlew build` green on `port/mc-26.1` (NeoForge 26.1).
- [ ] `./gradlew runGameTestServer` green - all 158 GameTests pass.
- [ ] Manual creative-tab sweep: every item with correct tint + display name.
- [ ] Full milk pipeline, Crucible/Mold lane, Terrarium loop, boss altars, and EE lane verified by hand.
- [ ] Jar names carry the MC version; CF upload tags `26.1` on project 1552728.
- [ ] `port/mc-26.1` merged to `main`; load-bearing docs updated.
- [ ] `2.0.0` released when ATM11 / partner ecosystem is ready.

## Backwards compatibility

- **1.21.1 worlds** stay on the `mc-1.21.1` line + 1.24.x jars (Sky Frogs and any standalone 1.21.1 player). Untouched by this port.
- **26.1 is a fresh save target** - 2.0.0 is a new major on a new MC version; no cross-version world migration is promised or expected.
- **Cross-mod compat datapacks** are version-neutral JSON; only the codebase that consumes them moves.
