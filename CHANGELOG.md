# Changelog

## v1.0.1 — 2026-05-24

Visual-polish patch. **No behavior changes.** No world migration, no API surface change. Production loop, drops, AI, tints, infusion semantics, JEI subtypes: unchanged.

### What changed visually

- **Resource Slime inner cube now displays the variant's vanilla resource block texture at native 16x16 per face.** Pre-v1.0.1, the renderer downsampled the vanilla 16x16 block texture to 6x6 (matching vanilla `SlimeModel`'s per-face UV resolution) and stamped it into a per-variant atlas. At slime size 4 the 6x6 source visibly blurred at 60+ screen pixels; v1.0.1 binds the vanilla block PNG directly so what you see inside the slime IS the vanilla block, byte-identical.
- Same treatment for the 6 parent species (Bog/Cave/Geode/Tide/Infernal/Void) - each now displays a themed vanilla block (moss, stone, amethyst, prismarine, netherrack, end stone) inside its translucent shell.

### How

Two-pass entity rendering. The slime's outer translucent shell + eyes + mouth still bind the existing per-category atlas (unchanged from v1.0). The inner cube binds to the variant's `inner_texture` field directly (e.g. `minecraft:textures/block/iron_block.png`). A new `ResourceSlimeInnerModel` swaps vanilla `SlimeModel`'s 6x6-UV inner cube for a 16x16-UV inner cube scaled down to vanilla's visual size, so the world-space geometry is identical while each face's UV spans the full bound texture.

### Data layer

- New optional `inner_texture` field on `SlimeVariant` and `ParentSpeciesEntry` codecs. Format: full texture path with namespace + `textures/` prefix + `.png` suffix (e.g. `minecraft:textures/block/iron_block.png`).
- All 12 shipped variant JSONs populated.
- All 6 parent_species JSONs populated.

### Asset cleanup

- 12 per-variant `<variant>_resource_slime.png` atlas PNGs deleted (no longer needed; the outer-shell atlas is per-category, the inner cube binds directly to vanilla block PNGs).
- `scripts/generate_variant_slime_textures.ps1` deleted (the textures it produced are gone).

### Fallback

Variant JSON without an `inner_texture` field (typo, modded block from an absent mod) renders the vanilla missing-texture sprite (purple/black checker) at render time. Visually loud, doesn't crash, easy to spot.

### Modpack-author note

No migration. No third-party variants are known to ship between v1.0 and v1.0.1; if any exist, they'll continue to load (`inner_texture` is optional) and just render with the missing-texture fallback until a JSON edit adds the field.

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
