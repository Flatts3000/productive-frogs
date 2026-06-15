# End Dragon Altar (#249)

A controllable, repeatable Ender Dragon "farm" built on top of the vanilla End exit
portal. Rather than re-fighting the real dragon, the altar summons a harmless replica
that a special plinth frog devours, depositing the dragon's drops into a hatch. It is
the End-tier counterpart to the boss catalyst altar (`docs/boss_catalyst_altar.md`)
and, like that feature, sits under the boss-tier config master.

## Player flow

1. Defeat the Ender Dragon once the vanilla way. This leaves the exit portal with its
   Dragon Egg - the altar's capstone - and is the natural gate (you cannot complete the
   altar without an egg, and the egg only exists after a real first kill).
2. Build the altar around the exit portal: four **End Crystal Receptacles**, the
   reinforced Wither Skeleton Skull and Nether Star froglights, and the **End Dragon
   Altar Hatch** at the centre (the Dragon Egg sits on top of it). The canonical layout
   is the shipped `dragon_altar` structure; the validator is pinned to it by GameTest.
3. Drop an End Crystal into each of the four receptacles (by hand or via hopper/pipe -
   the receptacle exposes an insert-only item handler). The receptacle texture changes
   to show it is loaded and renders a hovering crystal.
4. With all four primed and the structure complete, a summon begins: converging crystal
   beams and a dragon replica that grows from tiny to full size over the summon, then
   the plinth frog ("Dragonsbane") eats it. The crystals are consumed, and the rewards
   land in the hatch.

There is **no controller block** - the four loaded receptacles are the trigger - and the
altar accepts **no bottle input**. The replica is never a real entity, so there is no
boss bar, no portal regeneration, and no gateway spawn.

## Rewards

A completed summon pays out in three parts.

### 1. Boss Froglights (the altar's signature output)

Like the rest of the mod's frog loop, the altar yields **variant-stamped Froglights**
(each smelts back to its resource), not the raw resource:

- A **Dragon Breath Froglight** (the `productivefrogs:dragon_breath` boss variant) - always.
- A **Dragon Egg Froglight** (the `productivefrogs:dragon_egg` boss variant) - when
  `boss.dragon_altar.repeatableEgg` is on (the renewable-egg lever, delivered as the
  froglight that smelts to a Dragon Egg).

Both are minted through `FrogTongueDropHandler.buildFroglight`, the single
froglight-construction point shared with the frog tongue drop and the Froglight weapon, so
variant stamping lives in one place. The existing v1.14 smelt-backs
(`configurable_froglight_dragon_egg_to_dragon_egg`, `..._dragon_breath_to_dragon_breath`)
turn them into the resource.

### 2. The dragon's own drops (data-driven)

What the dragon *itself* drops is defined by a **loot table**, not hardcoded Java:

- `data/productivefrogs/loot_table/dragon_altar.json` (type `minecraft:entity`).
- Ships with one pool: the **Princess's Kiss** (#216).
- Rolled with a never-spawned phantom Ender Dragon as the `this_entity` loot context, so
  pack/mod loot conditions can key off "the dragon" exactly as on a real kill.

This is deliberate: the Princess's Kiss does not come from vanilla's (never-rolled)
`ender_dragon` loot table - it is spawned by `PrincessKissHandler` on a *real* dragon's
KILLED removal, which the replica never triggers. Routing the altar through its own loot
table both restores the Kiss and lets a pack add or remove any other dragon drop by
overriding or extending `dragon_altar.json` - **one JSON, no Java**, matching the mod's
cross-mod compat ethos.

### 3. XP

