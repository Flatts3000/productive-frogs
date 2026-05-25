# Code Review Audit - Architecture (2026-05-24)

Specialist: architecture reviewer (Opus). Scope: whole source tree, design coherence,
coupling, V1.1 scalability. Raw findings preserved for posterity. See
`docs/code_review_2026_05_24.md` for the cross-validated, deduplicated synthesis and the
remediation plan. IDs here (A-N) are this reviewer's own numbering.

Reviewed the full source tree against the design docs (`architecture.md`, `design_overview.md`,
`versioning.md`, `species_as_category_redesign.md`) and `CLAUDE.md`. The codebase is
well-structured overall: clean DeferredRegister wiring, a sound datapack-registry spine, good
separation of `content` (behavior) from `registry` (wiring), and disciplined V1/V2 scope
adherence (no power/pipe/multiblock creep - the Slime Milker is a legitimate single-block
appliance).

## CRITICAL

None. No defects that break correctness, security, or data integrity.

## HIGH

### A-1: "Add a variant = one JSON, no Java" is violated by hardcoded parallel lists
Files: `registry/PFItems.java:336-366` (`buildSlimeVariantSpawnEggs`, 12-entry `VariantSpec[]`
with hardcoded variant->category mapping), `registry/PFFluidTypes.java:54-61` (`VARIANTS` list
of 14), `data/.../slime_variant/*.json` (the 12 files that are the supposed source of truth).

`architecture.md` states the guiding principle: "Slime variants are data, not code." But adding
a variant today requires Java edits in at least two places: a `VariantSpec` row in
`buildSlimeVariantSpawnEggs` (with a hand-copied category that duplicates the JSON), and a
`VARIANTS` entry to get a Slime Milk fluid. So V1.1's "22 new variants, JSON-only" claim is not
achievable without 22 spawn-egg rows + 22 fluid entries + the category duplication. The
variant->category mapping now lives in three places and can drift, producing a spawn egg whose
tint/JEI category disagrees with the entity's registry-resolved category.

Recommendation: ship ONE `resource_slime_spawn_egg` item whose variant is a data component
(exactly like `configurable_froglight` and `FrogEggItem` already do), reusing the JEI subtyping
that already handles "one item id, N component values." That deletes the entire `VariantSpec[]`
and its category duplication. Fluids genuinely must register at mod-init, so keep the static
`VARIANTS` superset but document it and add a startup validation that every shipped variant JSON
has a matching entry. At minimum, correct the V1.1 doc so the next release is not planned against
a false constraint.

## MEDIUM

### A-2: Two mechanisms resolve "parent species -> category" for the same operation
Files: `event/SlimeInfusionHandler.java:133-142` (`instanceof` dispatch on the 6 PF subclasses)
vs `event/SlimeSplitDiscoveryHandler.java:153-170` (`PARENT_SPECIES` datapack registry lookup).

Both answer "what category does this parent slime belong to?" with incompatible mechanisms. A
modpack that adds a modded parent slime via a `parent_species` JSON works for split-discovery but
silently does not work for infusion - the `instanceof` chain returns null for any non-PF subclass.
The split handler's javadoc calls the old `instanceof` approach a "footgun" that was replaced, yet
infusion still uses it.

Recommendation: route `resolveParentSpecies` through the same registry lookup (shared
`categoryForEntityType(Mob, Level)` helper). The 6-entry scan on a right-click path is negligible.

### A-3: Six near-identical parent-species classes + six near-identical renderers
Files: `content/entity/{Bog,Cave,Geode,Tide,Infernal,Void}Slime.java` (6 classes ~50 lines each,
differing only in one `Category.X.tintRgb()` in `getParticleType()`),
`client/renderer/{...}SlimeRenderer.java` (6 renderers differing only in a texture path and tint),
`registry/PFEntities.java:91-182` (6 near-identical EntityType registrations).

