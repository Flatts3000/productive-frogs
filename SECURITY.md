# Security Policy

## Supported Versions

Productive Frogs is pre-release; only the latest commit on `main` is supported. Once stable releases are published, this section will be updated with a version table.

## Reporting a Vulnerability

If you discover a security vulnerability in Productive Frogs, **please do not open a public issue.** Instead, use one of the following private channels:

1. **GitHub Security Advisories** (preferred): visit the repo's [Security tab](https://github.com/Flatts3000/productive-frogs/security/advisories/new) and submit a draft advisory. This is the canonical channel and routes directly to the maintainer.
2. **Direct contact**: message the maintainer via the GitHub profile at [@Flatts3000](https://github.com/Flatts3000).

## What Counts

For a Minecraft mod, the realistic threat surface is small but non-empty:

- **Server crashes / denial of service** triggered by specific item / block / entity states the mod adds.
- **Resource exhaustion** (memory, CPU) caused by mod-introduced loops or unbounded data structures.
- **Datapack injection** that bypasses the variant-validation chain to load malformed data.
- **Cross-mod compat issues** where another mod's input causes unsafe behavior in our event handlers.

If you're unsure whether something qualifies, report it privately and we'll classify together.

## Response Timeline

- **Acknowledgement**: within 7 days of receiving the report.
- **Initial assessment**: within 14 days.
- **Fix + disclosure**: timing varies by severity. Critical issues get a hotfix release; lower-severity issues land in the next regular release.

This is a hobby OSS project - timelines are best-effort, not contractual.

## Disclosure

We follow coordinated disclosure: report privately, we work on the fix, and we publish the advisory + fix together. We'll credit the reporter unless you request anonymity.
