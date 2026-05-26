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

*No open bugs currently.* Recently resolved (see the [archive](./known_issues_archive.md)): empty-bucket slime capture, and canonical species ordering across tabs / JEI / recipe book. By-design V1 limitations are listed below.

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
V1 ships no in-house crushing block. The 2× yield on Cave-species (ore / metal) Froglights is unlocked by installing Create, Mekanism, or Thermal (compat recipes still pending — see Cross-Mod section below).

### 🔵 No drop-collection block
Use vanilla hoppers under the frog pen to collect Froglight item entities. A custom collection block is V2.

---

## Cross-Mod Compat caveats

### 🔵 Crush recipes (Create / Mekanism / Thermal) not yet shipped
Design ([farming.md §Cross-Mod](./farming.md)) calls for conditional `mod_loaded` JSON recipes converting 1 Cave-species (ore / metal) Froglight → 2 dust / crushed material. Not yet shipped — pending a multi-mod test environment that can validate the cross-mod recipe shapes. Players can still smelt directly for 1× yield in the meantime.

A `crushable` item tag will be created and populated alongside the recipes (none exists in the repo yet).

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

*Last updated: 2026-05-25 (staleness pass: corrected pre-V1.5 "metallic" Froglight references to Cave-species naming, and removed the false claim that a `crushable` tag already exists - it does not yet).*
