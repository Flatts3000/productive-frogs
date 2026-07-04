# Warden Altar - the Shrieker Pit (#279)

A frog-safe deep-dark altar that summons and harvests the Warden without the
player ever fighting it. Part of the #281 mob-drop redesign: bosses are farmed
via dedicated altars (raw drops + Liquid Experience), not by a frog eating them.
Third altar on the shared `BossAltarHatchBlockEntity` machinery (see
`docs/dragon_altar.md` for the family shape and `AltarApexDock` for the Phase 4
Apex gate + LE bank).

## Shape - an open-topped pit

The altar rules (maintainer, 2026-07-04): one Hatch, structure made of
reinforced froglights, gated by one resource from the boss. The pit is 5x5
footprint, five tall, **4-fold symmetric** (no facing-aware rotation pass -
contrast the wither altar), anchored on the **Warden Altar Hatch** at the pit
floor center:

- **Floor** (`y-1`): 5x5 Reinforced Sculk Froglight, with the **Echoing
  Catalyst** capstone set directly beneath the Hatch (crafted from a Sculk
  Catalyst - building the altar proves a first Warden kill; the altar pays
  catalysts thereafter).
- **Shaft lining** (`y0..2`): the 5x5 ring, Reinforced Sculk Froglight, around
  a 3x3 open cavity.
- **Rim at grade** (`y3`): Reinforced Echo Shard Froglight ring with the four
  **Shrieker Receptacles** at the cardinals.
- **Interior**: the 3x3 column from the Hatch to the rim must be air (the
  replica needs the shaft clear).

The canonical layout ships as `data/productivefrogs/structure/warden_altar.nbt`
(authored by `scratchpad`-generated NBT from the validator's own offsets) and is
locked by the `warden_altar_*` GameTests - a layout edit without re-syncing
`WardenAltarValidator` fails CI.

## Summon loop

1. Build the pit; **install the Warden Apex Frog** (Prowler x Rift cross) by
   shift-right-clicking the Hatch with a filled Frog Net. Wardenbane - the
   display frog - perches on the Hatch while installed.
2. Load each Shrieker Receptacle with one **Sculk Shrieker** (4 = warning level
   4, the vanilla summon trigger; renewable via sculk-catalyst farms, which the
   altar's own catalyst payout bootstraps).
3. The summon runs `boss.warden_altar.summonTicks` (default 168, the vanilla
   emerge animation): the Warden replica rises out of the pit floor
   (client-only `GrowingReplicaRenderer` - never a real entity, no boss bar, no
   sonic boom), then Wardenbane devours it.
4. **Payout** into the Hatch chest: the Warden's own loot roll (the Sculk
   Catalyst; phantom-entity context so pack/GLM additions apply) plus **one
   explicit Echo Shard** per summon - the altar is the game's renewable echo
   shard source (vanilla Wardens drop none). The shard is stripped from the
   roll so a pack table that adds shards cannot double-pay. XP
   (`boss.warden_altar.xpReward`, default 5) banks as Liquid Experience in the
   dock; pipes drain it from any face.

## Recipes (boss-gated)

| Block | Recipe |
|---|---|
| Warden Altar Hatch | obsidian corners, sculk edges, chest center |
| Shrieker Receptacle | obsidian corners, sculk edges, sculk sensor center |
| Echoing Catalyst | 8 obsidian around a sculk catalyst |
| Reinforced Sculk Froglight | obsidian cross + sculk-variant Configurable Froglight |
| Reinforced Echo Shard Froglight | obsidian cross + echo-shard-variant Configurable Froglight |

Both reinforced blocks use **resource variants** (survivors of the Phase 5
mob-variant retirement) per the reinforced-froglight re-key (#304).
