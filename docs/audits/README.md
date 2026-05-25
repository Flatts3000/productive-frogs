# Audits

Archived audit findings, preserved for posterity. Each audit is a point-in-time snapshot; the
codebase moves on, so treat file paths and line numbers as accurate only as of the audit date.

## Code review - 2026-05-24 (at v1.0.1, commit 92d920e)

Comprehensive review run with six parallel specialist reviewers. Every CRITICAL and HIGH claim was
verified against the actual code before being trusted.

- [Consolidated synthesis + remediation plan](../code_review_2026_05_24.md) - START HERE. Deduplicated,
  cross-validated findings (IDs CR-N) grouped by severity, with suggested remediation tranches.
- [Architecture](code_review_2026_05_24_architecture.md) - design coherence, coupling, V1.1 scalability (A-N).
- [Registry / Events / Data](code_review_2026_05_24_registry_events_data.md) - wiring, lifecycle, codecs (R-N).
- [Content](code_review_2026_05_24_content.md) - entities, AI, blocks, block entities, items, menu (C-N).
- [Client Rendering](code_review_2026_05_24_client_rendering.md) - renderers, layers, JEI, screen (CL-N).
- [Dead Code and Duplication](code_review_2026_05_24_dead_code_duplication.md) - Grep-verified (D-N).
- [Security / Trust Boundaries](code_review_2026_05_24_security.md) - decode/deserialization safety (S-N).

Two findings were verified FALSE and rejected (recorded in the per-specialist docs so they are not
re-raised):

- "Wrong event bus, mod is broken" (R-1) - NeoForge 21.1.x `@EventBusSubscriber` has no `bus()`
  parameter and auto-routes by `IModBusEvent`; the mod runs and 50 GameTests pass.
- "ResourceSlime double-split" (C-1) - `setSize(1, false)` before `super.remove()` suppresses
  vanilla's split; the split-discovery GameTests confirm correct child count and category.
