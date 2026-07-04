# Forward Port to Minecraft 26.1 / NeoForge 26.1

> **STATUS: PLANNED (not started).** Forward port of Productive Frogs from MC 1.21.1 / NeoForge 21.1.x to **MC 26.1 / NeoForge 26.1.2.76** (ATM11's target), shipping as **PF 2.0.0**. This plan is grounded in the four-track research sweep recorded in [`port_mc_26_1_research.md`](./port_mc_26_1_research.md) - read that first; it is the authoritative API/ecosystem reference and this plan derives from it. Companion history: [`port_mc_1_21_1.md`](./port_mc_1_21_1.md) (the original 1.21.11 -> 1.21.1 backport, which this port partially reverses). Authored 2026-06-29, revised post-research.

## Why this is happening

PF is feature-complete on 1.21.1 (v1.24.2, stable on CurseForge). Two decisions drive the move:

1. **Decouple from Sky Frogs.** PF was on 1.21.1 only to anchor that modpack. PF now becomes a **standalone** mod on the modern line; Sky Frogs stays on 1.21.1 pinned to the `1.24.x` jar.
2. **Follow ATM11.** The mod targets where its audience is heading - **ATM11 = MC 26.1.2 / NeoForge 26.1**. NeoForge signals 26.1 is the next stable modding baseline. (As of mid-2026 ATM11 is alpha and the heavy tech mods are still mid-port; PF ships when the build is green, independent of partner timing - see scope below.)

## 2.0 scope - standalone, JEI + Jade only

**PF 2.0 ships with no cross-mod integrations.** This sidesteps the research's biggest finding (Mekanism, Create, Industrial Foregoing, and Flux Networks aren't ported to 26.1, so their content is untestable) and sharply cuts port surface.

**In 2.0:** the six species, the **vanilla** resource roster, all four appliances (Milker / Churn / Spawnery / Casting Mold), the Froglight Crucible + the melt-and-cast lane, the Terrarium multiblock, the boss/endgame tier + altars, the Equivalence (EE) lane, frog stat breeding, Frog Legs / fairy-tale content, the advancements tab. **Plus JEI** (recipe + info pages) and **Jade** (look-at tooltips) - both have 26.1 builds (JEI beta, Jade stable).

**Dropped from 2.0 (return as additive 2.x minors as the ecosystem ports, mirroring the original v1.2-v1.15 layering):**
- **All cross-mod content** - every partner-mod `slime_variant` + recipe (Mekanism, Create, AE2, AllTheOres, Mystical Agriculture, Powah, Industrial Foregoing, Refined Storage, Flux Networks, Just Dire Things), the 33 cross-mod crush recipes, the `c:`-tag primer variants, the `disabledIntegrations` config surface, and the `VariantIntegrations` boot hook.
- **Curios** - the brewed-Froglight worn-charm slot + `integration/curios` glue + `curios/` slot JSON. Brewed Froglights keep their **placed-aura** and **held self-buff** forms; only the worn charm is gone until Curios is re-added.
- **Patchouli** - the in-game guide book (`patchouli_books/` + assets). Returns when Patchouli ports to 26.1.

**Note:** Flux Networks looks abandoned on the modern line (no update since Jan 2025) - a candidate to retire permanently rather than re-add.

## Target versions

| Setting | Current (1.21.1) | Target (26.1) | Source / note |
|---|---|---|---|
| Minecraft | 1.21.1 | **26.1.2** | Matches ATM11. Pin exact: `minecraft_version_range=[26.1.2]`. |
| NeoForge | 21.1.230 | **26.1.2.76** | Latest stable; 4-part scheme. Dep range `[26.1.2.76,)`. |
| **Java** | 21 | **25** | The one environment prerequisite - install a JDK 25. |
| moddev plugin | 2.0.141 | **2.0.141** | No bump; the official 26.1.2 MDK ships exactly this. |
| Gradle | 9.5.1 | (min 9.1.0) | Already satisfied. |
| `pack.mcmeta` | `pack_format` int | **`min_format`/`max_format`** | resource `84`, data `101` (diverged; reconfirm minors against the 26.1.2 client). |
| CurseForgeGradle | 1.1.26 | 1.2.30 (advised) | Defensive bump for Gradle 9. |
| `loaderVersion` | `[4,)` | likely **dropped** | 26.1.2 MDK omits it; verify against the template. |

