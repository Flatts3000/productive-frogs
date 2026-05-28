# Known Issues

Living tracker of playtest bugs, limitations, and workarounds for Productive Frogs. Player-facing concerns only - developer-facing test flakiness, code hygiene debt, and refactor ideas live in [backlog.md](./backlog.md) §Polish.

**Resolved issues** are archived in [known_issues_archive.md](./known_issues_archive.md). When an entry here gets marked 🟢, move the whole entry to the archive so this doc stays focused on what's currently open.

## Status legend

| Symbol | Meaning |
|---|---|
| 🔴 | Open. Fix pending. |
| 🟡 | Open. Workaround available - see entry. |
| 🔵 | Limitation by design for V1. May revisit in V2 (see [versioning.md](./versioning.md)). |

Symbols 🟢 (resolved) and 🟠 (reopened / since-reverted) live in the [archive](./known_issues_archive.md).

---

## Open issues

### 🔴 Flowing Slime Milk displaces frogspawn
Flowing **Slime Milk** (from a placed source block, or a bucket emptied nearby) washes **frogspawn** away the way flowing water sweeps off a non-solid plant block. Running milk next to where frogs lay spawn can destroy the spawn before it hatches.

- **Expected:** flowing Slime Milk leaves frogspawn intact - it should not displace or break it.
- **Observed:** flowing Slime Milk removes/replaces the frogspawn.

*Reported via the Sky Frogs pack, 2026-05-28.*

### 🔴 Primed frogspawn hatch delay should be deterministic
Primed Frog Egg blocks currently inherit vanilla's **randomized** hatch window: `PrimedFrogEggBlock` rolls a uniform delay in `[3600, 12000)` ticks (**3-10 minutes** at 20 TPS) on placement, matching `minecraft:frogspawn` exactly. The earlier "hatched at 3 minutes" report was the **floor** of that range, not a timer bug.

For modded (primed) frogspawn we want a **deterministic** hatch delay instead of the random 3-10 min spread, so hatch timing is predictable for automation and progression pacing.

- **Current:** random delay in `[3600, 12000)` ticks per egg (vanilla parity).
- **Wanted:** a single fixed hatch delay for primed frogspawn (ideally config-exposed via `PFConfig`). Vanilla `minecraft:frogspawn` keeps its random window untouched.

*Reframed from a "timer is wrong" report; the random window is vanilla-parity, the deterministic delay is a Sky Frogs design request. 2026-05-28.*

---

The Spawnery dev-testing findings (Slime Milker recipe, Slime Milk JEI subtypes, container-GUI tooltips, and the Spawnery primer-required decision) shipped in v1.4.0 and are in the [archive](./known_issues_archive.md). By-design V1 limitations are listed below.

Recently resolved (see the [archive](./known_issues_archive.md)): the JEI info text calling the block "Configurable Froglight" instead of its "Froglight" display name (now guarded by a copy-lint test); cross-mod variant slimes showing a raw lang key in the Froglight tooltip (fixed via the JEI title-case fallback plus explicit `en_us.json` keys for all 57 shipped variants, now guarded by a lang-completeness unit test); empty-bucket slime capture; and canonical species ordering across tabs / JEI / recipe book.

---

## V1 limitations (by design)

These are intentional V1 scope cuts. Each is on the V2 roadmap unless noted otherwise.

### 🔵 No Frog Terrarium / Habitat block
Frogs in V1 live where you place them, near water. A placeable housing block with I/O inventory is V2.

### 🔵 Slime Milk only in buckets (by design - final)
Bucket is the only first-party Slime Milk container, and that is the intended design - **jugs, tanks, and other custom fluid containers are not planned**. The underlying fluid IS accessible to any tank-mod ecosystem (Mekanism, Thermal, Create, Fluid Tanks): the bucket exposes `Capabilities.Fluid.ITEM` via NeoForge's automatic `BucketItem` registration and the source block uses vanilla `LiquidBlock` pickup, so bulk storage is covered by a tank mod. Verification details live in the archive under "Slime Milk integrates with tank mods".

### 🔵 No texture-desaturation cue on milk source blocks (count shown via Jade)
Source blocks deplete after `depletionCount` spawns (default 16) and drain to air. The block texture does not desaturate as the counter drops, and a desaturation renderer is **not planned**. Instead the remaining count is surfaced in the **Jade** look-at tooltip ("Slime spawns left: N / cap", where cap is the configured `depletionCount`; or "unlimited" when depletion is off) by `ProductiveFrogsJadePlugin` (which also shows the Slime Milker / Spawnery cook progress). So the info is available in-world via Jade; only the in-texture cue is omitted by design. Added in `feat/jade-tooltips`.

### 🔵 No native crusher / pestle
No in-house crushing block, and a native crusher is **not planned** (see the roadmap's "Explicitly NOT planned"). The 2× yield on metal Froglights is unlocked by installing Mekanism, Immersive Engineering, or EnderIO (the optional `mod_loaded` crush recipes shipped in v1.3.0); the 2× payoff is delegated to those crusher mods rather than reimplemented in-house.

### 🔵 No drop-collection block
Use vanilla hoppers under the frog pen to collect Froglight item entities. A custom collection block is **not planned** (see the roadmap's "Explicitly NOT planned").

---

## Cross-Mod Compat caveats

### 🔵 Crush recipes (Mekanism / Immersive Engineering / EnderIO) - 2x yield needs an external crusher mod (shipped v1.3.0)
The `mod_loaded`-gated recipes ([v1_3_crush_recipes.md](./v1_3_crush_recipes.md), [cross_mod_compat.md](./cross_mod_compat.md)) converting 1 metal Froglight → 2 dust in a crusher, then the mod's own (or AllTheOres') dust→ingot smelt yields 2 ingots (vs 1 from direct smelting), are **generated, in-tree, and verified** (33 recipes under `data/productivefrogs/recipe/<modid>/`, via `scripts/generate_crush_recipes.ps1`). JSON shape is pinned by `CrushRecipeTest`; they load cleanly (gated out) in the GameTest server; and the full per-mod `runClient` smoke test passed 2026-05-26 (Mekanism accepts the nested `neoforge:components` input, EnderIO `"bonus": "none"` is a flat 2x, the IE grit routing matches, and the ATO fallback resolves - `scripts/fetch_dev_mods.py` + [dev_setup.md](./dev_setup.md) set this up). They are player-available as of v1.3.0 (with one of those crusher mods installed); without a crusher mod, players smelt directly for 1× yield.

Matching is per-variant via the `neoforge:components` ingredient - there is **no** `crushable` item tag (every variant is the same `configurable_froglight` item distinguished only by component, so a tag can't select metals).

### 🔵 No `compat/` Java package - deliberate
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

*Last updated: 2026-05-28 (logged: flowing Slime Milk displaces frogspawn; primed frogspawn hatch delay should be deterministic - reported via the Sky Frogs pack).*
