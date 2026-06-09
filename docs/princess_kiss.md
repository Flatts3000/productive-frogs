# Princess's Kiss

The Princess's Kiss (issue #216) is a rare Ender Dragon drop that turns a frog
into a villager - the Frog Prince fairy tale, mechanically modelled on the
zombie-villager cure (a timed conversion with particles, not an instant swap).

## Behavior

- **Drop:** the Ender Dragon (the princess) drops **1 Princess's Kiss** when slain.
- **Use:** right-click **any frog** ({@code PFEntityTags.isFrog} - vanilla,
  Resource, or a modded frog) with the Kiss to start the conversion; the Kiss is
  consumed.
- **Convert:** over ~10 seconds (200 ticks) the frog emits hearts, then is replaced
  by a **plain villager** - unemployed (can take a profession), never a nitwit. A
  custom-named frog passes its name to the villager.
- One Kiss bootstraps a villager economy (breed for more), so the dragon gate is
  the cost.

## Design

- **Item** `PFItems.PRINCESS_KISS` ({@code PrincessKissItem}); `interactLivingEntity`
  seeds the conversion attachment and consumes the Kiss.
- **Timed conversion on any frog:** the countdown lives in the
  {@code PFAttachments.PRINCESS_CONVERTING} NeoForge data attachment - not entity
  fields - so it works on frogs we don't own and survives a save/reload (it
  serializes, like the zombie cure is reload-safe). `PrincessKissHandler` ticks it
  in `EntityTickEvent.Post` and converts at zero.
- **Dragon drop:** the Ender Dragon's death is custom (it skips the normal loot
  path), so `PrincessKissHandler` handles `LivingDeathEvent` for the dragon and
  spawns the Kiss at the dragon's position.
- **Config** `princess_kiss.enabled` gates the drop, the item visibility (creative
  + JEI), and the conversion (an in-progress conversion cancels cleanly if the
  feature is turned off).

## Tests

- **GameTest** `princessKissConvertsFrogToVillager` - seed the attachment, let the
  handler tick, assert the frog is replaced by a villager.

The item texture (a pink kiss mark) and the conversion particles need a `runClient`
eyeball; GameTest is blind to rendering.

## Related

- Shared frog predicate: `PFEntityTags.isFrog` (Frog Net #205, Frog Legs #194)
- Vanilla reference: the zombie-villager cure (timed, particles, reload-safe)