Awarded as orbs at the hatch (`boss.dragon_altar.xpReward`, default 500 - one vanilla
repeat-kill's worth; vanilla respawns grant none).

## Config (`boss.dragon_altar`)

All under the boss-tier section; the whole altar is gated by `boss.enabled` (#200) - when
the boss tier is off, the altar blocks are uncraftable and the summon never starts.

| Key | Default | Meaning |
| --- | --- | --- |
| `summonTicks` | 200 | Length of the summon show (beams + dragon growth) in ticks. Rewards land at the end regardless. |
| `xpReward` | 500 | Experience granted per completed summon. 0 = none. |
| `repeatableEgg` | true | Whether each summon deposits a Dragon Egg Froglight (smelts back to a Dragon Egg). |

`PFConfig` is `ModConfig.Type.COMMON`, so the client reads `summonTicks` too and the
growth animation stays in sync with the server's summon length.

## Implementation map

- `content/block/EndDragonAltarHatchBlock` + `EndCrystalReceptacleBlock` - the placed
  blocks (obsidian-tier; the receptacle carries a `FILLED` blockstate).
- `content/block/entity/EndDragonAltarHatchBlockEntity` - a `BaseContainerBlockEntity`
  (27-slot chest GUI via `ChestMenu.threeRows`, pipe I/O via an `InvWrapper`). Its
  `static serverTick` runs the summon state machine: while idle it reconciles the plinth
  frog and, when the structure validates + boss tier is on + all four receptacles are
  filled, starts a summon; `completeSummon` spends the crystals, triggers the frog's
  tongue lash, awards XP, deposits the egg, and rolls the `dragon_altar` loot table.
- `content/block/entity/EndCrystalReceptacleBlockEntity` - holds one End Crystal via an
  insert-only `IItemHandler` (extraction blocked so a hopper cannot steal a primed
  crystal); the summon `consume()`s them.
- `content/entity/DragonsbaneFrog` ("Dragonsbane") - a neutered, pinned `Frog` reskin (NoAI,
  invulnerable, persistent). `reconcileFrog` keeps exactly one pinned on the central
  bedrock plinth while the structure is valid and removes it when broken.
- `content/multiblock/DragonAltarValidator` - strict, anchored on the hatch; offsets are
  generated from the captured `dragon_altar` structure (bedrock fountain, exit portal,
  receptacles, both reinforced froglight sets, the egg capstone). Reads block identity
  only, so it runs client-side (Jade) and server-side (the summon) alike.
- `client/renderer/EndDragonAltarHatchRenderer` - the in-world summon animation
  (converging `EnderDragonRenderer.renderCrystalBeams` + a growing vanilla `DragonModel`
  fed by a cached client-side phantom dragon). Growth is driven by client elapsed time
  since the summon was first observed, divided by `summonTicks`.
- `client/renderer/EndCrystalReceptacleRenderer` - renders the held crystal hovering
  above a loaded receptacle.

## Recipes

All boss-gated (`productivefrogs:config_enabled` -> `boss`):

- **End Crystal Receptacle**: `OEO / ERE / OEO` (O obsidian, E end stone bricks, R end rod).
- **End Dragon Altar Hatch**: `OOO / OCO / OOO` (O obsidian, C chest).

## Tests

- `PFGameTests.dragonAltarValidatesWhenBuilt` - the shipped structure must validate;
  pins validator and structure together.
- `PFGameTests.dragonAltarRejectsMissingFroglight` - strictness: removing one froglight
  must fail validation.
- `PFGameTests.dragonAltarSummonDepositsDrops` - end-to-end: prime the receptacles, run a
  summon, and assert the hatch ends up holding the Princess's Kiss plus the two
  variant-stamped boss Froglights (Dragon Breath + Dragon Egg). Guards both the loot-table
  path (a wrong id or param set would crash/empty it) and the froglight variant ids.

The summon animation itself is GameTest-blind (client visuals) - verify scale, beam
anchoring, and growth pacing with a manual `runClient` pass.

## In-game guide

A Patchouli entry ships at `assets/.../patchouli_books/guide/en_us/entries/advanced/end_dragon_altar.json`
(the `advanced` category, after the boss-tier overview). It includes a
`patchouli:multiblock` page whose pattern is generated from this validator's offsets, so
the book diagram and the validator stay in sync (full structure: bedrock fountain, exit
portal, receptacles, both froglight sets, hatch + egg). Like all guide content it is
**not build-validated** - confirm it renders with a `runClient` pass.