This is a genuine tension with the "six categories fixed" decision, so MEDIUM not HIGH. The entity
CLASSES probably must stay distinct types - per-category biome spawn rules key on EntityType
through `neoforge:add_spawns`, and the six are a fixed set. But the RENDERERS have no such
constraint and could be a single parameterized `ParentSlimeRenderer(texture, outerTint)`. The
`getParticleType()` RGB->Vector3f conversion is copy-pasted 7 times.

Recommendation: keep the 6 entity types; collapse the 6 renderers into one parameterized class
and extract the particle-tint conversion into a shared helper.

### A-4: ResourceSlime carries Category and variant id as separate synced fields with manual sync
File: `content/entity/ResourceSlime.java:53-126, 220-240, 354-368`.

Two synced fields (`DATA_CATEGORY` int + `DATA_VARIANT_ID` string); every mutation path must set
them in the right order (category first as fallback, then variant, so `setVariant`'s registry
lookup wins). When a variant is present, category is fully derivable from it, so the second field
is denormalized state that can desync. Defensible as a perf fast-path (avoids a registry lookup in
`getCategory()` on a hot path), but the cost is four call sites carrying an implicit ordering
contract.

Recommendation: centralize the invariant in a single private `applyVariant(id)` so callers stop
carrying the ordering contract, or document the invariant once on the field.

### A-5: `instanceof` chain in resolveParentSpecies orders species out of enum order
File: `event/SlimeInfusionHandler.java:135-141`. Minor cohesion nit compounding A-2; the order
(Bog, Cave, Geode, Tide, Void, Infernal) differs from `Category` enum order. Harmless functionally;
resolved by folding into the registry lookup (A-2).

## LOW

### A-6: Pervasive documentation drift after the V1.5 rename
Representative files: `data/ParentSpeciesEntry.java:15-20` (dead `@link Category#METALLIC` etc.),
`registry/PFEntities.java:84-141` and `CaveSlime/GeodeSlime` (stale "instanceof" descriptions),
`content/entity/ResourceSlime.java:33,38,82-87` and `ResourceFrog.java:69-70` ("Fall back to
METALLIC" where code returns `Category.BOG`), and a CLAUDE.md "Item tinting" paragraph that
describes 1.21.4+ behavior while the code correctly uses 1.21.1 `RegisterColorHandlersEvent`.
The code is correct; the docs mislead. Mechanical `docs:` sweep.

### A-7: PFConfig is COMMON-type but at least one value is server-authoritative
File: `PFConfig.java:13-19, 78-83`. `DISCOVERY_CHANCE_PER_OFFSPRING` and the depletion/interval
values drive server-side behavior. COMMON is internally consistent for V1 (clients do not read
them), and the class javadoc shows it was a considered decision. Revisit COMMON-vs-SERVER when V2
adds client-visible production state. Not a V1 defect.

## What is strong (do not change)

- Datapack-registry spine is correct and idiomatic; the v1.0.1 `inner_block` field extends cleanly;
  network codec passed so client rendering gets variant data for free.
- Registry/lifecycle ordering is correct with constraints documented inline (FluidTypes before
  Fluids; BlockEntities/MenuTypes after Blocks; the forward-reference closure in
  `PFFluids.buildFluids`).
- Two-layer category enforcement (sensor + death-event) is preserved per design.
- V1/V2 scope discipline holds; the Slime Milker is a legitimate single-block appliance.
- Package layering is clean with no cycles; compat is JSON-only with no `compat/` Java package.
- The JEI plugin is fully dynamic (walks the variant registry), the one surface that already
  honors "data, not code" - the model A-1 wants the spawn eggs to match.

## Priority order
1. A-1 (reconcile variant lists with the JSON-only principle before V1.1).
2. A-2 (unify parent-species resolution).
3. A-3 (collapse renderers, extract particle helper).
4. A-4 (centralize the category/variant invariant).
5. A-6 (doc sweep).
6. A-7 (COMMON vs SERVER at V2).
