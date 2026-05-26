# Known Issues

Living tracker of playtest bugs, limitations, and workarounds for Productive Frogs. Player-facing concerns only — developer-facing test flakiness, code hygiene debt, and refactor ideas live in [backlog.md](./backlog.md) §Polish.

**Resolved issues** are archived in [known_issues_archive.md](./known_issues_archive.md). When an entry here gets marked 🟢, move the whole entry to the archive so this doc stays focused on what's currently open.

## Status legend

| Symbol | Meaning |
|---|---|
| 🔴 | Open. Fix pending. |
| 🟡 | Open. Workaround available — see entry. |
| 🔵 | Limitation by design for V1. May revisit in V2 (see [versioning.md](./versioning.md)). |

Symbols 🟢 (resolved) and 🟠 (reopened / since-reverted) live in the [archive](./known_issues_archive.md).

---

## Open issues

### 🟡 Slime Milker had no crafting recipe (fix in `feat/spawnery`)
The Slime Milker (`productivefrogs:slime_milker`) shipped with a loot table (it drops itself when broken) but **no crafting recipe**, so a survival player had no intended way to obtain it - only creative or `/give`, or breaking an already-placed one (a chicken-and-egg). The block is the V1 production keystone, so this blocked the survival loop. Discovered 2026-05-26 while speccing the Spawnery.
**Fix written** (lands with the Spawnery PR): `data/productivefrogs/recipe/slime_milker.json` - shaped, 5 cobblestone + 3 planks + 1 slime ball (slime ball centered), mirroring the Spawnery's frame. Not config-gated (the Milker is always craftable). Pending `./gradlew build` verification + a JUnit recipe-shape test alongside the Spawnery's; will move to the archive once merged.

### 🟡 Slime Milk buckets collapsed to a single entry in JEI (fixed in `feat/spawnery`)
Every Slime Milk variant showed in the creative tab (one stamped stack per variant), but JEI listed only **one** Slime Milk Bucket. Root cause: `ProductiveFrogsJeiPlugin.registerItemSubtypes` registered `SLIME_VARIANT`-keyed subtype interpreters for the Configurable Froglight and the Resource Slime spawn egg, but **not** for `slime_milk_bucket` - so JEI treated every variant-stamped milk bucket as the same ingredient and deduped them to one. Discovered 2026-05-26 during Spawnery dev testing. Pre-existing bug, unrelated to the Spawnery.
**Fixed (2026-05-26):** registered the existing `slimeVariantInterp` for `PFItems.SLIME_MILK_BUCKET` in `registerItemSubtypes`, so each variant is now a distinct JEI ingredient (and its per-variant info page attaches correctly). Pending a `runClient` confirm - JEI subtype behaviour isn't GameTest-visible.

### 🟡 Spawnery runs without a primer (decision pending)
As built (per the original spec the user confirmed), an empty primer slot makes the Spawnery produce a plain **vanilla frogspawn** bottle; a primer only upgrades the output to a species egg. Flagged 2026-05-26 in dev testing as possibly unwanted - the open question is whether a primer should be **required** (Spawnery stalls with an empty primer slot, dropping the vanilla-frogspawn path entirely). This is a design decision, not a malfunction. If "required": treat a null primer category like "no bottle" in `SpawneryBlockEntity.serverTick`'s `canProduce` gate, then update `docs/spawnery.md` (D-decisions) and the `spawneryProducesVanillaFrogspawnBottle` GameTest. Ties into the broader primer-design discussion that's still open.

### 🔴 No item tooltips in the mod's container GUIs (Slime Milker + Spawnery)
Hovering slots in the Spawnery screen shows no tooltip - and the **shipped Slime Milker has the same problem** (confirmed 2026-05-26), so this is a pre-existing, shared bug, not Spawnery-specific. Both `SpawneryScreen` and `SlimeMilkerScreen` override only `renderBg` and otherwise inherit `AbstractContainerScreen`; empirically that path is not surfacing slot tooltips in this build (1.21.1 / NeoForge 21.1.230 + JEI). Because the Milker is already on CurseForge, this affects shipped content.
**Likely fix (verify before applying):** override `render(GuiGraphics, int, int, float)` - ideally in a shared screen base both GUIs extend - to call `super.render(...)` then `renderTooltip(...)`. First confirm whether it's no-tooltip-at-all (the vanilla item-name box never appears) vs. only the JEI "hold U/R for recipes" hint missing: if the vanilla box is actually fine and only JEI's line is gone, the cause is JEI integration, not the render path, and adding `renderTooltip` would double-render. Discovered 2026-05-26 during Spawnery dev testing.

