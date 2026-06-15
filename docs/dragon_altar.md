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

## Rewards (data-driven)

The altar's item drops are defined by a **loot table**, not hardcoded Java:

- `data/productivefrogs/loot_table/dragon_altar.json` (type `minecraft:entity`).
- Ships with two pools: one **Dragon's Breath** and one **Princess's Kiss** (#216).
- Rolled with a never-spawned phantom Ender Dragon as the `this_entity` loot context, so
  pack/mod loot conditions can key off "the dragon" exactly as on a real kill.

This is deliberate: the Princess's Kiss does not come from vanilla's (never-rolled)
`ender_dragon` loot table - it is spawned by `PrincessKissHandler` on a *real* dragon's
KILLED removal, which the replica never triggers. Routing the altar through its own loot
table both restores the Kiss and lets a pack add or remove any other dragon drop by
overriding or extending `dragon_altar.json` - **one JSON, no Java**, matching the mod's
cross-mod compat ethos.

Two rewards stay outside the loot table:

- **XP**: awarded as orbs at the hatch (`boss.dragon_altar.xpReward`, default 500 - one
  vanilla repeat-kill's worth; vanilla respawns grant none).
- **Dragon Egg**: a config toggle (`boss.dragon_altar.repeatableEgg`, default on), not a
  loot entry, because it flips the altar between a renewable-egg farm and "everything but
  a duplicate egg" - a balance lever rather than a question of which items drop.

## Config (`boss.dragon_altar`)

All under the boss-tier section; the whole altar is gated by `boss.enabled` (#200) - when
the boss tier is off, the altar blocks are uncraftable and the summon never starts.

| Key | Default | Meaning |
| --- | --- | --- |
| `summonTicks` | 200 | Length of the summon show (beams + dragon growth) in ticks. Rewards land at the end regardless. |
| `xpReward` | 500 | Experience granted per completed summon. 0 = none. |
| `repeatableEgg` | true | Whether each summon deposits a Dragon Egg into the hatch. |

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
- `content/entity/PlinthFrog` ("Dragonsbane") - a neutered, pinned `Frog` reskin (NoAI,
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
  summon, and assert the hatch ends up holding Dragon's Breath, the Princess's Kiss, and
  the Dragon Egg. Guards the data-driven drop path (a wrong loot-table id or param set
  would empty the hatch).

The summon animation itself is GameTest-blind (client visuals) - verify scale, beam
anchoring, and growth pacing with a manual `runClient` pass.