## GitHub branch + release model

Standard moving-trunk + maintenance-branch pattern.

| Branch | Role | MC / NeoForge | Version |
|---|---|---|---|
| `main` | Active dev; moves forward | 26.1 / 26.1.x (after merge) | **`2.x`** (starts `2.0.0`) |
| `mc-1.21.1` | Frozen 1.21.1, **hotfix-only** | 1.21.1 / 21.1.x | **`1.24.x`** |
| `port/mc-26.1` | Where the port runs; merges to `main` when green | 26.1 | becomes `2.0.0` |

- **`2.0.0` is a hard major bump** signalling the platform break. `1.x` = legacy 1.21.1 (hotfix-only), `2.x` = 26.1+ (active).
- **One CurseForge project (1552728), two game versions.** `build.gradle` hardcodes `addGameVersion('1.21.1')` - the port branch changes that one line to `'26.1.2'`. **MC-version in the jar name** (`productivefrogs-1.21.1-1.24.3.jar` / `productivefrogs-26.1-2.0.0.jar`) via `archivesName`.
- **No shared mutable state** (gradle.properties / CHANGELOG / ci.yml are per-branch) so the two lines never collide.
- **CI: no matrix** - each branch builds its own target (both Java 21? no: maintenance Java 21, `main` Java 25). Same `build` + `gameTest` + resolved-conversations protection on both.
- **Hotfix flow:** branch off `mc-1.21.1` -> fix -> bump `1.24.x` -> tag -> publish. If the bug exists in 26.1, **cherry-pick forward to `main`** (fixes flow old -> new only).

### Cut-over sequencing

1. Cut `mc-1.21.1` off current `main` HEAD (`0daf00f`, the v1.24.2 release commit; the `v1.24.2` *tag* sits one commit behind at `0d39998`, so freeze on HEAD). Push, protect.
2. Cut `port/mc-26.1` off `main`. The phases below drive it.
3. Merge `port/mc-26.1` -> `main` when green. `main` is now 26.1; the lines have diverged on purpose.

## Phase breakdown

Sized against the real codebase (measured 2026-06-29: 35 BlockEntities / ~20 save-load sites, 48 capability sites, 158 GameTest methods, 16 fluid classes, 10 screens, 18 tint sites) **minus the dropped cross-mod surface**. The original backport's 3-4 week estimate does **not** apply.

### Phase 0 - Branch, gradle pins, scope strip (1 PR)

- Cut the branches per the sequencing above. Install JDK 25.
- `gradle.properties` + `build.gradle`: NeoForge `26.1.2.76`, MC `26.1.2`, Java 25, `addGameVersion('26.1.2')`, MC-version in `archivesName`, CurseForgeGradle `1.2.30`. `pack.mcmeta` -> `min_format`/`max_format` (84 / 101).
- **Strip the dropped scope** (cross-mod content, Curios, Patchouli) so the port doesn't carry surface that isn't shipping: delete the partner `slime_variant`/recipe/crush JSONs, the `c:`-tag variants, `VariantIntegrations`, the `integration/curios` package + `curios/` data, the `patchouli_books/` data + guide assets, and the `disabledIntegrations` config. Keep the JEI + Jade `client/` plugins.
- The build will fail spectacularly from here; the phases fix it.

### Phase 1 - Deobf + `Identifier` + package-reorg sweep (large, do FIRST)

The single mechanical pass that lets the tree compile against 26.1 names before any semantic rework:
- `ResourceLocation` -> **`Identifier`** everywhere; `fromNamespaceAndPath(...)` -> `Identifier.of(...)`; `ResourceKey#location()` -> `identifier()`.
- Vanilla import moves: `Frog`/`Tadpole` -> `net.minecraft.world.entity.animal.frog.*`; `Slime`/`MagmaCube` -> `...monster.slime.*`; models likewise (`LavaSlimeModel` -> `MagmaCubeModel`). The `ResourceFrog`/`ResourceTadpole`/`ResourceSlime` subclasses + custom renderers ride these.
- Drop Parchment from the build (official param names ship in 26.1); fix any code that leaned on Parchment param names; `getType()` -> `codec()`, `CODEC` -> `MAP_CODEC` where they appear.

