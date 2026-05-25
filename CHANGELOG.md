# Changelog

## v1.1.0 (unreleased) — data-driven spawn eggs

> Builds on the v1.0.2 changes below. The next release that includes this work
> is a minor bump (v1.1.0), because it removes item IDs (see Breaking).

Implements code-review finding CR-9: the Resource Slime spawn egg is now a
single component-driven item instead of one item ID per variant. This is the
V1.1 enabler that makes adding a variant pure data (plus the one Slime Milk
`VARIANTS` Java edit that fluids inherently require).

### Breaking

- The 12 per-variant spawn-egg item IDs (`productivefrogs:iron_slime_spawn_egg`,
  `..._copper_...`, ...) are **removed** and replaced by a single
  `productivefrogs:resource_slime_spawn_egg` whose variant rides in the
  `slime_variant` data component. **Migration:** these are creative-only items;
  any stashed in an existing world disappear on load (survival play never grants
  them). Use `/give @s productivefrogs:resource_slime_spawn_egg[...]` or the
  creative tab to get the new form.

### Changed

- Creative tab, JEI subtypes, and inventory tint now enumerate variants from the
  `slime_variant` datapack registry, so a new variant's spawn egg appears with no
  code change. (Variant-driven creative entries populate in-world; like vanilla's
  enchanted-book entries they are empty on the title screen until a world loads.)
- Adding a variant no longer needs a spawn-egg Java edit; only the Slime Milk
  `VARIANTS` entry remains. Docs (`architecture.md`, `versioning.md`,
  `v1_1_scope.md`) updated.

## v1.0.2 (unreleased)

Internal hardening + cleanup patch from the 2026-05-24 code review
(`docs/code_review_2026_05_24.md`). **No world migration, no API or data
changes.** One small player-facing tweak; the rest is invisible in play.

### Player-facing

- Placing a Frog Egg now plays the bottle-empty sound instead of the frogspawn
  hatch sound (the hatch sound is for tadpoles emerging, not for placing the egg).
- Slime Milker block entities now sync their contents to the client, so info-HUD
  mods (Jade/WTHIT) read the inventory + cook progress without opening the GUI.

### Robustness

- The `Category` network codec rejects out-of-range ordinals cleanly instead of
  crashing the decode thread (defends the item data-component sync path).
- Slime Milker cook progress is clamped on load; `slime_variant` colours are
  range-checked; a warning fires if `minSpawnIntervalTicks > maxSpawnIntervalTicks`.

### Internal

- A modded `parent_species` JSON now wires a slime into **both** infusion and
  split-discovery (infusion previously used a hardcoded class check).
- Six near-identical parent-species renderers collapsed into one; dead code and
  stale post-V1.5 docs swept. Net ~350 fewer lines. Build + all 50 GameTests green.

## v1.0.1 — 2026-05-24

Visual-polish patch. **No behavior changes.** No world migration, no API surface change. Production loop, drops, AI, tints, infusion semantics, JEI subtypes: unchanged.

### What changed visually

- **Resource Slimes now have an actual vanilla resource block rendered inside the translucent shell.** Pre-v1.0.1, the inner cube was textured with a 6x6-downsampled copy of the block image stamped into a per-variant atlas (blurry at large slime sizes). v1.0.1 draws the real block model (iron block, copper block, ...) so the interior is the genuine vanilla block at native resolution, with vanilla's own UVs and mipmaps.
- Same treatment for the 6 parent species (Bog/Cave/Geode/Tide/Infernal/Void) - each shows a themed vanilla block inside (moss, stone, amethyst, prismarine, netherrack, end stone).
- The slime's face (eyes + mouth) and tinted translucent shell are unchanged.

### How

A new `ResourceSlimeInnerBlockLayer` renders the resource block via `BlockRenderDispatcher` in the volume vanilla's 6x6x6 inner cube occupies. The base renderer still draws the vanilla inner model (cube + eyes + mouth) and the tinted outer shell, both unchanged from v1.0 - the eyes live on the vanilla inner body layer, so keeping that model preserves the face. The opaque block covers the inner cube's body; the eyes sit proud of the cube's front face and stay visible.

### Data layer

