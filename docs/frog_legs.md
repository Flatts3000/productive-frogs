# Frog Legs

Frog Legs (issue #194) are the death payoff for killing a frog: a renewable meat
for a skyblock where animals are scarce, fitting the pack's "frogs replace the
overworld" premise without trivializing food (you have to kill a producer to get
it).

## Behavior

Modelled on how cows and chickens drop their meat:

- **Drop:** killing any frog drops **1-2 Raw Frog Legs**, Looting-scaled (+0..Looting).
- **Cooked-on-fire:** if the frog was on fire when it died, it drops **Cooked Frog
  Legs** instead - the same `furnace_smelt`-when-on-fire trick cows/chickens use.
- **Cooking:** Raw Frog Legs cook into Cooked Frog Legs in a furnace, smoker, or on
  a campfire (smoker is fastest, as usual).
- **Food values:** raw is chicken-tier (nutrition 2, saturation modifier 0.3, no
  hunger debuff); cooked is a step up (nutrition 6, modifier 0.6 - cooked-chicken
  tier).
- **Any frog, vanilla or modded.** The drop fires for anything
  {@code PFEntityTags.isFrog} accepts: a vanilla `Frog` (incl. the Resource Frog
  and frog mobs that subclass it) or an entity type in the `productivefrogs:frogs`
  tag. Resource Tadpoles are not frogs and drop nothing.

## Design

The drop is an **event** (`FrogLegDropHandler`, `LivingDropsEvent`), not a loot
table, for two reasons:

- **All frogs.** A per-entity loot table only governs our own Resource Frog; it
  can't add a drop to the vanilla frog or a modded frog. The event keys off the
  shared `productivefrogs:frogs` tag and so covers every frog uniformly.
- **Clean config gate.** `frog_legs.enabled` (default on) gates the whole feature
  in one `if`. When off: no legs drop, and the items + cooking recipes are
  uncraftable / hidden from JEI + the creative tab.

This mirrors `FrogTongueDropHandler`, which already uses an event for the same
"the vanilla loot-table predicate can't express our entity state" reason. The
Looting level is read from the killer's main-hand weapon; the cooked-on-fire check
reads the dying frog's `isOnFire()`, both reproducing the vanilla cow/chicken loot
functions in code.

## Frog Legs Soup (#217)

A bowl meal one tier above Cooked Frog Legs (rabbit-stew-tier food values): a
`BowlFoodItem` (stacks to 1, returns the bowl on eat), crafted shapeless from a
bowl + Cooked Frog Legs + a brown and a red mushroom. Shares the `frog_legs`
config gate (no legs feature, no soup). Texture baked by
`scripts/generate_frog_legs_soup_texture.py`.

## Config

`frog_legs.enabled` (default `true`). Set `false` for packs that want losing a
frog to sting - no legs drop and the items/recipes vanish. Recipe-gated via the
shared `ConfigEnabledCondition` (`Key.FROG_LEGS`).

## Decisions

- **Flat 1-2 + Looting, not stat-scaled.** The drop does not scale with the frog's
  Appetite/Bounty - those already gate the Froglight yield from the frog loop; the
  legs are a flat death consolation, kept simple and predictable.
- **Same legs for every frog.** Brewed/boss-variant frogs drop the same Frog Legs;
  no per-variant meat (it's a food item, not a resource).

## Assets

`scripts/generate_frog_legs_textures.py` bakes the two 16x16 item textures (raw
pink, cooked browned drumstick). Procedural Pillow art, run manually when the look
changes - not build-validated.

## Tests

- **JUnit** `FrogLegsRecipeTest` - the three cooking recipes (smelting / smoking /
  campfire) take raw -> cooked and carry the `frog_legs` gate.
- **JUnit** `ConfigEnabledConditionTest` - the `frog_legs` condition key
  serializes + round-trips.
- **GameTest** `killedFrogDropsRawLegs` - a killed Resource Frog drops raw legs.

GameTest is blind to the textures / item models and the cooked-on-fire path;
verify those with a manual `runClient` pass.

## Related

- The frog loop's other drop: `FrogTongueDropHandler` (slime -> Froglight)
- Shared frog predicate: `PFEntityTags.isFrog` (also used by the Frog Net, #205)
- Config-gating convention (#196)