By-design V1 limitations are listed below.

Recently resolved (see the [archive](./known_issues_archive.md)): the JEI info text calling the block "Configurable Froglight" instead of its "Froglight" display name (now guarded by a copy-lint test); cross-mod variant slimes showing a raw lang key in the Froglight tooltip (fixed via the JEI title-case fallback plus explicit `en_us.json` keys for all 57 shipped variants, now guarded by a lang-completeness unit test); empty-bucket slime capture; and canonical species ordering across tabs / JEI / recipe book.

---

## V1 limitations (by design)

These are intentional V1 scope cuts. Each is on the V2 roadmap unless noted otherwise.

### 🔵 No Frog Terrarium / Habitat block
Frogs in V1 live where you place them, near water. A placeable housing block with I/O inventory is V2.

### 🔵 Slime Milk only in buckets (UI surface)
Bucket-only is the shipped UI in V1 — no jugs, tanks, or custom fluid containers. The underlying fluid IS accessible to any tank-mod ecosystem (Mekanism, Thermal, Create, Fluid Tanks): the bucket item exposes `Capabilities.Fluid.ITEM` via NeoForge's automatic `BucketItem` registration, and the source block uses vanilla `LiquidBlock` bucket-pickup mechanics. Verification details live in the archive under "Slime Milk integrates with tank mods — confirmed working."

### 🔵 No visual depletion countdown on milk source blocks
Source blocks deplete after `depletionCount` spawns (default 16) and drain to air. The texture does NOT desaturate as the counter approaches zero — the counter lives in blockstate but has no client-side visual cue. Specced in `farming.md`; deferred to polish so J5 could ship without a custom fluid renderer.

### 🔵 No native crusher / pestle
V1 ships no in-house crushing block. The 2× yield on metal Froglights is unlocked by installing Mekanism, Immersive Engineering, or EnderIO (compat recipes ship in v1.3; a native in-house crusher is V2 - see Cross-Mod section below).

### 🔵 No drop-collection block
Use vanilla hoppers under the frog pen to collect Froglight item entities. A custom collection block is V2.

---

## Cross-Mod Compat caveats

### 🔵 Crush recipes (Mekanism / Immersive Engineering / EnderIO) built + verified, ship in v1.3
The `mod_loaded`-gated recipes ([v1_3_crush_recipes.md](./v1_3_crush_recipes.md), [cross_mod_compat.md](./cross_mod_compat.md)) converting 1 metal Froglight → 2 dust in a crusher, then the mod's own (or AllTheOres') dust→ingot smelt yields 2 ingots (vs 1 from direct smelting), are **generated, in-tree, and verified** (33 recipes under `data/productivefrogs/recipe/<modid>/`, via `scripts/generate_crush_recipes.ps1`). JSON shape is pinned by `CrushRecipeTest`; they load cleanly (gated out) in the GameTest server; and the full per-mod `runClient` smoke test passed 2026-05-26 (Mekanism accepts the nested `neoforge:components` input, EnderIO `"bonus": "none"` is a flat 2x, the IE grit routing matches, and the ATO fallback resolves - `scripts/fetch_dev_mods.py` + [dev_setup.md](./dev_setup.md) set this up). They become player-available when v1.3 publishes to CurseForge; until then players smelt directly for 1× yield.

Matching is per-variant via the `neoforge:components` ingredient - there is **no** `crushable` item tag (every variant is the same `configurable_froglight` item distinguished only by component, so a tag can't select metals).

### 🔵 No `compat/` Java package — deliberate
Cross-mod integration ships exclusively as JSON datapacks gated by `neoforge:conditions → mod_loaded`. Variants for modded resources (e.g. Mythic Metals) similarly ship as JSON `SlimeVariant` entries with `mod_loaded` conditions. See `docs/architecture.md` for the schema.

---

## How to report a new issue

1. Try to reproduce in the latest `main` build (`./gradlew build` → `runClient`).
2. If it still happens, file a GitHub issue with:
   - MC / NeoForge / Productive Frogs versions
   - Minimal repro steps
   - Expected vs observed
   - `latest.log` snippet around the failure if it's a crash / log warning
3. Tag the issue `bug` or `limitation` so it sorts cleanly against this doc.

---

*Last updated: 2026-05-26 (Spawnery dev-testing findings: Slime Milk JEI subtype-collapse, Slime Milker missing recipe, Spawnery no-primer design question, and a shared container-GUI tooltip bug affecting both the Slime Milker and the Spawnery).*
