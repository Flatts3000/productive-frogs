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

No open player-facing bugs currently. By-design V1 limitations are listed below.

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

### 🔵 Crush recipes (Mekanism / Immersive Engineering / EnderIO) built, pending pre-release smoke test
The `mod_loaded`-gated recipes ([v1_3_crush_recipes.md](./v1_3_crush_recipes.md), [cross_mod_compat.md](./cross_mod_compat.md)) converting 1 metal Froglight → 2 dust in a crusher, then the mod's own (or AllTheOres') dust→ingot smelt yields 2 ingots (vs 1 from direct smelting), are now **generated and in-tree** (33 recipes under `data/productivefrogs/recipe/<modid>/`, via `scripts/generate_crush_recipes.ps1`). JSON shape is pinned by `CrushRecipeTest` and they load cleanly (gated out) in the GameTest server. **Remaining before release:** the manual per-mod `runClient` smoke test (CI has none of the crushers installed, so it can't exercise the live recipe) - confirm Mekanism accepts the nested `neoforge:components` input, EnderIO's `"bonus": "none"` yields a flat 2x, and re-verify the IE grit set. Until released, players still smelt directly for 1× yield.

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

*Last updated: 2026-05-25 (fixed the JEI "Configurable Froglight" -> "Froglight" copy and added a copy-lint test; earlier the same day resolved the cross-mod variant raw-lang-key bug. Both now in the archive; no open player-facing bugs remain).*
