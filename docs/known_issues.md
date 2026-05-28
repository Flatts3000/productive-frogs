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

### 🔴 Flowing Slime Milk displaces blocks it shouldn't
Flowing **Slime Milk** (from a placed source block, or a bucket emptied nearby) washes other blocks away the way flowing water sweeps off a non-solid plant block. Three cases it should leave intact but currently doesn't:

- **Frogspawn / Primed Frog Eggs** - running milk next to where frogs lay spawn can destroy the spawn before it hatches.
- **Water source blocks** - flowing milk should not overwrite or displace adjacent water sources.
- **Other Slime Milk source blocks** - flowing milk should not displace neighboring milk source blocks.

- **Expected:** flowing Slime Milk leaves frogspawn, water source blocks, and other Slime Milk source blocks intact - it should not displace or break any of them.
- **Observed:** flowing Slime Milk removes/replaces all three.

*Reported via the Sky Frogs pack, 2026-05-28.*

### 🔴 Frogs drown in Slime Milk
A Resource Frog standing in / submerged by **Slime Milk** takes drowning damage and can die. Slime Milk is meant to be poured into pools that frogs work over (the milk-fountain production loop), so frogs are routinely in contact with it - they should treat it as safe, not as a suffocating fluid.

- **Expected:** frogs are unharmed by Slime Milk (no air-supply drain, no drowning damage) - they can sit in it like vanilla frogs sit in water.
- **Observed:** frogs lose air and take drowning damage in Slime Milk.

*Reported via the Sky Frogs pack, 2026-05-28.*

### 🔴 Bucketing and replacing Slime Milk resets its spawns-remaining counter
A Slime Milk source block tracks how many slime spawns it has left before it depletes (`depletionCount`, default 16, surfaced via Jade and the `SPAWNS_REMAINING` blockstate). Picking the milk up with a bucket and placing it back down **resets that counter to full** - so a partially-depleted source can be topped back to 16 just by re-bucketing, defeating depletion.

- **Expected:** the remaining-spawns count survives a bucket pickup -> place round-trip (carried on the Slime Milk bucket, restored on placement).
- **Observed:** re-placed Slime Milk starts fresh at the full `depletionCount`.

*Reported via the Sky Frogs pack, 2026-05-28.*

### 🔴 Jade "Slime spawns left" readout doesn't update as the source depletes
The Slime Milk source block's Jade look-at tooltip ("Slime spawns left: N / cap") **stays pinned at the full count** (e.g. `16 / 16`) even after the source has spawned slimes and its remaining count has dropped. The number only ever shows the cap, never the live remaining value.

- **Expected:** the readout counts down (`15 / 16`, `14 / 16`, ...) as the source depletes, matching the actual remaining spawns.
- **Observed:** it stays at `cap / cap` regardless of how many spawns have been consumed.

Likely a Jade server-data sync gap: the remaining count lives on the server-side BlockEntity and isn't being pushed to the client tooltip (Jade needs the value via its `IServerDataProvider`/`appendServerData` path, or the synced blockstate `SPAWNS_REMAINING` read instead of a server-only field). Client-only - invisible to `build` / GameTest, needs a `runClient` check (see [[jade_plugin_config_lang_key]] for the Jade-is-manual-drop-in caveat).

*Reported via the Sky Frogs pack with a Jade screenshot (source reading 16 / 16). 2026-05-28.*

### 🔴 Primed frogspawn hatch delay should be deterministic
Primed Frog Egg blocks currently inherit vanilla's **randomized** hatch window: `PrimedFrogEggBlock` rolls a uniform delay in `[3600, 12000)` ticks (**3-10 minutes** at 20 TPS) on placement, matching `minecraft:frogspawn` exactly. The earlier "hatched at 3 minutes" report was the **floor** of that range, not a timer bug.

For modded (primed) frogspawn we want a **deterministic** hatch delay instead of the random 3-10 min spread, so hatch timing is predictable for automation and progression pacing.

