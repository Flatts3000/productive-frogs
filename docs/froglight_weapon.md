# Froglight Cleaver

The Froglight Cleaver (issue #212) is a late-game sword that drops a Resource
Slime's Froglight when it kills it - the active-play counterpart to the passive
frog loop (wade into a slime farm and swing, instead of waiting on frogs).

## Behavior

- Killing a **size-1** Resource Slime with the cleaver drops that slime's
  Froglight - the same variant-stamped (and, if the slime was brewed,
  effect-stamped) `configurable_froglight` a frog would produce on eating it.
- **Looting** on the cleaver raises the drop count (1 + 0..Looting).
- A normal netherite-tier sword otherwise (takes Sharpness, Unbreaking, etc.).
- Bigger slimes split rather than dropping a Froglight, same as the tongue path.

## Why it's balanced

It is gated behind **boss Froglights** in its recipe (see below), so you can only
craft it once you already run boss-catalyst-altar farms - deep endgame. By then
the frog loop is long established, so manual slime-killing for Froglights does not
undercut early/mid farming. The cleaver can simply be good.

## Recipe (maintainer-prescribed)

```
[Nether Star FL][Nether Star FL][Dragon's Breath]
[Dragon Egg FL ][Nether Star FL][Dragon's Breath]
[Dragon Egg FL ][              ][               ]
```

3 Nether Star Froglights + 2 Dragon Egg Froglights + 2 Dragon's Breath. The
Froglight slots are component-ingredients (`neoforge:components`) requiring a
`configurable_froglight` of the exact boss variant, like the Crucible melt
recipes.

## Design

- A plain netherite-tier `SwordItem` (`PFItems.FROGLIGHT_CLEAVER`); the harvest is
  event-driven, not item-class behavior.
- `FroglightWeaponHandler` (`LivingDeathEvent`): when a `ResourceSlime` dies to a
  killer holding the cleaver, it reuses `FrogTongueDropHandler.buildFroglight`
  (now public) so variant + brewed-effect stamping lives in one place, shared with
  the tongue drop.
- Config `froglight_weapon.enabled` + `ConfigEnabledCondition.Key.FROGLIGHT_WEAPON`
  gates the recipe; creative-tab + JEI gating + an inert handler when off.
- Texture (`scripts/generate_froglight_cleaver_texture.py`) reflects the
  materials: a pale Nether-Star-froglight blade, a dark Dragon-Egg hilt, a purple
  Dragon's-Breath wisp.

## Tests

- **JUnit** `FroglightWeaponRecipeTest` - pins the prescribed grid + component
  variants + the `froglight_weapon` gate.
- **JUnit** `ConfigEnabledConditionTest` - the `froglight_weapon` key round-trips.
- **GameTest** `froglightCleaverKillDropsFroglight` - a zombie holding the cleaver
  kills a slime; a Froglight drops.

## Related

- `FrogTongueDropHandler` (shared `buildFroglight`)
- Boss tier + boss Froglights (v1.14, `docs/boss_catalyst_altar.md`)
- Config-gating convention (#196)
