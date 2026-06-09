# Potion of Hopping

The Potion of Hopping (issue #215) is a frog-themed mobility potion: while active,
**jumping launches you forward** (a frog leap), distinct from vanilla's vertical-only
Jump Boost. It gives Frog Legs (#194) a second use as a brewing reagent.

## Behavior

- **Brew:** awkward potion + **raw Frog Legs** -> Potion of Hopping (the
  rabbit's-foot -> Leaping parallel, our reagent). Splash / lingering / tipped-arrow
  variants come for free from the base potion. **Glowstone** upgrades it to
  Hopping II (the vanilla strong-potion pattern), which hops further.
- **Effect:** while Hopping is active, a jump adds a forward horizontal impulse in
  your facing direction (0.45 blocks/level), so you leap forward. Level II hops
  further.
- **Soft landing:** the effect shaves ~5 blocks off fall distance, so leaping off a
  ledge does not splat you (frogs do not splat). Pair with Slow Falling for bigger
  drops.
- Named **Hopping**, not Leaping - vanilla's Potion of Leaping is already Jump Boost,
  and frogs hop.

## Design

- A custom `MobEffect` (`PFEffects.HOPPING`) + `Potion` (`PFPotions.HOPPING`),
  registered via `DeferredRegister` (effects before potions). No per-tick effect
  logic - the jump does the work.
- `HoppingEffectHandler` (game bus) applies the impulse in
  `LivingEvent.LivingJumpEvent` - the same hook vanilla Jump Boost uses, so it runs
  on whichever side simulates the entity (client for the player, server for mobs)
  and stays in sync exactly like Jump Boost. The fall softening is in
  `LivingFallEvent`.
- The brewing mix is registered in `PFModBusEvents.onRegisterBrewingRecipes`
  (`RegisterBrewingRecipesEvent`).
- Effect icon baked by `scripts/generate_hopping_effect_icon.py`.

## Config

`hopping.enabled` (default `true`). When off, the brewing recipe is not registered
and the effect does nothing if applied another way. Brewing registers once at
startup, so toggling needs a restart.

## Related

- Frog Legs (#194) - the brewing reagent
- Brewed Froglights (v1.14) - the mod's other custom potion-effect content
- Config-gating convention (#196)
