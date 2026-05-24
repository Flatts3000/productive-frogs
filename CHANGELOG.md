# Changelog

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
- Add new slime variants without recompiling: drop a JSON in `data/<modpack>/productivefrogs/slime_variant/<name>.json` plus an inner-cube texture, a primer-tag entry, and a smelting recipe. JEI Information pages auto-extend to cover the new entry.
- Cross-mod variants (Mekanism, Create, Thermal, etc.) gated by `mod_loaded` neoforge conditions — see `docs/cross_mod_compat.md`.

### Known limitations
- One end-to-end AI tongue gametest (`frogTongueAiPathDropsConfigurableFroglight`) is timing-flaky in CI and runs as `required = false`. The drop path is independently covered by `matchingFrogKillDropsConfigurableFroglight` (manual hurt damage, no AI).
- No automation (hoppers, power, pipes, multiblocks) in v1.0 — that's the V2 scope; v1.0 is the playable foundation + hand-operated appliance layer.

### Modpack note
This is a fresh release on the MC 1.21.1 line. There is no migration path from earlier 1.21.11 / 1.21.x development snapshots; modpack maintainers carrying those should regenerate worlds.
