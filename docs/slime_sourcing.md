# Slime Sourcing (V1)

> **⚠️ SUPERSEDED by v1.0.** This doc describes the original pre-v1.0 design where vanilla slimes and magma cubes were acceptable infusion targets and the `productivefrogs:primer/<category>` tag system gated infusion. v1.0 ships a tightened model:
>
> - Vanilla `minecraft:slime` and `minecraft:magma_cube` are NOT acceptable infusion targets. Only the six PF parent species (Bog / Cave / Geode / Tide / Infernal / Void) can be infused.
> - Infusion is species-locked: a Cave Slime can only be infused into a Cave variant (iron, copper, gold, ...). Wrong-species primers are hard-rejected.
> - Re-infusion is hard-rejected: an Iron Slime cannot be re-infused into a Copper Slime.
> - The `productivefrogs:primer/<category>` tag files are deleted. Infusion requires exact `SlimeVariant.primer_item` match.
>
> See [species_as_category_redesign.md](./species_as_category_redesign.md) §Slime infusion for the authoritative v1.0 spec.

How players obtain Resource Slimes (original pre-v1.0 design follows).

## Core Mechanic

Resource Slimes are obtained by killing vanilla slimes (or magma cubes) and harvesting their split offspring. Two paths exist; both run on the same vanilla split event.

### Path 1: Random discovery (default behavior)

When a vanilla slime or magma cube is killed and splits, **each offspring has a configurable chance** to be a Resource Slime randomly picked from the parent slime species's **default category pool**, instead of a vanilla slime.

| Parent entity | Default category pool |
|---|---|
| `minecraft:slime` (overworld) | Metallic — iron, copper, gold + any modded metallics (osmium, bronze, tin, ...) |
| `productivefrogs:cave_slime` (deep caves) | Mineral — redstone, lapis, coal, quartz |
| `productivefrogs:geode_slime` (amethyst geodes / mountains) | Gem — diamond, emerald, amethyst, quartz, fluorite |
| `productivefrogs:tide_slime` (deep ocean) | Aquatic — sponge, prismarine, coral, kelp, ink, nautilus |
| `minecraft:magma_cube` (Nether) | Infernal — blaze, ghast, magma, wither |
| `productivefrogs:void_slime` (End) | Arcane — ender pearl, echo shard, end stone |
| Future modded slime species | (defined by their mod / datapack) |

- The pool is **data-driven**. Adding a new Resource Slime variant via JSON automatically adds it to its category's pool.
- The chance is **configurable** per parent slime species (default value TBD — see open questions).
- If a roll misses, the offspring is a regular vanilla slime, same as today.

### Path 2: Infusion (immediate transformation)

Right-click a vanilla slime (or magma cube) holding any item in a `productivefrogs:primer/<category>` tag → the slime is **immediately transformed** into a Resource Slime of the matching variant. The held item is consumed (1 count).

**An infused slime is no longer a vanilla slime.** The entity is replaced in-place: same position, same size, same HP, but now of type `productivefrogs:resource_slime` with the variant set. Texture and behavior swap to the Resource Slime variant. It will:

- Split into smaller Resource Slimes of the same variant on death (vanilla split mechanic preserved, just for a different entity type).
- Produce typed milk if put through a Slime Milker (see [farming.md](./farming.md)).
- Be eaten by matching-category frogs.

Because infusion is a transformation, there's no infusion-state to "store" — the entity *is* the Resource Slime now. Re-infusing a Resource Slime with a different category material is a separate question (currently: not allowed; once transformed, the slime is committed to its variant).

A size-1 vanilla slime fed a primer item transforms in place into a size-1 Resource Slime, ready to be bucketed or fed to a frog (see [open_questions.md](./open_questions.md) Q2a).

## Why both paths