### Phase 2 - BlockEntity / Entity save-load -> ValueInput/ValueOutput (large)

`saveAdditional(CompoundTag, Provider)` -> `saveAdditional(ValueOutput)`; `loadAdditional(ValueInput)` (landed 1.21.6). Same swap on the entities' `addAdditionalSaveData`/`readAdditionalSaveData` (frog/tadpole/slime + the bucket/egg capture path). Use `put*`/`getXOr`/`store(key, Codec, v)`/`read(key, Codec)`/`child(key)`. ~20 BE sites + the entity serializers.

### Phase 3 - Transactional capabilities (largest behavioural surface)

The Transfer Rework (NeoForge 21.9 / MC 1.21.9), ~48 sites. **No forward compat wrapper - assume the 21.9 shims are gone.**
- Item: `IItemHandler`/`Capabilities.ItemHandler.BLOCK` -> `ResourceHandler<ItemResource>`/`Capabilities.Item.BLOCK`. Back each appliance BE with the new handler, expose `RangedResourceHandler` slices per face (DOWN -> output, others -> input). Mutation moves to `insert`/`extract` inside `try (var tx = Transaction.openRoot()) { ...; tx.commit(); }` - which gives the EE machines' all-or-nothing transaction for free. Fire `onContentsChanged -> setChanged` on commit.
- Energy (EE Alembic/Distiller): `Capabilities.EnergyStorage.BLOCK`/`IEnergyStorage` -> `Capabilities.Energy.BLOCK`/`EnergyHandler` (long-backed). Receive-only = external `extract` returns 0 + internal `consume`.
- Fluid (milk pipe/tank exposure): `Capabilities.FluidHandler.BLOCK` -> `Capabilities.Fluid.BLOCK`/`ResourceHandler<FluidResource>`. The per-variant milk `FluidType`/`BaseFlowingFluid.Properties` **identity** is unchanged; only the tank/pipe handler reworks.

### Phase 4 - Item tint -> JSON ItemTintSource + completeness gate (large)

Tint pipeline moved to JSON at 1.21.4; `RegisterColorHandlersEvent.Item` and `ItemColors` are gone. PF's dynamic tints **port cleanly** (not a design blocker): register a custom `ItemTintSource` `MapCodec` via `RegisterColorHandlersEvent.ItemTintSources` whose `calculate(ItemStack, ClientLevel, LivingEntity)` reads the `SLIME_VARIANT` / `contained_category` / `stored_effect` component + the registry, returning `ARGB.opaque(...)`. Reference it from each tinted item's `assets/<ns>/items/*.json` `tints` array (ensure the model carries the matching `tintindex`). **Datagen these JSONs and add a completeness gate** (like the lang gate) - a missing entry renders silently greyscale (same trap as the texture-baker/milk-asset skips). Verify `BlockColor` block-icon tints separately. ~18 tint sites (minus the dropped synthesized/mimic ones? EE stays, so keep those).

### Phase 5 - GameTest registry migration (medium; gates CI)

`@GameTestHolder`/`@GameTest`/`RegisterGameTestsEvent` -> registry `TestData`/`GameTestInstance` + `minecraft:test_function` (1.21.5). The 158 method **bodies stay as-is** (already `Consumer<GameTestHelper>`). **Generate** the 3-line shim per test from a `(name, function, structure, timeout)` table. **Watch `manual_only=false`** or a required test silently skips (green CI testing nothing); prefer the JSON `test_instance/*.json` form (self-documenting vs the unlabeled `TestData` constructor args). Structure NBT stays at `data/<ns>/structure/<name>.nbt`.

### Phase 6 - GUI rendering -> RenderPipelines + two-phase render (medium/high)

`blit`/`RenderType` -> `RenderPipelines.GUI_TEXTURED` (1.21.4); two-phase `GuiRenderState` submit-don't-draw (1.21.6) across the 10 screens. **`renderTooltip` is now in the standard container-screen flow, so the `PFContainerScreen` workaround is OBSOLETE** - remove it and rebuild on the new flow rather than porting it. Rebuild the Casting Mold fluid-gauge hover hit-test and the Froglight Crucible BER on the new pipeline.

