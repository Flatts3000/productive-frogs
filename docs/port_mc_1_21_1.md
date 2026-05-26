# Port to Minecraft 1.21.1 / NeoForge 21.1.230

> **STATUS: DONE (shipped).** This port landed via PR #80 + #81, bundled with the V1.5 species-as-category redesign. `main` now targets **MC 1.21.1 / NeoForge 21.1.230**, and v1.0.0 through v1.2.0 all shipped on the ported codebase. The phase checklist below is complete; this document is retained as historical design and rationale. For current state see [CLAUDE.md](../CLAUDE.md) and [ROADMAP.md](../ROADMAP.md).
>
> *Original framing (for context): this was the blocker for the data-driven variant refactor, V1.1, and V1.2 - refactoring on the old 1.21.11 codebase was wasted effort because the load-bearing APIs (mod resource scanning at init, fluid registration, lang derivation) differ between 1.21.1 and 1.21.11.*

## Why this is happening

[Sky Frogs](../../sky-frogs) is a void-skyblock modpack built around Productive Frogs. The modpack's load-bearing mods (Ex Deorum for Tier 0 bootstrap, Skyblock Builder for worldgen) only ship on 1.21.1 — no 1.21.4+ NeoForge builds exist. Sky Frogs is therefore locked to **MC 1.21.1 + NeoForge 21.1.230**. Productive Frogs has to match for the pack to ship.

Per `sky-frogs/CLAUDE.md`:

