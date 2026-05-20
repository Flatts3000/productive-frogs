# Items and Blocks

This document enumerates every new item, block, and entity in the v0.1 scope.

## Items

### Frog Net

- **ID**: `productivefrogs:frog_net`
- **Role**: The only way to obtain a `Frog Egg` item. Single-purpose tool — captures frogspawn from the world.
- **Crafting recipe**: 3 string + 2 sticks (shape similar to a fishing rod).
- **Use**: right-click on a vanilla `minecraft:frogspawn` block in the world.
  - Block is consumed.
  - 1 `Frog Egg` item is added to the player's inventory.
- **Durability**: 16 uses (cheap, intended to be re-crafted).
- **Does NOT capture adult frogs.** Players relocate adult Resource Frogs via vanilla **leads** (canonical method), slimeball lure (short-distance herding), or boats (water crossing).

### Frog Egg (item)

- **ID**: `productivefrogs:frog_egg`
- **Role**: Stackable, inventory-friendly representation of a frog egg.
- **Obtained from**: Frog Net used on `minecraft:frogspawn`.
- **Stack size**: 64.
- **Use**: right-click on water → places `productivefrogs:frog_egg` block.

### Slime Milker (block)

- **ID**: `productivefrogs:slime_milker`
- **Role**: V1 farming keystone. Single appliance block (no power, no fuel, no GUI) that converts a captured slime into a typed milk fluid.
- **Crafting**: TBD recipe — likely 4 iron ingots + 1 piston + 1 cauldron (themed as a mechanical press).
- **Use**: right-click while holding a `Slime Bucket` → slime is consumed, bucket transforms into `Bucket of <Variant> Slime Milk`. Brief press animation + squelch sound.
- **Operation**: instant. No tick delay. V2 may add auto-fed variants.
- **Visual**: themed as a single-block mechanical press. Pistons / cogs / hopper-shaped chassis.

See [farming.md](./farming.md) for the full milking-to-production loop.

### Slime Milk (fluid + bucket)

- **Fluid IDs**: one per slime variant, e.g. `productivefrogs:iron_slime_milk`, `productivefrogs:sponge_slime_milk`, `productivefrogs:vanilla_slime_milk`, `productivefrogs:magma_slime_milk`.
- **Bucket item**: `productivefrogs:<variant>_slime_milk_bucket` for each variant.
- **Flow**: lava-style, 4-block flow distance in overworld.
- **Spawning**: each milk **source block** rolls a spawn tick (default: 20s ± 10s jitter). On success, spawns a size-1 slime of the matching variant directly above. Won't overspawn — checks for occupied space.
- **Depletion**: configurable (default ON, 16 spawns per source block). When depleted, the source block disappears.
- **Player interactions**: walkable like vanilla liquids; harmless. An empty bucket scoops a source into a typed milk bucket. No other interaction.

### Slime Bucket

- **ID**: `productivefrogs:slime_bucket`
- **Role**: Transport mechanic for slimes. Mirrors the vanilla axolotl-bucket pattern — single item, contents stored in NBT.
- **Obtained from**: capture interaction only (no crafting recipe).
- **Capture**: right-click any **size-1 slime entity** (vanilla `minecraft:slime`, `minecraft:magma_cube`, or any Resource Slime) while holding an empty bucket.
  - The slime entity is removed from the world.
  - The empty bucket transforms into a `Slime Bucket` with NBT storing the captured slime's variant ID, infusion state (if present), and any custom name.
- **Size restriction**: size 1 only. Medium / big / huge slimes can't be bucketed — kill them down to size 1 first.
- **Display name**: dynamically reflects contents — "Bucket of Iron Slime", "Bucket of Magma Cube", "Bucket of Slime", etc. Resource Slime buckets are typed; an infused-vanilla-slime bucket shows the infusion.
- **Placement**: right-click on a solid block or water tile → spawns the captured slime at the target position; bucket transforms back to empty.
- **Stacking**: does not stack (vanilla bucket constraint).
- **Why it exists**: vanilla offers no way to move slimes (no leads, no nametag transport). Players who infuse a big slime and harvest tiny Resource Slime offspring can now bottle them up and walk them home to their frogs.

### Resource Drops (Froglights)