### Phase 7 - pack.mcmeta + mechanical tail (small)

**Datapack dirs stay SINGULAR** (`recipe/`, `loot_table/`, `structure/`, `tags/item/`, `advancement/`) - no rename. Only `pack.mcmeta` changes (`pack_format` int -> `min_format`/`max_format`). The `assets/.../items/*.json` set is created in Phase 4. The `SlimeVariant`/`ParentSpecies` datapack registries + codecs, the `biome_modifier` JSON, DataComponents (`.persistent(CODEC)`), `Component.translatable`, and the brain/`Sensor`/`addActivityWithConditions` architecture are all **unchanged** - only the `Identifier` rename from Phase 1 touches them.

### Phase 8 - Compile sweep + playtest + gametest green (large)

Whatever the phases didn't catch: `Item.Properties` now **requires `setId(...)`** (use `registerItem`/`registerSimpleItem`/`registerBlock` helpers - bare `new Item(props)` throws; landed 1.21.2), `BlockBehaviour.Properties` likewise, `EntityType.Builder` chain, menu registration. (`BlockEntityType.Builder.of` is unchanged.) Then the manual sweep (GameTest is blind to client visuals): creative-tab pass (every item, tint, name), the full milk pipeline per vanilla variant, tongue-kill -> Froglight -> smelt, the Crucible/Mold lane, the Terrarium loop, the boss altars, the EE lane (dev sysprop on). All 158 GameTests green.

### Phase 9 - Merge, docs, release (1 PR + release)

- Squash-merge `port/mc-26.1` -> `main`. Update `CLAUDE.md`, `ROADMAP.md`, `versioning.md` for the 26.1 target + the 1.x/2.x split + the integrations-deferred scope. Mark this doc DONE.
- Confirm `mc-1.21.1` protection is live.
- Release `2.0.0` once the build is green and soaked - it does **not** wait on partner mods (no cross-mod content ships in 2.0).

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Transactional capability rework (48 sites, no compat wrapper) is the largest surface | High | Phase 3 is multi-day; re-verify side-aware I/O per machine via runClient + GameTest. |
| Tint JSON is unvalidated -> silently greyscale froglights | Medium | Datagen + completeness gate in Phase 4 (the one new safety net the port must add). |
| `manual_only` misconfig -> required CI tests silently skip | Medium | Phase 5 prefers JSON test instances; assert the discovered-test count. |
| Exact 26.1 entity-class packages / pack-format minors uncertain from docs | Low | The compile spike + a 26.1 source jar settle these empirically. |
| Java 25 not on the dev machine | Low | Install JDK 25 in Phase 0 (environment prereq). |
| Codebase far larger than the original port | Medium | Phases 1-4 each multi-day; don't batch. Dropping cross-mod scope offsets some of this. |

## Success criteria

- [ ] `mc-1.21.1` cut, pushed, protected; 1.21.1 hotfixes ship independently.
- [ ] `./gradlew build` green on `port/mc-26.1` (NeoForge 26.1.2.76, Java 25).
- [ ] `./gradlew runGameTestServer` green - all 158 GameTests pass (and the discovered-test count is asserted).
- [ ] Manual creative-tab sweep: every item with correct tint + name (tint completeness gate passes).
- [ ] Full milk pipeline, Crucible/Mold lane, Terrarium loop, boss altars, EE lane verified by hand.
- [ ] JEI + Jade load and work; no cross-mod / Curios / Patchouli references remain in the 2.0 tree.
- [ ] Jar names carry the MC version; CF upload tags `26.1.2` on project 1552728.
- [ ] `port/mc-26.1` merged to `main`; load-bearing docs updated; `2.0.0` released.

## Backwards compatibility

- **1.21.1 worlds** stay on `mc-1.21.1` + 1.24.x (Sky Frogs + any standalone 1.21.1 player). Untouched.
- **26.1 is a fresh save target** - 2.0.0 is a new major on a new MC version; no cross-version world migration is promised.
- **Integrations are additive** - re-adding cross-mod content / Curios / Patchouli in a 2.x minor won't break a 2.0 world.
