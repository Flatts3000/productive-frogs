# Elder Guardian Altar - the Monument Well (#280)

A frog-safe aquatic-monument altar that summons and harvests Elder Guardians -
no mining fatigue, no lasers, no thorns. Part of the #281 mob-drop redesign
(bosses are farmed via dedicated altars: raw drops + Liquid Experience).
Supersedes the scrapped #248. Fourth altar on the shared
`BossAltarHatchBlockEntity` machinery.

## Shape - a sealed water tank

Altar rules: one Hatch, reinforced froglights, gated by one boss resource. The
well is a 5x5x5 sealed tank, **4-fold symmetric**, anchored on the **Elder
Altar Hatch** in the tank floor center:

- **Floor** (`y-1`): 5x5 Reinforced Sponge Froglight.
- **Walls** (`y0..2`): the 5x5 ring, Reinforced Prismarine Froglight, around a
  3x3 cavity.
- **Roof** (`y3`): Reinforced Prismarine Froglight plate with the four **Tide
  Offering Receptacles** standing at the corners like monument spires and the
  **Monument Core** capstone at the center (crafted from a Wet Sponge - the
  Elder Guardian's signature drop).
- **Interior**: every 3x3x3 cell (except the Hatch) must be a **water source**
  - the validator checks the flood, not just the shell. Elderbane, the display
  frog, swims pinned above the Hatch.

Canonical layout: `data/productivefrogs/structure/elder_altar.nbt`, locked by
the `elder_altar_*` GameTests (including `elder_altar_rejects_drained_tank` -
one drained source invalidates the altar).

## Summon loop

1. Build + flood the tank; **install the Elder Apex Frog** (Gulper x Prowler
   cross) via a filled Frog Net on the Hatch.
2. Load each Tide Offering Receptacle with one **Prismarine Crystal** -
   renewable through the predation chain (Gulper-line frogs eating guardians
   feed the elder altar).
3. The summon runs `boss.elder_altar.summonTicks` (default 200): the Elder
   Guardian replica swells mid-tank (client-only `GrowingReplicaRenderer`),
   then Elderbane devours it.
4. **Payout** into the Hatch chest: one explicit **Wet Sponge** (its loot pool
   carries `killed_by_player` - the same trap as the wither's Nether Star - so
   it is paid directly and stripped from the roll) plus the rest of the Elder
   Guardian's loot roll (prismarine shards, cod/crystals, unconditioned). XP
   (`boss.elder_altar.xpReward`, default 10) banks as Liquid Experience in the
   dock.

## Pack extension point

Beyond the boss's own table, each summon also rolls the data-driven
`productivefrogs:elder_altar` loot table (the dragon-altar precedent). It ships
empty; packs and mods override or add pools to extend the altar's yield
without Java - e.g. extra sponges, nautilus shells, or a mod-gated ocean
resource.

## Recipes (boss-gated)

| Block | Recipe |
|---|---|
| Elder Altar Hatch | obsidian corners, prismarine brick edges, chest center |
| Tide Offering Receptacle | obsidian corners, prismarine brick edges, sea lantern center |
| Monument Core | 8 obsidian around a wet sponge |
| Reinforced Prismarine Froglight | obsidian cross + prismarine-variant Configurable Froglight |
| Reinforced Sponge Froglight | obsidian cross + sponge-variant Configurable Froglight |

The prismarine variant was reclassified as a **block resource** (block-primed,
smelts back to the prismarine block, #304) so it survives the Phase 5
mob-variant retirement; sponge is monument-room-obtainable and survives as a
block resource too.
