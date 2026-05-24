# Changelog

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
