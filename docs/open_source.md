# Open Source Structure

Productive Frogs is open source, hosted on GitHub. This document captures the OSS file layout and conventions.

## License

**MIT License.** Chosen because:

- Most permissive — modpack authors can bundle and redistribute trivially.
- Standard for Minecraft content mods.
- Compatible with all major modpack license requirements.
- Trivial to read; no surprises for contributors.

The full license text lives in `LICENSE` at the repo root. Copyright header: `Copyright (c) 2026 Flatts3000`.

## Required Files

| File | Status | Purpose |
|---|---|---|
| `LICENSE` | required | MIT license text + copyright |
| `README.md` | exists | Project overview + docs index |
| `CONTRIBUTING.md` | required | How to file issues / submit PRs / dev setup |
| `CODE_OF_CONDUCT.md` | required | Contributor Covenant v2.1 |
| `SECURITY.md` | recommended | Vulnerability disclosure process |
| `CHANGELOG.md` | recommended | Human-readable version history (or rely on GH Releases) |
| `.gitignore` | required | Excludes build artifacts, IDE files, Terraform state |
| `.gitattributes` | recommended | Line-ending normalization for cross-platform contributors |

## GitHub-Specific Files

All under `.github/`:

| Path | Purpose |
|---|---|
| `.github/ISSUE_TEMPLATE/bug_report.md` | Structured bug reports |
| `.github/ISSUE_TEMPLATE/feature_request.md` | Feature proposals |
| `.github/ISSUE_TEMPLATE/mod_compat.md` | "X mod doesn't work right with Productive Frogs" |
| `.github/ISSUE_TEMPLATE/config.yml` | Disables blank issues; routes users to discussions for questions |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR checklist (tests pass, docs updated, CHANGELOG entry) |
| `.github/dependabot.yml` | Auto-PRs for Gradle dep updates |
| `.github/FUNDING.yml` | Sponsor links (optional, can omit) |
| `.github/workflows/ci.yml` | Two required jobs (`build` + `gameTest`) on every push and PR |

## CI Pipeline (GitHub Actions)

`ci.yml` is the only workflow. It runs two independent jobs on every push and PR, both required by `main` branch protection:

- **`build`** — checkout, JDK 21 (Temurin), Gradle setup + wrapper validation, then `./gradlew build` (compile + JUnit suite + assemble jar), and uploads the `.jar` artifact.
- **`gameTest`** — same setup, then `./gradlew runGameTestServer` (a headless server that runs every in-world GameTest, exits non-zero on failure). Independent of `build` so the unit-test and in-world-test layers report separately.

## Release (manual, not CI)

There is **no** release workflow. Publishing is a manual local step:

```
./gradlew publishCurseForge
```

This Gradle task (via the `net.darkhax.curseforgegradle` plugin) uploads `build/libs/*.jar` to CurseForge project **1552728**, with the per-version section of `CHANGELOG.md` as the file changelog, tagged MC 1.21.1 / NeoForge / Java 21. It depends on `build`, so a clean build runs first.

The API key is read locally (first hit wins): `CURSEFORGE_API_KEY` from `.env` at the repo root (gitignored), then the `CURSEFORGE_API_KEY` env var, then the `cfApiToken` Gradle property. It is **not** a CI Actions secret - releases never run in GitHub Actions.

Modrinth distribution is intentionally omitted — the FTB ecosystem requires CurseForge-only distribution, and Productive Frogs targets the FTB modpack audience. Players who want the mod can grab it from CurseForge or the GitHub Releases attachment.

## Contributor Conventions

Spelled out in `CONTRIBUTING.md`:

- **Branching**: `main` is protected; all changes via PR from a feature branch
- **Commits**: conventional commits format (`feat:`, `fix:`, `refactor:`, etc.)
- **Tests**: any new mechanic should ship with at least one game-test scenario or unit test
- **Docs**: design changes should update the relevant `/docs` markdown in the same PR
- **Java style**: 4-space indent, no wildcard imports, Google Java Format compatible
- **Issue triage**: maintainer applies labels; community can comment to suggest scope/priority
- **Slowness OK**: this is an OSS hobby project; replies may take days