- **Random discovery** preserves vanilla feel — slime farms produce occasional Resource Slimes as a side effect of normal play. Players discover the mechanic organically. The biome/dimension a slime comes from naturally biases what category they'll find (overworld = metallic, Nether = infernal).
- **Infusion** gives precise control — once a player knows they want lots of Iron Slimes, they infuse-and-kill on a schedule. No grinding for RNG.
- **Cross-category via infusion** — players can produce gem or arcane slimes from an overworld green slime by paying the infusion cost (diamond / ender pearl). They aren't locked into a slime species's default category.

## Tiny Slimes (Size 1)

Size-1 slimes don't split on death in vanilla — they just die. This is an edge case for both paths:

- **Random discovery path:** no offspring → no roll. Tiny slimes don't drop Resource Slimes via discovery.
- **Infusion path:** what feeding a size-1 slime does is an open question. See [open_questions.md](./open_questions.md) Q2a.

## V1 Configurability

Exposed as mod config (with datapack overrides where appropriate):

- `discovery_chance_per_offspring` — per parent entity ID (default: TBD, see Q2b).
- `default_category` — per parent entity ID (`minecraft:slime → metallic`, `minecraft:magma_cube → infernal`).
- `pool_membership` — variant JSON's category tag determines which pools it appears in. Modpack authors can re-tag.

Modpack authors can also:
- Disable discovery entirely (set chance to 0) and force infusion-only.
- Add new parent entities with their own default categories (e.g. a Botania mana slime → arcane).
- Adjust per-variant weights inside a pool (a variant JSON's optional `weight` field).

## Slime Transport — Slime Bucket

Captured size-1 slimes (any type, including Resource Slimes and infused-vanilla slimes) can be carried in a **Slime Bucket** item. See [items_and_blocks.md](./items_and_blocks.md#slime-bucket) for the full spec.

This is purely a transport mechanism. The bucket does not create new slimes — it just moves existing ones around so players can ferry split-offspring Resource Slimes from their slime farm to their frog terrarium.

## Direct-Kill Drops

If a player kills a Resource Slime directly (sword, fall damage, lava, accident, etc.) without it being eaten by a matching frog, the slime drops **slimeballs only** — same as vanilla slime parity. The resource conversion happens through the frog, not through direct combat.

This keeps the frog the canonical conversion path while not punishing accidents — players can still recover slimeballs to make new Resource Slimes via infusion, or to breed Resource Frogs.

## What's NOT in V1

- **No slime breeding.** Slimes don't breed in this mod (breeding is on the frog side — see [items_and_blocks.md](./items_and_blocks.md#resource-tadpoles--frogs-entities)).
- **No wild Resource Slime spawns.** Resource Slimes only exist as offspring of a vanilla slime split, as a transformation via infusion, or spawned from Slime Milk.
- **No Slime Nursery block.** Deferred to V2 (see [versioning.md](./versioning.md)).
- **No fishing / loot / trading sources.** Slimes only come from three V1 paths: split, infusion, milk-spawn.

## Implementation Sketch

Two hook points:

1. **`Item.useOn(LivingEntity)` (or NeoForge equivalent)** — when the player right-clicks a slime/magma cube while holding a primer-tagged item:
   - Remove the existing vanilla slime / magma cube / parent slime entity.
   - Spawn a new `ResourceSlime` of the matching variant at the same position, same size, same HP, same velocity.
   - Consume one of the held item.
   - This is the "infusion = immediate transformation" model — there's no NBT-tracked infusion state, the entity type itself encodes the variant.

2. **Vanilla slime split event** — when a vanilla parent slime (not a Resource Slime) is killed and would spawn child slimes, intercept each spawn:
   - Roll `discovery_chance_per_offspring` (default 5%).
   - On hit, pick a random variant weighted from the parent entity's default category pool and spawn a `ResourceSlime` of that variant.
   - On miss, spawn vanilla as normal.
   - Resource Slimes that split simply split into smaller Resource Slimes of the same variant — no roll needed.

Both hooks plug into the data-driven `SlimeVariant` registry from [architecture.md](./architecture.md).