- New optional `inner_block` field on `SlimeVariant` and `ParentSpeciesEntry` codecs. Format: a plain vanilla block id (e.g. `minecraft:iron_block`), resolved to its default block state at render time.
- Fully data-driven, parallel to the existing tint config: Resource Slime variants read `inner_block` from the variant JSON (like `primary_color`); parent species read it from their `parent_species` registry entry. A modpack can repoint any slime's interior block by editing JSON, no code change.
- All 12 shipped variant JSONs populated; all 6 parent_species JSONs populated.
- Removed the pre-v1.0.1 per-variant atlas `texture` field from `SlimeVariant` (the renderer no longer reads it; the outer-shell atlas is per-category).

### Asset cleanup

- 12 per-variant `<variant>_resource_slime.png` atlas PNGs deleted (the inner content is now a rendered block; the outer-shell atlas is per-category).
- `scripts/generate_variant_slime_textures.ps1` deleted (the textures it produced are gone).

### Fallback

A variant JSON without an `inner_block` field (typo, modded block from an absent mod) skips the inner-block render pass - the slime renders with its shell, eyes, and inner cube but no interior block.

### Modpack-author note

No migration. No third-party variants are known to ship between v1.0 and v1.0.1; if any exist, they'll continue to load (`inner_block` is optional) and just render without an interior block until a JSON edit adds the field.

---

## v1.0.0 — 2026-05-24

First public release. **Minecraft 1.21.1 / NeoForge 21.1.230.**

### Production loop
- Six parent slime species, each in a themed biome: **Bog** (swamps), **Cave** (dripstone / deep dark / lush caves), **Geode** (mountain peaks), **Tide** (oceans), **Infernal** (nether), **Void** (end islands).
- Right-click a parent slime with a variant primer item (iron ingot, copper ingot, diamond, prismarine shard, magma cream, ender pearl, etc.) to convert it into a **variant Resource Slime**.
- Six matching frogs — Bog Frog, Cave Frog, Geode Frog, Tide Frog, Infernal Frog, Void Frog. Each eats only Resource Slimes of its species.
- Frog kills a Resource Slime → drops a **Configurable Froglight** stamped with the slime's variant.
- Smelt the Froglight in a vanilla furnace → get the original resource back.

### Production block
- **Slime Milker** — furnace-style appliance. Drop a variant Slime Bucket in the input slot, get the matching Slime Milk bucket out 100 ticks later. Hopper-compatible.
- **Slime Milk source blocks** — place in world to spawn the source variant slime periodically. Self-sustaining production loop.

### Item collection
- **Bottle of Frog Eggs** — right-click vanilla frogspawn with a glass bottle to bottle it. Prime the bottle with a variant primer to assign a species. Place on water to hatch into tadpoles.
- **Primed Frog Egg blocks** — six species. Place on water; hatches into species tadpoles after a delay.
- **Resource Tadpole Bucket / Slime Bucket** — capture / release with category and variant preserved.
- 30 spawn eggs (6 parent species + 6 frogs + 6 tadpoles + 12 variant slimes).

### Integrations
- **JEI Information pages** on every PF item — explains the production role, who hunts what, what smelts to what. Dynamically generated from the SlimeVariant datapack registry.
- **Jade** — drop-in compatible (no plugin code needed); shows species + variant in the in-world entity tooltip.

### Datapack-driven
- Add new slime variants without recompiling: drop a JSON in `data/<modpack>/productivefrogs/slime_variant/<name>.json` (the `primer_item` field is the exact 1:1 match for infusion), an inner-cube texture, a smelting recipe, and a lang entry for the display name. JEI Information pages auto-extend to cover the new entry.
- Cross-mod variants (Mekanism, Create, Thermal, etc.) gated by `mod_loaded` neoforge conditions — see `docs/cross_mod_compat.md`.

### Known limitations
- One end-to-end AI tongue gametest (`frogTongueAiPathDropsConfigurableFroglight`) is timing-flaky in CI and runs as `required = false`. The drop path is independently covered by `matchingFrogKillDropsConfigurableFroglight` (manual hurt damage, no AI).
- No automation (hoppers, power, pipes, multiblocks) in v1.0 — that's the V2 scope; v1.0 is the playable foundation + hand-operated appliance layer.

### Modpack note
This is a fresh release on the MC 1.21.1 line. There is no migration path from earlier 1.21.11 / 1.21.x development snapshots; modpack maintainers carrying those should regenerate worlds.