Each slime species drops a thematic "froglight-style" block when consumed by a matching frog. Drops are defined per-slime-variant in loot tables, not in code. The dropped item is an **item entity** (vanilla hoppers can collect it) — players don't have to break a placed block.

**Processing paths** (see [farming.md](./farming.md#drop-processing)):

- **Smelt** (always available, every category): 1 Froglight → 1 base resource. Vanilla furnace recipe.
- **Crush** (metallic category only, requires installed crushing mod): 1 Froglight → 2× powder → smelt → 2 ingots. Compat recipes ship for Create, Mekanism, and Thermal. See [cross_mod_compat.md](./cross_mod_compat.md).

Examples:

| Slime species (category) | Dropped block | Process to base resource |
|---|---|---|
| Iron Slime (metallic) | Iron Froglight | Break → iron nuggets |
| Copper Slime (metallic) | Copper Froglight | Break → copper nuggets |
| Gold Slime (metallic) | Gold Froglight | Break → gold nuggets |
| Diamond Slime (gem) | Diamond Froglight | Break → diamonds |
| Redstone Slime (mineral) | Glowing Redstone Block | Break → redstone dust |
| Ender Slime (arcane) | Ender Froglight | Break → ender pearls |
| Blaze Slime (infernal) | Blaze Froglight | Break → blaze rods |
| Osmium Slime (metallic, Mekanism compat) | Osmium Froglight | Break → osmium nuggets |
| Bronze Slime (metallic, Create compat) | Bronze Froglight | Break → bronze nuggets |

Drop blocks are decorative, light-emitting variants in the same family as vanilla froglights (visual consistency with the inspiration).

## Blocks

### Frog Egg (block)

- **ID**: `productivefrogs:frog_egg` (same path as the item, separate registry).
- **Placement**: by using the Frog Egg item on water (mirrors how vanilla frogspawn lives on water).
- **Interaction**: right-click with any item in a `productivefrogs:primer/<category>` tag.
  - Held item is consumed (1 count).
  - Block transforms into the matching primed egg variant block: e.g. `productivefrogs:metallic_frog_egg`.
- **Hatch**: if left unprimed, it does not hatch (does not produce a vanilla tadpole). Player must prime before hatch.

### Primed Frog Eggs (6 blocks, one per category)

- IDs: `productivefrogs:metallic_frog_egg`, `mineral_frog_egg`, `gem_frog_egg`, `aquatic_frog_egg`, `infernal_frog_egg`, `arcane_frog_egg`.
- **Placement:** like vanilla frogspawn — must be on or adjacent to a water source block.
- **Hatch:** vanilla frogspawn timer (~5-10 minutes wall-clock). Spawns a Resource Tadpole of the matching category at the water tile.
- **Two ways to obtain a placed Primed Frog Egg block:**
  - Manually: place a `Frog Egg` item on water → unprimed block → right-click with a primer item.
  - Automatically: two same-category Resource Frogs in love mode place one on nearby water during their breeding event.

### Parent Slime Species

The mod adds four new vanilla-style slime entities so that every category has its own discovery parent. Each behaves like vanilla `minecraft:slime` (splits on death at size 2+, drops slimeballs at size 1, is infusable, is bucketable at size 1) but defaults to its themed category pool on split.

| Entity ID | Display name | Default category | Spawn niche |
|---|---|---|---|
| `productivefrogs:cave_slime` | Cave Slime | Mineral | Dripstone caves, deep dark, lush caves at low Y, deep slime chunks |
| `productivefrogs:geode_slime` | Geode Slime | Gem | Mountain biomes near amethyst geodes; geode-adjacent caves |
| `productivefrogs:tide_slime` | Tide Slime | Aquatic | Deep ocean, lukewarm ocean, warm ocean biomes; near ocean monuments |
| `productivefrogs:void_slime` | Void Slime | Arcane | Outer End islands (end_highlands, end_midlands, end_barrens, small_end_islands) |

Together with vanilla `minecraft:slime` (metallic) and `minecraft:magma_cube` (infernal), this gives a 1-to-1 parent-slime-per-category set:

| Category | Parent slime species |
|---|---|
| Metallic | `minecraft:slime` |
| Mineral | `productivefrogs:cave_slime` |
| Gem | `productivefrogs:geode_slime` |
| Aquatic | `productivefrogs:tide_slime` |
| Infernal | `minecraft:magma_cube` |
| Arcane | `productivefrogs:void_slime` |

Names are working titles — easy to rename before code lands.

### Resource Slimes (entities)

- Data-driven via JSON variants (see [architecture.md](./architecture.md)).
- One `ResourceSlime` entity class. Per-variant config defines:
  - Display name
  - Texture / tint color
  - Category tag membership
  - Drop loot table (the block the frog produces from eating it)
  - Optional `weight` for the random-discovery pool
- Variants ship as JSON in `data/productivefrogs/slime_variant/`.
- Cross-mod variants gated by `neoforge:conditions → mod_loaded`.
- **Resource Slimes do NOT spawn in the world directly.** They only come into existence as offspring of a parent slime split (vanilla green slime, magma cube, Cave Slime, Geode Slime, or Void Slime) — see [slime_sourcing.md](./slime_sourcing.md) for the discovery + infusion mechanic.

### Resource Tadpoles & Frogs (entities)

- One `ResourceTadpole` entity, one `ResourceFrog` entity, each with a `category` variant property (Metallic / Mineral / Gem / Aquatic / Infernal / Arcane).
- Tadpoles grow into frogs of the same category (mirrors vanilla tadpole → frog).
- **AI inherits vanilla `Frog` behavior unchanged.** Tongue range, tongue cooldown, movement, water proximity, breeding behavior — all vanilla.
- **Only behavioral override:** the "is this a valid prey entity?" check is filtered by category. A Metallic Frog only tongues slimes whose variant is in the metallic category tag. Vanilla magma cubes are not eaten by Metallic Frogs (they're infernal-category). Resource Slimes whose category doesn't match are ignored.
- **Direct-feed interaction:** right-clicking a Resource Frog while holding a Slime Bucket containing a matching-category slime causes the frog to immediately tongue the bucketed slime; the bucket transforms to empty, and the frog drops the corresponding Froglight at its current position. Mismatched bucket → no-op (bucket retained). Tongue cooldown still applies.
- Vanilla frog death/despawn rules apply — bred or player-hatched frogs are persistence-required and won't despawn naturally.

### Frog Breeding

Resource Frogs can breed with each other via the standard vanilla animal love-mode mechanic:

- **Breed item:** slimeball (1 per parent, consumed).
- **Same-category only.** Two Metallic Resource Frogs produce a Metallic offspring. A Metallic Frog and a Gem Frog cannot breed together (different species in love-mode terms).
- **Offspring:** the breeding pair places a **Primed Frog Egg block** of their shared category on a nearby water tile — the same block obtained by hand-priming a Frog Egg with the matching primer. Player can let it hatch normally into a Resource Tadpole of that category, or net it with the Frog Net to retrieve it as a `Primed Frog Egg` item.
- **No primer cost on breeding offspring.** Once a player has two Resource Frogs of the same category, they can self-sustain that category's frog population indefinitely. The primer pipeline (Frog Net → Frog Egg → primer item) is the one-time cost to bootstrap each category.
- **Vanilla frogs still breed normally** (per [open_questions.md](./open_questions.md) Q3) — they produce vanilla frogspawn, which the player nets into vanilla Frog Eggs that need priming. Cross-breeding (vanilla frog × Resource Frog) is not supported.

This subsumes the previously-open Q12 question.

### Slime Nursery — DEFERRED TO V2

The Slime Nursery is an automation block and explicitly **not in V1 scope**. See [versioning.md](./versioning.md). V1 slime acquisition relies only on vanilla-style mechanics (wild spawn and/or hand interaction) — see [open_questions.md](./open_questions.md) Q2 for the V1 sourcing choice.

## Summary roster (v0.1 target)

**Items (3 + N new):**
- Frog Net
- Frog Egg
- Slime Bucket
- N Bucket-of-Slime-Milk variants (one per slime variant)

**Blocks (7 + N drop blocks + N milk fluids):**
- Frog Egg block (unprimed)
- 6 primed Frog Egg blocks (one per category)
- Slime Milker
- N "froglight" drop blocks (one per slime species, decorative)
- N Slime Milk fluid blocks (one per slime variant)

**Entities (3, with variants):**
- ResourceSlime (data-driven N variants)
- ResourceTadpole (5 category variants)
- ResourceFrog (5 category variants)
