# Open Source Structure

Productive Frogs is open source, hosted on GitHub. This document captures the OSS file layout and conventions.

## License

**MIT License.** Chosen because:

- Most permissive — modpack authors can bundle and redistribute trivially.
- Standard for Minecraft content mods.
- Compatible with all major modpack license requirements.
- Trivial to read; no surprises for contributors.

The full license text lives in `LICENSE` at the repo root. Copyright header: `Copyright (c) 2026 Flatts`.

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
| `.github/workflows/ci.yml` | Build + test on every push and PR |
| `.github/workflows/release.yml` | Publish to CurseForge on tag push (when ready) |

## CI Pipeline (GitHub Actions)

`ci.yml` runs on every push and PR:

1. Checkout code
2. Set up JDK 21 (Temurin)
3. Set up Gradle cache (key: `gradle-${{ hashFiles('**/*.gradle', 'gradle/wrapper/gradle-wrapper.properties') }}`)
4. `./gradlew build` — full build + tests
5. Upload build artifact (`.jar`) for review

`release.yml` runs on tag push (`v*.*.*`):

1. Same build steps
2. Verify tag matches `mod_version` in `gradle.properties`
3. Publish to CurseForge via [Kir-Antipov/mc-publish](https://github.com/Kir-Antipov/mc-publish)
4. Create GitHub Release with the jar attached and CHANGELOG section as body

Modrinth distribution is intentionally omitted — the FTB ecosystem requires CurseForge-only distribution, and Productive Frogs targets the FTB modpack audience. Players who want the mod can grab it from CurseForge or the GitHub Releases attachment.

Secret required: `CURSEFORGE_TOKEN`. Set via `gh secret set` from the sibling `infra/setup.ps1` script (see [infrastructure.md](./infrastructure.md)).

## Contributor Conventions

Spelled out in `CONTRIBUTING.md`:

- **Branching**: `main` is protected; all changes via PR from a feature branch
- **Commits**: conventional commits format (`feat:`, `fix:`, `refactor:`, etc.)
- **Tests**: any new mechanic should ship with at least one game-test scenario or unit test
- **Docs**: design changes should update the relevant `/docs` markdown in the same PR
- **Java style**: 4-space indent, no wildcard imports, Google Java Format compatible
- **Issue triage**: maintainer applies labels; community can comment to suggest scope/priority
- **Slowness OK**: this is an OSS hobby project; replies may take days