> Minecraft: **1.21.1** (rolled back from 1.21.11 on 2026-05-23 — Ex Deorum and Skyblock Builder have no 1.21.4+ NeoForge builds, and they're load-bearing for Tier 0 bootstrap and skyblock worldgen respectively. Productive Frogs needs to be rebuilt for 1.21.1 to match.)

## Target versions

| Setting | Current | Target | Source |
|---|---|---|---|
| Minecraft | 1.21.11 | **1.21.1** | sky-frogs lockstep |
| NeoForge | 21.11.42 | **21.1.230** | sky-frogs pack.toml |
| Java | 21 | 21 | unchanged |
| Distribution | CurseForge-only (per [open_source.md](./open_source.md)) | CurseForge-only | unchanged (Modrinth still blocked by FTB ecosystem) |

## Scope — this is a port, not a bump

Between 1.21.1 and 1.21.11 (~10 minor versions of MC, equivalent NeoForge), many APIs changed. Audit of the productive-frogs codebase against the known 1.21.x deltas:

### Java API breakage (will not compile on 21.1.230)

| Surface | Current usage (1.21.11) | 1.21.1 replacement | Files affected |
|---|---|---|---|
| `net.minecraft.resources.Identifier` | NeoForge alias added later | `net.minecraft.resources.ResourceLocation` | **33 files** import `Identifier` |
| `ValueInput` / `ValueOutput` BE save/load | 1.21.5+ API | `CompoundTag` / `loadAdditional(CompoundTag)` + `saveAdditional(CompoundTag)` | **5 BlockEntities** (ConfigurableFroglightBE, SlimeMilkerBE, ResourceFrog, ResourceSlime, ResourceTadpole) |
| `ItemStacksResourceHandler` / `ResourceHandlerSlot` | NeoForge 21.4+ transfer API rewrite | `IItemHandler` / `ItemStackHandler` (legacy NeoForge API still present in 21.1.x) | **3 files** (SlimeMilkerBE, SlimeMilkerInventory, SlimeMilkerMenu) |
| `BuiltInRegistries.TEST_FUNCTION` + `DeferredRegister<Consumer<GameTestHelper>>` | 1.21.6+ registry-based GameTest registration | `@GameTestHolder` + `@GameTest` annotations | `PFGameTests.java` — substantial revert of the test scaffolding |
| `RegisterGameTestsEvent` | 1.21.6+ event | Annotation discovery (vanilla GameTest framework) | `PFGameTests.java` |
| `RenderPipelines.GUI_TEXTURED` | 1.21.4+ pipeline-based GUI rendering | Older `RenderType` / direct `GuiGraphics.blit` overload | `SlimeMilkerScreen` and any other client screens |
| `RegisterColorHandlersEvent.Item` removal + `ItemTintSource` JSON-driven tint | 1.21.4+ tint redesign | `RegisterColorHandlersEvent.Item` still present in 21.1.x — register Java item colors directly | `PFClientEvents` substantially |
| `DataComponentGetter` interface | 1.21.6+ | Use `ItemStack.get(DataComponentType)` directly | A few sites in `ConfigurableFroglightBlockEntity` etc |
| Item registration `registerItem(name, Function, properties)` shape | 1.21.5+ | Older `register(name, () -> new Item(props))` shape | `PFItems`, `PFBlocks` |

### Asset/data path breakage (will not load on 1.21.1)

| Path now (1.21.11) | Path needed (1.21.1) | File count |
|---|---|---|
| `assets/productivefrogs/items/*.json` | **delete entirely** (this file format only exists in 1.21.4+; tint sources go back into `models/item/*.json` directly) | ~70 files |
| `data/productivefrogs/tags/item/*` | `data/productivefrogs/tags/items/*` | rename of dir + every tag file path |
| `data/productivefrogs/tags/entity_type/*` | `data/productivefrogs/tags/entity_types/*` | rename |
| `data/productivefrogs/structure/empty_5x5x5.nbt` | `data/productivefrogs/structures/empty_5x5x5.nbt` | 1 file |
| `data/productivefrogs/loot_table/blocks/*`, `loot_table/entities/*` | `data/productivefrogs/loot_tables/blocks/*`, `loot_tables/entities/*` | rename |
| `data/productivefrogs/recipe/*.json` | `data/productivefrogs/recipes/*.json` | ~18 files |
| `pack.mcmeta` `pack_format` | drop from current value to 48 (1.21.1 pack format) | 1 file |

### Tint pipeline rewrite

1.21.4+ moved per-item tinting from `RegisterColorHandlersEvent.Item` (Java) to JSON `tints` arrays in `assets/<ns>/items/*.json` referencing registered `ItemTintSource` types. The mod uses this throughout: `ContainedCategoryTint`, `BucketedCategoryTint`, `SlimeVariantTint`.

In 1.21.1, the `items/*.json` format doesn't exist — tints must go through `RegisterColorHandlersEvent.Item` in Java. So:

- `PFClientEvents.onRegisterItemTintSources` deleted
- All 70 `assets/productivefrogs/items/*.json` files deleted
- The 3 `ItemTintSource` classes (`ContainedCategoryTint`, `BucketedCategoryTint`, `SlimeVariantTint`) replaced with `ItemColor` lambdas registered in `RegisterColorHandlersEvent.Item`
- All references to `tints` in JSONs removed; models in `models/item/*.json` carry the tint behavior implicitly via the registered Java item color

### Smaller / TBD-during-port

- Various `Component.translatable` overloads may differ
- `BlockEntityType.Builder.of` → `BlockEntityType.Builder.create` (or similar — these helper names shift)
- `RecipeManager` / `RecipeHolder` API shape
- `Capabilities` namespace organization
- `BiomeModifier` JSON schema (the 4 `data/productivefrogs/neoforge/biome_modifier/*.json` for parent slime spawns)
- `DataPackRegistryEvent.NewRegistry` (used by `PFDataPackRegistryEvents` for the SlimeVariant + ParentSpecies datapack registries) — codec API differs

## Strategy

### Option A: Full port on a long-lived branch (recommended)

Branch `port/mc-1.21.1` off main. Drive every change documented above (and the unknowns surfaced during the port) on that branch in a sequence of focused PRs. Merge to main when the port is fully green (build + gameTest + manual playtest).

- **Pro**: clean history, each PR reviewable, no main-branch churn during the port.
- **Pro**: existing 1.21.11 branch state stays available if we need to reference it.
- **Con**: the long-lived branch carries merge-conflict risk against any docs PRs that land on main during the port window — likely small, since the active work IS the port.

### Option B: Hard reset main to 1.21.1, drop 1.21.11 work

- **Pro**: no two-version maintenance burden.
- **Con**: loses the in-progress doc-only state (V1.1 scope, V1.2 scope, refactor PRD) — they'd need to be re-created on the port branch. Cheaply recoverable from git history but disruptive.

### Option C: Maintain two branches (1.21.11 + 1.21.1) long-term

- **Con**: doubles maintenance forever. Rejected — sky-frogs is the actual distribution target.

**Recommendation: Option A**.

## Phase breakdown — PR sizing

### Phase 0 — Branch + gradle setup (1 PR, 1 day)

- Create branch `port/mc-1.21.1` off main.
- Update `gradle.properties`:
  - `minecraft_version=1.21.1`
  - `minecraft_version_range=[1.21.1,1.21.5)`
  - `neoforge_version=21.1.230`
- Update `neoforge.mods.toml` version constraints to match.
- Update `build.gradle` moddev plugin version if needed (current is 2.0.141; verify it supports 21.1.x).
- Build will fail spectacularly. Phase 1 onwards fixes it.
- Commit message: this PR doesn't get merged on its own; it's the branch base. Subsequent phase PRs all open against `port/mc-1.21.1`.

### Phase 1 — Identifier → ResourceLocation sweep (1 PR, 1 day)

- Mechanical replace across 33 files: `import net.minecraft.resources.Identifier` → `import net.minecraft.resources.ResourceLocation`, then `Identifier` → `ResourceLocation` in all usage.
- `Identifier.fromNamespaceAndPath(...)` → `ResourceLocation.fromNamespaceAndPath(...)` (the static factory exists in 21.1.x).
- Verify nothing else needs adjusting in import statements.

### Phase 2 — BE save/load reverts to CompoundTag (1 PR, 1 day)

- 5 BlockEntities: `ConfigurableFroglightBE`, `SlimeMilkerBE`, `ResourceFrog`, `ResourceSlime`, `ResourceTadpole`.
- Each `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` reverts to `saveAdditional(CompoundTag)` / `loadAdditional(CompoundTag)`.
- Manual port of each save/load body — replace `out.putInt("X", x)` → `tag.putInt("X", x)`, and `in.getIntOr("X", 0)` → `tag.getInt("X")` (or contains check + getInt).

### Phase 3 — Resource handler API revert (1 PR, 2 days)

- `SlimeMilkerInventory`: replace `ItemStacksResourceHandler` extends with `ItemStackHandler` extends. The legacy `IItemHandler` API is the 1.21.1 equivalent.
- `SlimeMilkerMenu`: replace `ResourceHandlerSlot` with `SlotItemHandler` from `net.neoforged.neoforge.items` (the 21.1.x package).
- `SlimeMilkerBE`: capability registration in `PFModBusEvents` changes — `Capabilities.Item.BLOCK` exposes `IItemHandler` not `ResourceHandler<ItemResource>`.
- Hopper input/output side-aware logic stays the same conceptually; only the capability type changes.

### Phase 4 — GameTest annotation revert (1 PR, 3 days)

- Remove the `DeferredRegister<Consumer<GameTestHelper>>` scaffolding in `PFGameTests`.
- Each test function gets `@GameTest(...)` annotations (with `template`, `timeoutTicks`, `batch`, etc).
- `@GameTestHolder(modid = ProductiveFrogs.MOD_ID)` on the class.
- `RegisterGameTestsEvent` listener removed; vanilla annotation discovery picks them up.
- Test bodies stay 95% the same — only the registration shape changes.
- Verify `./gradlew runGameTestServer` discovers + runs all tests.

### Phase 5 — Tint pipeline revert (1 PR, 3 days)

- Delete all `assets/productivefrogs/items/*.json` (~70 files).
- Delete `PFClientEvents.onRegisterItemTintSources` and the 3 `ItemTintSource` classes (`ContainedCategoryTint`, `BucketedCategoryTint`, `SlimeVariantTint`).
- Replace with Java `ItemColor` lambdas registered via `RegisterColorHandlersEvent.Item`:
  - Configurable Froglight item: reads `SLIME_VARIANT` component → variant's `primary_color`
  - Slime Bucket item: reads `BUCKET_ENTITY_DATA` → variant > category > base
  - Resource Tadpole Bucket: reads `BUCKET_ENTITY_DATA` → category
  - Frog Egg bottle: reads `CONTAINED_CATEGORY` component
  - Variant Slime Spawn Eggs (12): per-variant lambda from the `SLIME_VARIANT` component the item stack carries
  - Parent species slime spawn eggs (4): constant per-egg color
- Re-check that the existing `BlockColor` registrations for `<category>_froglight` and `configurable_froglight` block survive the rewrite unchanged.

### Phase 6 — Asset/data path renames (1 PR, 1 day)

- `assets/productivefrogs/items/` → deleted (done in Phase 5).
- `data/productivefrogs/tags/item/` → `data/productivefrogs/tags/items/`.
- `data/productivefrogs/tags/entity_type/` → `data/productivefrogs/tags/entity_types/`.
- `data/productivefrogs/structure/` → `data/productivefrogs/structures/`.
- `data/productivefrogs/loot_table/` → `data/productivefrogs/loot_tables/`.
- `data/productivefrogs/recipe/` → `data/productivefrogs/recipes/`.
- `pack.mcmeta` `pack_format` → 48.
- Mechanical `git mv` sweep + recipe file content review (recipe schema may have shifted).

### Phase 7 — Item / Block registration shape revert (1 PR, 2 days)

- `PFItems.registerItem(name, factory, propertiesSupplier)` shape from 1.21.5+ reverts to `register(name, () -> new Item(properties))` shape.
- `PFBlocks` similar.
- BlockItem registration may need explicit `() -> new BlockItem(block.get(), properties)` rather than the shortcut form.
- Verify `BlockEntityType.Builder` API call shape.

### Phase 8 — DataPackRegistryEvent + codec adjustments (1 PR, 2 days)

- `PFDataPackRegistryEvents.NewRegistry` registration shape may differ; verify codec API still matches.
- `SlimeVariant` and `ParentSpeciesEntry` codecs should largely survive — `MapCodec.codec()` API is stable across 1.21.x.
- Adjust if the registry-event constructor shape differs.

### Phase 9 — Biome modifier JSON adjustments (1 PR, 1 day)

- 4 files at `data/productivefrogs/neoforge/biome_modifier/`.
- 1.21.1 biome modifier schema may differ slightly from 1.21.11 (especially `add_spawns` and the `targets` field shape).
- Test each parent species spawns correctly in its target biome.

### Phase 10 — Final compile sweep + manual playtest (1 PR, ~1 week)

- Whatever didn't get caught in 1-9. Likely surfaces: `Capabilities` namespace, `Component.translatable` overloads, `BlockBehaviour.Properties` factory shape, `EntityType.Builder` chain shape, sensor / brain API changes, vanilla `Tadpole` / `Frog` superclass differences (vanilla rendered hitbox constants might have moved).
- Full creative-tab playtest: every item appears, every tint reads correctly.
- Full Slime Milker pipeline test: capture → milk → place → spawn → kill → drop → smelt.
- All 12 variants: spawn egg → live entity → tongue kill → variant Froglight → smelt to resource.
- All `./gradlew runGameTestServer` green.

### Phase 11 — Merge to main + post-port cleanup (1 PR, 1 day)

- Squash-merge `port/mc-1.21.1` → `main`.
- Update `CLAUDE.md` to reflect MC 1.21.1 / NeoForge 21.1.230.
- Update `docs/dev_setup.md` for any setup changes.
- Update `docs/architecture.md` if any of the load-bearing patterns differ.

## Total estimated effort

≈ **3-4 weeks** of focused work across 11 phase PRs. Most of the cost is in Phases 4, 5, and 10.

## Impact on existing roadmap

| Roadmap item | Pre-port status | Post-port status |
|---|---|---|
| V1.0.x — data-driven refactor | Scoped in [refactor_data_driven_variants.md](./refactor_data_driven_variants.md), 2-3 weeks estimated | **Blocked until port lands.** Scope unchanged conceptually; specific NeoForge APIs (mod resource scanning, fluid registration) may differ in 1.21.1 — re-validate Phase 2 ("milk fluid auto-registration") spike when re-entering. |
| V1.1 — vanilla resource coverage | Scoped in [v1_1_scope.md](./v1_1_scope.md), 16 variants, JSON-only post-refactor | **Blocked.** Variant JSONs themselves are version-neutral; only the codebase that consumes them moves. V1.1 implementation work doesn't start until refactor is done. |
| V1.2 — new mob-drop category | Scoped in [versioning.md](./versioning.md), 5 variants + Java edits | **Blocked.** Same as V1.1. |
| V2 — automation | Deferred | Still deferred. |

## Backwards compatibility

- **Existing worlds saved on 1.21.11**: incompatible. Players on the current 0.1.0 dev build (anyone with a local `productivefrogs-0.1.0.jar`) would need to start a new world after the port. Since PF isn't published to CurseForge yet, this is zero impact externally.
- **Future MC version bumps**: the port re-establishes the baseline. Sky-frogs is locked to 1.21.1 for the foreseeable future, so we shouldn't expect another bump soon. If sky-frogs ever moves forward, we port forward in lockstep.
- **The 1.21.11 work**: stays in git history on `main` until the port branch merges. Recoverable from the squash-merged commit if needed.

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Some 1.21.11 features have no 1.21.1 equivalent (e.g. an API used by V1 wasn't backportable) | High | Audit each Phase as it's worked. If a load-bearing feature has no equivalent, surface immediately for design rethink — don't silently degrade. |
| GameTest annotation revert breaks the elaborate test scaffolding (the `testInstancesRegistered` guard etc) | Medium | Phase 4 budget includes design time for the annotation pattern. Reference an older PF commit or look at vanilla `GameTestHolders` for the canonical 1.21.1 shape. |
| Tint pipeline revert silently breaks per-variant tint rendering | Medium | Manual playtest is the only catch. Phase 5 budget assumes a full creative-tab visual sweep. |
| Cross-mod compat tags (`c:ingots/...`) may have different naming conventions in 1.21.1 vs current | Low | Audit during Phase 6; reference Mekanism / Mythic Metals on 1.21.1 for canonical tag names if needed. |
| NeoForge moddev plugin 2.0.141 may not support 21.1.x — could need a plugin version downgrade | Low | Verify in Phase 0; if needed, find the matching plugin version. |
| The 9 docs we just wrote (V1.0.x refactor, V1.1 scope, V1.2 scope) reference specific 1.21.11 APIs that don't apply to 1.21.1 | Low | Re-audit those docs post-port; mostly the design holds, only specific NeoForge API references need correction. |

## Success criteria

- [ ] `./gradlew build` green on `port/mc-1.21.1`.
- [ ] `./gradlew runGameTestServer` green — all existing GameTests pass.
- [ ] Manual creative-tab playtest: every item appears with correct tint and display name.
- [ ] Full Slime Milker pipeline works end-to-end for all 12 V1 variants.
- [ ] Frog tongue kill on a Resource Slime drops the correct variant Froglight.
- [ ] All 4 parent species spawn naturally in their target biomes (verify with `/locate biome`).
- [ ] Built jar can be installed into a sky-frogs dev environment and loads cleanly.
- [ ] Branch merged to `main`; `CLAUDE.md` and load-bearing docs updated.

## When this lands

Re-enter the data-driven variant refactor scope ([refactor_data_driven_variants.md](./refactor_data_driven_variants.md)), then V1.1 ([v1_1_scope.md](./v1_1_scope.md)), then V1.2.
