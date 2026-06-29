# MC 26.1 Forward-Port Research Findings

> **STATUS: RESEARCH COMPLETE (2026-06-29).** Consolidated findings from a four-track research sweep that backs [`port_mc_26_1.md`](./port_mc_26_1.md). Every claim here is sourced; the source URLs live at the end of each section. This doc is the authoritative pre-port reference - the port plan's phases derive from it, and where they disagreed, this doc won and the plan was corrected.

## Executive summary

Porting PF from MC 1.21.1 to **MC 26.1 / NeoForge 26.1.2.76** (ATM11's target) is **viable, with no design-level blocker** - but it is a substantially bigger lift than the original 1.21.11 -> 1.21.1 backport, and the research overturned two assumptions in the first-draft plan.

**The reassuring findings:**
- The one feared design risk - PF's *dynamic, registry-driven per-variant item tints* - **ports cleanly** as a custom `ItemTintSource` (the tint callback gets the live `ItemStack` + `registryAccess()`). Not a redesign.
- PF's core data model is untouched: the `SlimeVariant`/`ParentSpecies` datapack registries, `SlimeVariant.CODEC`, DataComponents, the brain/Sensor architecture, biome-modifier JSON, and `FluidType` identity all have a **clean forward path**.
- Build toolchain is a straightforward bump: **no hard blockers**, moddev plugin and Gradle already satisfy 26.1.
- PF's hard dependencies are present on 26.1: **Jade (stable), JEI, Curios** all have builds.

**The two corrections the research forced (plan was wrong):**
1. **Datapack dirs stay SINGULAR.** The plan assumed a singular->plural rename phase. Wrong: singularization happened at MC 1.21 and *holds* on 26.1 (`recipe/`, `loot_table/`, `structure/`, `tags/item/`). **No rename needed.** What *did* change is `pack.mcmeta`: the single `pack_format` int became `min_format`/`max_format` fields (since 1.21.9).
2. **The "ResourceLocation check" is not a near-no-op.** Mojang renamed `ResourceLocation` -> **`Identifier`** globally at MC 1.21.11 and reorganized entity/model packages, and 26.1 ships **deobfuscated**. This is a pervasive mechanical sweep touching nearly every file, not a quick verify.

**The real shape of the work:** five genuine rework surfaces (transactional capabilities, item tints, two-phase GUI, GameTest registry, and the deobf/`Identifier` sweep), plus a long tail of mechanical edits. The original "3-4 week" backport estimate does **not** transfer.

---

## Track B - Toolchain & build viability

| Setting | Current (1.21.1) | Required (26.1) | Notes |
|---|---|---|---|
| NeoForge | `21.1.230` | **`26.1.2.76`** | Latest stable; exact build ATM11 0.1.2 pins. 4-part scheme now (`26.1.<hotfix>.<build>`; `-beta` suffix = breaking allowed). |
| moddev plugin | `2.0.141` | **`2.0.141`** | No bump - the official 26.1.2 MDK ships exactly this version. |
| **Java** | **21** | **25** | `JavaLanguageVersion.of(25)`. **The one real environment prerequisite - a JDK 25 must be installed.** |
| Gradle | `9.5.1` (wrapper) | min `9.1.0` | Already satisfied. |
| MC version pin | `[1.21.1,1.21.5)` | **`[26.1.2]`** | MDK uses an *exact* pin under the new scheme, not a half-open range. |
| NeoForge dep range | - | `[26.1.2.76,)` | In `neoforge.mods.toml`. |
| `loaderVersion` | `[4,)` | likely **dropped** | 26.1.2 MDK's `neoforge.mods.toml` omits `modLoader`/`loaderVersion` entirely; verify against the MDK template before deleting. |
| resource `pack_format` | (34) | **84** | Now `min_format`/`max_format` fields, not a bare `pack_format` int (since 1.21.9). |
| data `pack_format` | (48) | **101** | Resource and data numbers diverged - not interchangeable. Minor (`.0`/`.1`) to reconfirm against the 26.1.2 client. |
| CurseForgeGradle | `1.1.26` | `1.2.30` (advised) | No confirmed break, but predates Gradle 9; bump defensively. |

**Verdict:** buildable today; the toolchain bump is easy. The cost is the *source* port. Headline prereq: **install JDK 25** (and the machine's stale-`JAVA_HOME` override story moves from jdk-21 to jdk-25 for the port branch).

Sources: NeoForge 26.1 release, NeoForge maven, versioning docs, MC wiki 26.1, the `NeoForgeMDKs/MDK-26.1.2-ModDevGradle` template, ATM11 0.1.2 CurseForge file.

---

## Track C - Dependency availability on 26.1 (as of 2026-06-29)

| Mod | Category | 26.1 build? | Latest | Stability |
|---|---|:--:|---|---|
| **Jade** | library | **Y** | 26.1.8 | **stable** |
| **JEI** | library | **Y** | 29.6.2.38 | beta |
| **Curios** | library | **Y** | 15.0.0-beta.2+26.1.2 | beta |
| **Patchouli** | library | **N** | 1.21.1-93 | (1.21.1 only) |
| AE2 | partner | **Y** | 26.1.10 | beta |
| AllTheOres | partner | **Y** | 4.0.4 | stable |
| Mystical Agriculture | partner | **Y** | 9.0.3 | stable |
| Powah | partner | **Y** | 7.0.4-alpha | alpha |
| Refined Storage | partner | **Y** | 3.2.1 | stable |
| Just Dire Things | partner | **Y** | 1.6.11 | stable |
| Mekanism | partner | **N** | 10.7.19.85 | (1.21.1 only) |
| Create | partner | **N** | (1.21.1, port in progress) | - |
| Industrial Foregoing | partner | **N** | 1.21-3.6.38 | (1.21.1 only) |
| Flux Networks | partner | **N** | 1.21.1-8.0.0 (Jan 2025) | (looks abandoned) |

**Curios vs Accessories:** target **Curios** - it's the *only* accessory-slot API with a 26.1 build (Accessories returns empty for the 26.1 filter). PF's slot is data-driven JSON (`data/productivefrogs/curios/slots/brewed.json`) + a load-gated validator, **unchanged** from today's posture - the brewed-charm code carries over as-is.

**What this gates:**
- **Testable now on 26.1:** core mod, JEI, Jade (stable - safest dep), Curios brewed slot, and cross-mod content for AE2 / AllTheOres / Mystical Agriculture / RS / Powah / Just Dire Things.
- **Patchouli (guide):** not ported. But per CLAUDE.md the guide is `compileOnly` + inert when absent, so **2.0.0 ships with the guide dormant** and lights up when Patchouli ports - a soft feature-gate, not a release blocker. *(The compile spike should confirm whether any Java glue `compileOnly`-references the Patchouli API, or it's purely data + runtime.)*
- **Unported partners** (Mekanism, Create, IF) - their JSON-gated content can't be *tested* on 26.1 yet, but they never block the build. **Flux Networks looks abandoned** (no update since Jan 2025) - candidate to **retire** rather than port.

---

## Track A - NeoForge API delta sweep (1.21.1 -> 26.1)

The two structural shocks that touch nearly every file:
- **Deobfuscation + `ResourceLocation` -> `Identifier` (MC 1.21.11).** 26.1 ships unobfuscated; 1.21.11 globally renamed `ResourceLocation` -> `Identifier` (`Identifier.of(ns, path)`), moved `net.minecraft.world.entity.*` / `client.model.*` into per-type subpackages, and renamed e.g. `getType()` -> `codec()`, `CODEC` -> `MAP_CODEC` on several surfaces. **Parchment can be dropped** (official param names ship in 26.1).
- **The Transfer Rework (NeoForge 21.9 / MC 1.21.9).** Item/fluid/energy capabilities moved to a transactional `ResourceHandler` model with new capability ids. A genuine redesign.

| # | Surface | 1.21.1 -> 26.1 | Landed | Risk |
|---|---|---|---|---|
| 1 | Identifiers | `ResourceLocation` -> **`Identifier`**, `fromNamespaceAndPath` -> `Identifier.of` | 1.21.11 | mechanical, **pervasive** |
| 2 | BE/Entity save-load | `saveAdditional(CompoundTag, Provider)` -> `saveAdditional(ValueOutput)`; `loadAdditional(ValueInput)`. `put*`/`getXOr`/`store(key,Codec,v)`/`read(key,Codec)`/`child(key)` | **1.21.6** | mechanical, ~20 sites + entities |
| 3 | Item/Block registration | `Item.Properties` now **requires `setId(...)`**; use `registerItem`/`registerSimpleItem`/`registerBlock` helpers. Bare `new Item(props)` throws | 1.21.2 | mechanical |
| 4 | `BlockEntityType.Builder.of` | **unchanged** | - | none |
| 5 | Item tint | `RegisterColorHandlersEvent.Item`/`ItemColors` **removed** -> JSON `ItemTintSource` (custom `MapCodec`) | 1.21.4 | **rework** |
| 6 | Item transfer | `IItemHandler`/`Capabilities.ItemHandler.BLOCK` -> `ResourceHandler<ItemResource>`/**`Capabilities.Item.BLOCK`**, transactional | 1.21.9 | **rework** |
| 7 | Energy | `Capabilities.EnergyStorage.BLOCK`/`IEnergyStorage` -> **`Capabilities.Energy.BLOCK`**/`EnergyHandler` (long-backed, transactional) | 1.21.9 | **rework** |
| 8 | Fluid identity | `FluidType`/`BaseFlowingFluid.Properties`/`getTintColor` **stable**; but fluid *transfer/tank* exposure -> `ResourceHandler<FluidResource>`/`Capabilities.Fluid.BLOCK` | identity: - ; transfer: 1.21.9 | milk defs clean; pipe/tank exposure reworks |
| 9 | GameTest | `@GameTestHolder`/`@GameTest`/`RegisterGameTestsEvent` -> registry `TestData`/`GameTestInstance` + `minecraft:test_function` | 1.21.5 | **rework** (bodies stay) |
| 10 | Datapack registries | `DataPackRegistryEvent.NewRegistry` + codecs **unchanged** | - | none |
| 11 | `biome_modifier` JSON | `neoforge:add_spawns` schema **unchanged** | - | none |
| 12 | GUI render | `blit`/`RenderType` -> `RenderPipelines.GUI_TEXTURED`; two-phase `GuiRenderState` (submit-don't-draw); **`renderTooltip` now in the standard flow -> `PFContainerScreen` workaround is OBSOLETE** | 1.21.4 + 1.21.6 | **rework** |
| 13 | Components / brain | `Component.translatable`, DataComponent API (`.persistent(CODEC)`), brain/`Sensor`/`addActivityWithConditions` **structurally unchanged** (Identifier rename only) | - | mechanical |
| 14 | Datapack dirs | **SINGULAR, unchanged** (`recipe/`, `loot_table/`, `structure/`, `tags/item/`). `pack.mcmeta`: `pack_format` int -> `min_format`/`max_format` fields | dirs: 1.21 (stable); mcmeta: 1.21.9 | mechanical |

**Deobf blast radius on vanilla subclasses:** `Frog`/`Tadpole` -> `net.minecraft.world.entity.animal.frog.*`; `Slime`/`MagmaCube` -> `...monster.slime.*`; models likewise (`LavaSlimeModel` -> `MagmaCubeModel`). `Tadpole.HITBOX_*` now `final` (PF only reads them - fine). Do the `Identifier` + import sweep as **one mechanical pass first**, so the tree compiles against 26.1 names before the semantic reworks.

*(Confidence note from the researcher: the exact `Slime` entity-class package is inferred from the confirmed model-class move; verify against a 26.1 source jar. Pack-format minor numbers to reconfirm against the 26.1.2 client - the compile spike settles both.)*

---

## Track D - High-risk rewrite deep-dives (the three scariest)

**1. Transfer API (~48 sites) - wide but mechanical.** Back each appliance BE with the new component-based handler, expose `RangedResourceHandler` slices per face (DOWN -> output, others -> input), register `Capabilities.Item.BLOCK`. Mutation moves from `extractItem`/`setStackInSlot` to `insert`/`extract` inside `try (var tx = Transaction.openRoot()) { ...; tx.commit(); }` - which hands the EE machines' all-or-nothing transaction *for free*. EE energy: `EnergyHandler` with external `extract` returning 0 + internal `consume`. **Don't lean on the 21.9 `IItemHandler.of`/`IEnergyStorage.of` compat shims - assume gone by 26.1.** Fire `onContentsChanged -> setChanged` on commit, not mid-transaction.

**2. Dynamic item tints - NOT a design blocker (the key result).** Custom `ItemTintSource.calculate(ItemStack, ClientLevel, LivingEntity)` gets the live stack, so PF's component-read + registry-lookup tints port nearly verbatim. Register a `MapCodec` via `RegisterColorHandlersEvent.ItemTintSources`; reference it from each tinted item's `assets/<ns>/items/*.json` `tints` array. **The real risk is the new unvalidated per-item JSON asset** - a missing/mis-indexed `tints` entry renders silently greyscale (same failure family as the texture-baker/milk-asset traps already in memory). **Mitigation: datagen the tint JSONs + add a completeness gate** like the lang gate. Two musts: wrap returns in `ARGB.opaque(...)`; ensure the model carries the matching `tintindex`. Verify `BlockColor` block-icon tints separately (the item-JSON pipeline doesn't cover them).

**3. GameTest (158 methods) - bodies stay as-is.** The method bodies are already `Consumer<GameTestHelper>`. Migration is a generated 3-line shim per test: a `TEST_FUNCTION.register(...)`, a `FunctionGameTestInstance`, and the per-test metadata moved into `TestData` (or a `test_instance/*.json`). **Generate it** from a `(name, function, structure, timeout)` table. Two CI traps: `manual_only` must be `false` (else the test silently doesn't run - green CI testing nothing on a *required* job), and `TestData`'s constructor args are unlabeled and easy to transpose (prefer the JSON form for self-documentation).

---

## Consolidated design-rethink risks (ranked by effort)

1. **Transactional capabilities (item/energy/fluid-tank)** - highest effort; no forward wrapper. Every appliance + the two EE RF machines + any milk pipe/tank exposure.
2. **Item tinting -> `ItemTintSource`** - high effort, wide rearchitecture, but **not a design blocker** (dynamic tints work). Add the datagen completeness gate.
3. **Two-phase GUI render + tooltip** - medium/high; `PFContainerScreen` base is obsolete; the Crucible BER + Mold fluid-gauge hit-test rebuild on `RenderPipelines`.
4. **GameTest registry migration** - medium; blocks the required `gameTest` CI job; generated shim.
5. **Deobf / `Identifier` / package-reorg sweep** - mechanical but touches nearly every file; do it **first** as one pass.

Everything else (BE Value I/O, registration-id helpers, `BlockEntityType.Builder`, datapack registries, biome modifiers, `FluidType` identity, DataComponents, brain/Sensor, singular datapack dirs) is a clean, well-documented mechanical forward path.

---

## Decisions the research surfaced (for maintainer sign-off)

1. **Ship 2.0.0 with the guide dormant** (Patchouli unported) and light it up when Patchouli lands - vs hold the release for Patchouli, vs swap guide backends.
2. **Retire Flux Networks support** (v1.11) - the mod looks abandoned on the modern line. Drop the variant + crush JSONs rather than carry dead integration.
3. **Target Curios** (not Accessories) on 26.1 - forced, Accessories has no 26.1 build. No real decision, just confirmation.
4. **Install JDK 25** on the dev machine - environment prerequisite for any 26.1 work.
5. **Add a tint-JSON completeness gate** alongside the existing lang gate, as part of the tint rework (prevents silently-greyscale froglights).
6. **Sequence the port deobf-first:** one mechanical `Identifier`/import sweep to compile against 26.1 names, *then* the five semantic reworks.

---

## Recommended next step

The desk research has taken the unknowns as far as documents can. The remaining uncertainty (exact 26.1 entity-class packages, pack-format minors, whether any Patchouli Java glue blocks compile, and the true error count) is best resolved by the **empirical compile spike**: stand up `port/mc-26.1`, do Phase 0 (gradle pins + Java 25 + the `Identifier` sweep), and let the compiler enumerate the real error surface. The compiler is the most honest researcher left.

## Sources

All four research tracks cited primary sources (NeoForge per-version primers 1.21.2 -> 26.1, NeoForge release notes for 21.2 / 21.6 / 21.9-transfer-rework / 26.1, docs.neoforged.net for Value I/O / Capabilities / Items / Data Components / Codecs, MC wiki 26.1 + Pack_format, the 26.1.2 MDK, and the Modrinth/CurseForge file listings for every dependency). Full URL list retained in the four task transcripts; key anchors:
- NeoForge primers: https://github.com/neoforged/.github/tree/main/primers and https://docs.neoforged.net/primer/docs/
- Transfer Rework: https://neoforged.net/news/21.9-transfer-rework/
- Value I/O: https://docs.neoforged.net/docs/datastorage/valueio/
- Capabilities: https://docs.neoforged.net/docs/1.21.10/inventories/capabilities/
- Items / registration: https://docs.neoforged.net/docs/items/
- Client item tints: https://docs.neoforged.net/docs/resources/client/models/items/
- GameTest: https://docs.neoforged.net/docs/1.21.5/misc/gametest/
- 26.1 MDK: https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle
- Pack format: https://minecraft.wiki/w/Pack_format
