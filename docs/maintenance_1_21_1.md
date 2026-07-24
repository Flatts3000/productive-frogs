# Maintenance Runbook: the mc-1.21.1 line

> **Scope.** How updates land on the frozen **MC 1.21.1 / NeoForge 21.1.x** line after `main` moved to MC 26.1 (Productive Frogs 2.x). This branch is **hotfix-only**; the active line lives on `main`. Branch model background: `docs/port_mc_26_1.md` **on `main`** (this branch was frozen before that doc landed, so it does not exist here).

## Branch model

| Branch | Role | MC / NeoForge | Mod version |
|---|---|---|---|
| `main` | Active development | 26.1 / 26.1.x | `2.x` |
| `mc-1.21.1` | Frozen maintenance, hotfix-only | 1.21.1 / 21.1.x | `1.25.x` |

- `mc-1.21.1` is **protected to parity with `main`**: required `build` + `gameTest` status checks (strict), resolved review conversations, no force pushes, no deletion. All changes go via PR.
- There is **no shared mutable state** between the lines: `gradle.properties`, `CHANGELOG.md`, `build.gradle`, and `ci.yml` are per-branch. Never merge branches across the lines.
- Sky Frogs pins the `1.24.x` jar; this line exists to serve that pack and existing 1.21.1 players.

## What qualifies for this branch

**Yes:** crash fixes, dupe/loss bugs, data corruption, broken recipes/loot, license/metadata corrections, and (sparingly) player-facing papercuts with small, low-risk diffs.

**No:** new features, new variants or species, new integrations, refactors, dependency bumps (JEI/Jade/toolchain stay pinned unless a fix requires a bump), or anything that changes save/world format. Feature work targets `main` (26.1) only.

When in doubt, fix it on `main` only and leave this line alone.

### The v1.25.0 backport set (shipped 2026-07-24) - the one-time reopening, now spent

The line was reopened **once**, by maintainer decision (2026-07-23), for three **named** backports from the 2.x line and nothing else:

| Work | Epic | PR |
|---|---|---|
| Slime Milk Basin | #342 | #344 |
| Virtual Terrarium | #341 | #345 |
| Guidebook coverage audit + new entries | #343 | #346 |

All three shipped in **v1.25.0 "Second Helpings"** (#347). Because they are features they could not ship as a `1.24.x` patch, hence the minor bump. **The reopening is now spent: the line is back to hotfix-only** (`1.25.x` patches). This section stays as the record of why a `1.25` line exists at all - do not read it as license for further feature work, which still lands on `main` only.

The forward-flow rule was unaffected: these features already existed on `main`, so nothing flowed forward. Only **fixes** ever cross, and only old -> new.

## Hotfix procedure

1. **Branch off `mc-1.21.1`** (never off `main`): `git checkout -b fix/<slug> origin/mc-1.21.1`.
2. Fix, with a test where the surface allows (JUnit or GameTest). Conventional commit (`fix:` ...).
3. **Open a PR with base `mc-1.21.1`** (`gh pr create --base mc-1.21.1 ...`). CI runs `build` + `gameTest` on the 1.21.1 toolchain (Java 21) automatically; both are required to merge, and review conversations must be resolved.
4. Squash-merge. The fix is now on the line but unreleased; batching several fixes into one release is fine.

## Release procedure (v1.25.x patch)

From the `mc-1.21.1` branch, mirroring the standard release runbook (most recent: v1.25.0, 2026-07-24):

1. Bump `mod_version` in `gradle.properties` (patch bump: `1.25.0` -> `1.25.1`).
2. Add a `## v<mod_version>` section to `CHANGELOG.md` (the CurseForge changelog body is extracted by matching that heading).
3. Land the release commit via PR (`chore(release): v1.25.x - <name>`), then tag: `git tag v1.25.x && git push origin v1.25.x`.
4. Publish: `./gradlew publishCurseForge` from this branch. Its `build.gradle` already targets game version `1.21.1` and names the jar `productivefrogs-1.21.1-<version>.jar`; no flags needed. Reads `CURSEFORGE_API_KEY` from `.env`.

## Forward flow (old -> new only)

After a hotfix lands here, **check whether the bug exists on 26.1** and port it forward to `main` if so. Fixes flow `mc-1.21.1` -> `main`, never the reverse.

- Do not expect a clean cherry-pick: the lines diverged heavily (Identifier rename, ValueInput/ValueOutput I/O, transfer API, single-fluid milk). **Re-implement against the 26.1 APIs** and re-check whether vanilla 26.1 already fixed the bug upstream before porting a workaround (precedent: #276 was NOT ported because vanilla's `fudgePositionAfterSizeChange` supersedes it, while #277 was re-implemented; #282 -> #283 was re-applied with 26.1-side adjustments).
- Reference the original issue/PR number in the forward commit so the pairing is traceable.

## Dependabot

Dependabot config is read from the default branch (`main`) and does not target this line. That is intentional: a frozen branch takes no routine dependency bumps. If a security-critical bump is ever needed here, do it as a manual hotfix PR.

## Sunset

This line has no fixed end date. It sunsets when Sky Frogs migrates off 1.21.1 or activity drops to zero; when that happens, rename the branch under the `legacy/` prefix rather than deleting it (precedent: the `legacy/mc-1.21.11` branch preserves the original MC 1.21.11 line).
