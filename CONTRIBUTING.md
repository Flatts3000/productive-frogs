# Contributing to Productive Frogs

Thanks for your interest in contributing! This document covers how to file issues, submit pull requests, and what to expect from the project's maintenance pace.

## Reporting Issues

- **Bugs**: open an issue using the **Bug Report** template. Include your Minecraft version, NeoForge version, mod list (or modpack name), and steps to reproduce.
- **Mod compatibility problems**: use the **Mod Compatibility** template. Tell us which mod and what specifically goes wrong.
- **Feature ideas**: use the **Feature Request** template. Frame it as the problem you're trying to solve, not just the solution you want.
- **General questions**: use [GitHub Discussions](https://github.com/Flatts3000/productive-frogs/discussions) rather than the issue tracker.

Don't open issues for security vulnerabilities — see [SECURITY.md](./SECURITY.md).

## Submitting Pull Requests

### Branching

- `main` is protected — all changes land via PR.
- Create a feature branch from `main` named like `feat/new-resource-slime` or `fix/milker-uses-wrong-bucket`.
- Don't push directly to `main`.

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short subject>

<body — explain WHY, not what>
```

Types we use:
- `feat:` — new feature or content
- `fix:` — bug fix
- `refactor:` — code restructuring with no behavior change
- `docs:` — documentation only
- `test:` — adding or updating tests
- `chore:` — tooling, build config, infrastructure
- `ci:` — CI/CD changes
- `perf:` — performance improvements

One logical change per commit. Squash trivially-related work locally before PR.

### Code Quality Expectations

- **Tests**: new mechanics ship with at least one test (unit or game-test) demonstrating the mechanic works. Bug fixes ship with a regression test.
- **Docs**: design changes should update the relevant `/docs/*.md` file in the same PR. Lock decisions in `docs/open_questions.md` get marked DECIDED with a brief rationale.
- **No silent dead code**: if a feature is incomplete or behind a feature flag, say so in the PR description.
- **Cross-mod compat is JSON**: don't add hard dependencies on other mods. Use common tags + `neoforge:conditions → mod_loaded` patterns. See `docs/cross_mod_compat.md`.

### Java Style

- Java 21, target compatibility 21.
- 4-space indent, no tabs.
- No wildcard imports.
- Imports ordered: `java.*`, `javax.*`, third-party, `net.minecraft.*` / `net.neoforged.*`, `com.flatts.productivefrogs.*`.
- Prefer immutable data structures; use Records for value types.
- `null` is fair game — annotate ambiguous returns with `@Nullable` (NeoForge ships JetBrains annotations).

### Before You Open a PR

1. `./gradlew build` passes locally.
2. CI green on your branch.
3. New code has tests covering the happy path and at least one edge case.
4. Docs updated where relevant.
5. PR description explains the **why** — the **what** is in the diff.

### Review

- The maintainer reviews when bandwidth permits — this is an OSS hobby project, expect days, not hours.
- Review feedback is collaborative; address comments or push back if you disagree. Both are fine.
- Approved + green CI + no unresolved threads → maintainer squash-merges.

## Adding a New Resource Slime

The mod is data-driven. Most new slimes need no Java code:

1. Add a JSON to `src/main/resources/data/productivefrogs/slime_variant/<name>.json` with the standard schema (see `docs/architecture.md`).
2. Add the variant's ID to its category tag at `src/main/resources/data/productivefrogs/tags/slime_category/<category>.json`.
3. Add a loot table at `src/main/resources/data/productivefrogs/loot_tables/entities/slime/<name>.json`.
4. If the variant is from a specific other mod, wrap the JSONs in `neoforge:conditions → mod_loaded`.
5. Run `./gradlew runData` to regenerate auto-data if any.
6. Open a PR with the new files plus an entry in `CHANGELOG.md`.

No Java changes needed for most contributions of this shape.

## What We Probably Won't Accept

- Adding hard dependencies on other mods.
- Multi-loader (Fabric) support — Productive Frogs is NeoForge-only by design. See `docs/architecture.md`.
- Renaming the canonical primer items per category — these are part of the public API now.
- Removing the per-variant `color_rgb` JSON field — modpack authors rely on it.

## Maintainer Cadence

This is a hobby OSS project. Realistic expectations:

- Issue triage: within ~1 week of opening.
- PR review: ~1 week, sometimes longer.
- Releases: irregular, driven by significant feature batches or upstream NeoForge updates.

If something is urgent (security, major upstream break), ping the maintainer in the relevant issue/PR; they'll prioritize.

Thanks again for contributing! 🐸