- **Current:** random delay in `[3600, 12000)` ticks per egg (vanilla parity).
- **Wanted:** a single fixed hatch delay for primed frogspawn (ideally config-exposed via `PFConfig`). Vanilla `minecraft:frogspawn` keeps its random window untouched.

*Reframed from a "timer is wrong" report; the random window is vanilla-parity, the deterministic delay is a Sky Frogs design request. 2026-05-28.*

### 🔴 Tadpole growth and frog breeding times should be deterministic
Same intent as the hatch-delay request above, extended to the rest of the lifecycle. Currently both inherit vanilla pacing rather than a mod-controlled deterministic value:

- **Tadpole -> frog growth:** `ResourceTadpole` inherits vanilla `Tadpole`'s age counter (a fixed ~24000-tick / 20-min maturation, slimeball-accelerated). Deterministic today, but **not config-exposed** - packs can't tune it.
- **Frog breeding / re-breed cooldown:** `ResourceFrog` inherits vanilla `Animal` love-mode and post-breed cooldown pacing, which carries vanilla's **randomized** component. Conception -> lay is handled by `LayCategoryFrogspawn` (fires when the pregnant frog reaches a valid water tile - no fixed timer of its own).

- **Current:** vanilla-inherited timings (growth fixed-but-unexposed; breeding partly randomized).
- **Wanted:** deterministic, **config-exposed** (`PFConfig`) growth and breeding/re-breed delays for the modded lifecycle, so progression and automation pacing are predictable. Vanilla frogs/tadpoles keep their stock pacing.

*Companion to the hatch-delay request; same Sky Frogs determinism goal across the full egg -> tadpole -> frog -> breed loop. 2026-05-28.*

### 🔴 Frogs from crafted frogspawn should start at baseline stats (not a starter roll)
A Resource Frog matured from a **crafted** (Spawnery / bottled Frog Egg, i.e. non-bred) frogspawn came out with stats **Appetite 2 / Bounty 1 / Reach 3** - i.e. a random above-baseline roll.

**Current code behavior:** `ResourceFrog.finalizeSpawn()` rolls starter stats for *any* non-bred frog, uniformly in `[starterStatMin, starterStatMax]` = **`[1, 3]`** by default (`PFConfig` `breeding.starterStatMin` / `starterStatMax`; documented in `docs/frog_breeding.md`). So `2 / 1 / 3` is a legal starter roll under today's rules - this is a design change, not a code defect.

**Confirmed intended behavior (2026-05-28):** crafted / Spawnery / non-bred frogspawn should produce a **baseline** frog - **all stats at the floor (`FrogStats.STAT_MIN` = 1)**. Breeding (with its improvement chance) is the **only** way to climb above baseline; there is no random starter roll for crafted-egg frogs.

- **Expected:** a frog from crafted/non-bred frogspawn shows `1 / 1 / 1`.
- **Observed:** it shows a random `[1, 3]` roll (here `2 / 1 / 3`).
- **Fix:** in `ResourceFrog.finalizeSpawn()` (or `rollStarterStats`), set non-bred frogs to `STAT_MIN` across the board instead of rolling `[starterStatMin, starterStatMax]`. Bred frogs (inherited stats applied after `finalizeSpawn`) and disk-loaded frogs (`statsInitialized`) are unaffected. Update `docs/frog_breeding.md` and retire / repurpose the now-unused `starterStatMin` / `starterStatMax` config keys.

*Reported via the Sky Frogs pack with a Jade screenshot (frog at 2/1/3). Intent confirmed: baseline (all 1s). 2026-05-28.*

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

*Last updated: 2026-05-28 (logged: flowing Slime Milk displaces frogspawn / water sources / other milk sources; frogs drown in Slime Milk; bucketing and replacing Slime Milk resets spawns-remaining; Jade spawns-left readout doesn't update as the source depletes; primed frogspawn hatch delay should be deterministic; tadpole growth and frog breeding times should be deterministic; frogs from crafted frogspawn should start at baseline stats - reported via the Sky Frogs pack).*
