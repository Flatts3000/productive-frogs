# Infrastructure

The Productive Frogs GitHub repository is configured via an idempotent **gh CLI script** in a sibling folder at `F:\minecraft-repos\infra\`. Operational details (commands, setup, day-to-day operations) live at `infra/README.md`. This doc covers the higher-level architecture.

## Tooling

| Tool | Version target |
|---|---|
| gh CLI | 2.0+ (latest stable) |
| Auth | Existing gh CLI session (no PAT needed) |
| Script language | PowerShell (Windows-native; Bash version can be added if needed) |
| Location | `F:\minecraft-repos\infra\` (sibling to `productive-frogs/`) |

## Why gh CLI instead of Terraform

For a solo OSS project, the gh CLI approach is simpler:

- **No PAT to manage.** `gh` reuses the existing authenticated session from `gh auth login`.
- **No state file** to back up.
- **No Terraform installation** required (gh CLI is much more universally available).
- **Imperative, idempotent script** — easier to read line-by-line, no `terraform plan` review step needed for trivial changes.

The trade-off: no declarative drift detection. For a single-operator project where config changes are rare and intentional, this is fine. Migration to Terraform is straightforward later if contributor scale demands it.

## Layout

```
F:\minecraft-repos\
├── infra/                  # ← repo setup script lives here, OUTSIDE the project repo
│   ├── README.md           — operational playbook
│   ├── .gitignore          — excludes secrets / OS files
│   └── setup.ps1           — idempotent PowerShell script
│
└── productive-frogs/       # ← project repo (mod code, design docs)
    ├── README.md
    ├── docs/
    └── (Java, Gradle, etc.)
```

## What `setup.ps1` manages

1. **Repository creation** — `gh repo create` (skipped if exists).
2. **Repository settings** — description, visibility, topics (`minecraft`, `neoforge-mod`, etc.), features (issues + discussions on, wiki + projects off), merge strategy (squash-only, delete-branch-on-merge, auto-merge enabled).
3. **Branch protection on `main`** — required status checks (`build` **and** `gameTest`, the live repo requires both), no force-pushes, no deletions, no enforce-admins (owner can hotfix), no PR reviews required (solo OSS — re-enable when contributors arrive). (Note: `setup.ps1`'s required-contexts list still names only `build` and also needs the second context added.)
4. **Labels** — 10 standard OSS labels + 6 per-species labels (`category/cave`, `category/geode`, `category/bog`, `category/tide`, `category/infernal`, `category/void`), one per parent slime species. (Note: `setup.ps1` still defines the old abstract label names (`category/metallic`, `category/mineral`, `category/gem`, `category/aquatic`, `category/infernal`, `category/arcane`) and also needs updating.)
5. **Actions secrets** — `CURSEFORGE_TOKEN`, conditional on `$env:PF_CURSEFORGE_TOKEN` being set before the script runs. (Modrinth distribution is intentionally not supported — FTB ecosystem requires CurseForge-only distribution; Productive Frogs targets the FTB modpack audience.)

## What `setup.ps1` does NOT manage

- `.github/workflows/*.yml` — workflow YAML lives in the repo content (git), not in the setup script.
- README, CONTRIBUTING, CODE_OF_CONDUCT, LICENSE — repo content.
- Code (Java, Gradle, etc.) — repo content.

The dividing line: **repository configuration goes in `setup.ps1`; repository content goes in git.**

## Bootstrapping (one-time)

Because the setup script lives **outside** the productive-frogs repo, there's no chicken-and-egg. The bootstrap order is straightforward:

1. **Authenticate gh CLI** (one-time per machine):
   ```powershell
   gh auth login
   ```
2. **Edit `setup.ps1`** — set `$GitHubOwner` to your real GitHub handle.
3. **Run the script** to create the empty repo:
   ```powershell
   cd F:\minecraft-repos\infra
   ./setup.ps1
   ```
   Branch protection will be skipped on this first run because `main` doesn't exist yet.
4. **Push the productive-frogs folder** into the new repo:
   ```powershell
   cd ..\productive-frogs
   git init -b main
   git add .
   git commit -m "Initial commit: design docs"
   git remote add origin git@github.com:<your-handle>/productive-frogs.git
   git push -u origin main
   ```
5. **Re-run the script** to apply branch protection now that `main` exists:
   ```powershell
   cd ..\infra
   ./setup.ps1
   ```

Day-to-day, you only re-run the script when you change repo settings/labels/secrets. Code changes are normal git workflow.

## Auth flow

The script does not handle authentication itself — it relies on the gh CLI's existing session. The flow:

```
You run: gh auth login              (one-time, browser-based)
                ↓
gh CLI stores credentials in OS keychain / config
                ↓
You run: ./setup.ps1
                ↓
gh CLI commands inside the script use the stored credentials
                ↓
GitHub API calls authenticated as your account
```

No tokens are written to disk by the script. No environment variables for the GitHub token. The release-workflow secret (`CURSEFORGE_TOKEN`) DOES use an env var (`$env:PF_CURSEFORGE_TOKEN`) because it's passed *into* the repo's Actions secrets — but that env var is transient and never persisted.
