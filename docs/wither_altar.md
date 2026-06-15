# Wither Altar (#247)

A controllable, repeatable Wither "farm" - the Nether-side sibling of the End Dragon
Altar (`docs/dragon_altar.md`). Instead of fighting a real Wither, the altar summons a
harmless replica that a special frog ("Witherbane") devours, depositing the reward into
a hatch. Built to mirror the dragon altar; the replica approach dissolves this issue's
original problem (containing a block-breaking, exploding boss) - there is no real Wither.

## Player flow

1. Defeat a Wither the normal way once. The altar's capstone, the **Withered Star**, is
   crafted from a Nether Star - so building the altar proves a first kill (the gate).
2. Build the arena: a Reinforced Blaze Rod Froglight shell, a Reinforced Soul Sand
   Froglight floor, the **Wither Altar Hatch** in the centre with the Withered Star in
   the floor beneath/beside it, and the ritual receptacles on the far wall. The canonical
   layout is the shipped `wither_altar` structure; the validator is pinned to it by GameTest.
3. Load the full vanilla summon into the receptacles: **4 Soul Sand + 3 Wither Skeleton
   Skulls**, arranged as the vanilla Wither T (by hand or piped in).
4. With all seven loaded and the structure valid, a summon runs: a Wither replica charges
   in the arena (growing + roaring like a real spawn), then Witherbane devours it. All
   seven items are consumed - the full vanilla summon cost.

No controller block (the seven loaded receptacles are the trigger). The replica is never
a real entity: no boss bar, no block damage, no attacks.

## The summon animation - copies the vanilla spawn

The replica is rendered through the vanilla `EntityRenderDispatcher` with a never-spawned
phantom Wither whose invulnerable-ticks count 220 -> 0 over the summon. The stock
`WitherBossRenderer` then does everything vanilla does: grows the model 1.5x -> full 2.0x,
shows the blue `wither_invulnerable` charging texture, and plays the spawn head animation.
No boss bar appears (a phantom is never a server `BossEvent`). The summon length defaults
to 220 ticks (the vanilla invulnerable spawn); the renderer drives the spawn scale/texture
off a fixed 220-tick range so it stays correct even if the length is reconfigured. The roar
(`WITHER_SPAWN`) plays at spawn start.

## Config (`boss.wither_altar`)

Under the boss-tier section (gated by `boss.enabled`):

| Key | Default | Meaning |
| --- | --- | --- |
| `summonTicks` | 220 | Summon length in ticks (the charge before Witherbane eats the replica). |
| `xpReward` | 50 | Experience per completed summon (a vanilla Wither's value). 0 = none. |

## Reward

- **Nether Star Froglight + XP** (explicit; the boss-Froglight model from #249 - it smelts
  back to a Nether Star).
- **Whatever else the Wither drops**: the altar rolls the vanilla `minecraft:entities/wither`
  loot table with a phantom Wither (catching mod GLM additions), but **strips the raw
  Nether Star** so the star is only ever the Froglight.

## New blocks (6, boss-gated)

Reinforced Soul Sand Froglight + Reinforced Blaze Rod Froglight (4 obsidian + the matching
Froglight, mirroring the dragon altar's reinforced froglights), one parameterized
`WitherSummonReceptacleBlock` backing the Soul Sand + Wither Skull receptacles (the vanilla
summon T; `FILLED` blockstate; a BER renders the held item on the frog-facing face), the
Wither Altar Hatch (chest output + summon brain), and the Withered Star capstone (from a
Nether Star).

## Implementation map

- `content/block/WitherAltarHatchBlock` + `WitherSummonReceptacleBlock` (parameterized by
  accepted item) + `WitherAltarHatchBlockEntity` (chest + the `static serverTick` summon
  state machine) + `WitherSummonReceptacleBlockEntity` (one BE backs both receptacles).
- `content/entity/WitherbaneFrog` ("Witherbane") - a neutered, pinned `Frog` reskin (the
  Nether-side `DragonsbaneFrog`), dark-blue tinted, perched on the Hatch facing the ritual.
- `content/multiblock/WitherAltarValidator` - strict, anchored on the Hatch; offsets are
  generated from the captured `wither_altar` structure, and it **enforces air** in the
  interior cavity (marked with copper grates in the authoring build, swapped to air in the
  shipped fixture) so the summon space can't be walled in.
- `client/renderer/WitherAltarHatchRenderer` (the vanilla-spawn replica) +
  `WitherSummonReceptacleRenderer` (the held item on the frog-facing face) +
  `WitherbaneFrogRenderer`.

## Recipes (boss-gated, `config_enabled` -> `boss`)

- Reinforced Soul Sand / Blaze Rod Froglight: ` O / OFO / O ` (O obsidian, F the matching
  `configurable_froglight` variant via `neoforge:components`).
- Soul Sand / Wither Skull Receptacle: `OSO / SUS / OSO` (O obsidian, S soul soil, U soul
  sand / coal block).
- Wither Altar Hatch: `OOO / OCO / OOO` (O obsidian, C chest).
- Withered Star: `OOO / ONO / OOO` (O obsidian, N nether star).

## Tests

- `WitherAltarValidator` is locked to the shipped structure by `witherAltarValidatesWhenBuilt`;
  `witherAltarRejectsMissingFroglight` checks strictness; `witherAltarSummonDepositsDrops`
  runs a full summon and asserts the Nether Star Froglight lands in the Hatch.

The summon animation + receptacle render are GameTest-blind (client visuals) - verified via
a manual `runClient` pass.

## In-game guide

A Patchouli entry ships at `assets/.../patchouli_books/guide/en_us/entries/advanced/wither_altar.json`,
with a `patchouli:multiblock` page whose pattern is generated from this validator's offsets.
Guide content is not build-validated; confirm it renders with `runClient`.
