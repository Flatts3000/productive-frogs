# Sweetslimed Lily Pad (frog perch)

> **Status: IMPLEMENTED** (issue #214, 2026-06-13). A perch that pins a working Resource
> Frog over a spot without leashing it. Net = move a frog; Terrarium = contain a loop;
> **lily pad = pin one frog over a collection point** (e.g. on/next to a hopper beside a
> Slime Milk source) so it eats slimes and drops Froglights in place.
>
> **Implementation note (as-built):** the perch is driven by the pad's BlockEntity, not
> by a frog-brain behavior as the AI section below originally sketched. The pad BE does a
> cheap entity scan, claims the nearest free frog (one-per-pad), and nudges it back via
> the vanilla `WALK_TARGET` memory when it strays and isn't hunting; the claim is a short
> expiring link stored on the frog (`ResourceFrog#getActivePerch`), re-asserted each scan
> and auto-lapsing when the pad is broken. This needs no new memory type, sensor, or
> brain-priority surgery, is GameTest-verifiable, and yields the identical player-facing
> behavior. The fine pinning feel (how tightly the frog holds) is the one piece that
> still wants a `runClient` pass to tune (`STRAY_NUDGE_SQ` / nudge cadence).

## Decisions (locked with the maintainer)

- **D1 - Permanent.** The pad stays sweetslimed until broken. No wear-off timer.
- **D2 - One frog per pad.** The first Resource Frog to reach an unclaimed pad claims it;
  other frogs ignore a claimed pad. So N pads over N hoppers pin N frogs deterministically.
- **D3 - Range 16 blocks** (config default `lily_pad_perch.range`), matching the max
  prey-scan reach. A frog within range paths to the nearest unclaimed pad.
- **D4 - No pose change (v1).** Perching holds position only; the frog keeps its normal
  model/animation. A settled "sitting" pose is deferred polish.
- **D5 - Resource Frogs only.** We own the Resource Frog brain; vanilla/modded frogs
  can't have a behavior injected. Documented v1 limit; a generic attractant could come later.
- **D6 - Config-gated** (`lily_pad_perch.enabled`, default true): when off, the create
  interaction is inert and the block/item is hidden from the creative tab.

## Behavior

- **Create:** right-click a placed `minecraft:lily_pad` with a **Sweetslime** ->
  it is replaced by a `productivefrogs:sweetslimed_lily_pad` and one Sweetslime is
  consumed (creative keeps it). An event hook (`PlayerInteractEvent.RightClickBlock`),
  not a crafting recipe - mirrors `EggPrimerHandler` / `FrogspawnBottlingHandler`.
- **Attract + perch:** a Resource Frog within `range` of an unclaimed pad walks to it,
  claims it, and **holds position** there - its random stroll is anchored to the pad so
  it stops wandering off.
- **Still farms while perched (critical):** the perch suppresses *wandering only*. The
  prey sensor (`ResourceFrogAttackablesSensor`), tongue, and Froglight drop keep working,
  so a pinned frog eats same-species slimes in range and produces over the hopper. A
  short hunt that pulls the frog off the pad is fine - it returns to the pad afterward.
- **Release:** breaking the pad clears the claim and the frog wanders again. A frog whose
  pad is gone (broken, unloaded) drops its perch and reverts to normal AI.

## The pad block

- New block `sweetslimed_lily_pad` (a lily pad with a faint slime sheen). Mirrors vanilla
  `WaterlilyBlock`: sits on water, same placement survival rules, instant break.
- **Has a small BlockEntity** storing the **claimant frog UUID** (nullable) - the
  one-frog-per-pad claim (D2). The claim is **lazily validated**: if the stored UUID
  resolves to no living frog (died / unloaded / unperched), the pad is reclaimable. This
  is the only state the BE holds (no timer - D1).
- Drops itself as an item when broken (reusable perch); the item is creative-only
  (no recipe - it is made by the create interaction).
- Texture: procedural (Pillow script per the project's no-PixelLab rule) - the vanilla
  lily-pad texture with a translucent slime-green sheen.

## The perch AI (the load-bearing part)

On the Resource Frog brain:

1. **Memory** - register a new `MemoryModuleType<GlobalPos>` `PERCH_POS` (vanilla
   `Frog.MEMORY_TYPES` omits it, so register it in `brainProvider` exactly like
   `HAS_HUNTING_COOLDOWN` already is).
2. **Sensor** - `SweetslimedPadSensor` (new `PFSensors` entry): scans within `range` for
   the nearest `sweetslimed_lily_pad` whose claim is free (or already this frog's), claims
   it on the pad BE, and writes `PERCH_POS`. Clears `PERCH_POS` (and releases the claim)
   when the pad is gone or out of range.
3. **Behavior** - `PerchOnPad` (added via `brain.addActivityWithConditions`, re-supplying
   vanilla's requirement set per the brain-activity gotcha - a bare `addActivity` wipes the
   land/water gating): when `PERCH_POS` is present and the frog has drifted past ~1 block
   from it, set `WALK_TARGET` back to the pad. This anchors the frog like a leash without
   removing the stroll behavior - the frog keeps returning to the pad. It runs at a priority
   that competes with the idle stroll but yields to the existing prey/eat behavior, so
   hunting still works and the frog re-anchors after each eat.

**Priority/tuning is the main implementation risk** and needs in-world iteration
(GameTest + a manual `runClient` pass - AI is invisible to GameTest visuals). Start the
perch behavior alongside the stroll in `IDLE`/`SWIM`, below the prey behavior.

## Config (`PFConfig`)

| Key | Default | Meaning |
|---|---|---|
| `lily_pad_perch.enabled` | true | master switch; when off the create interaction is inert and the block/item is hidden from the creative tab |
| `lily_pad_perch.range` | 16 | blocks a frog will travel to reach a pad |

Note: the perch reuses **Sweetslime**, which is itself gated by `frog_stats.enabled`
(it is the breeding item). With the stat layer off, Sweetslime is uncraftable, so the
perch is only reachable with a creative/leftover Sweetslime. Acceptable coupling - the
issue's whole premise is giving Sweetslime a second use; documented, not worked around.

## Implementation outline

- `content/block/SweetslimedLilyPadBlock` (+ `content/block/entity/SweetslimedLilyPadBlockEntity`
  holding the claimant UUID).
- `event/LilyPadPerchHandler` - the `RightClickBlock` create hook (`@EventBusSubscriber`).
- `content/entity/ai/SweetslimedPadSensor` + a `PERCH_POS` memory + `PerchOnPad` behavior;
  wire into `ResourceFrog#brainProvider` and `#makeBrain`.
- Registry: `PFBlocks` (block), `PFItems` (block item), `PFBlockEntities` (pad BE),
  `PFSensors` (pad sensor), creative tab entry; respect constructor ordering
  (`PFBlocks` before `PFBlockEntities`).
- `PFConfig` - the two keys above.
- Assets: blockstate + model + texture (procedural) + `en_us.json` lang
  (block name + any tooltip). Datagen if the block needs a loot table to drop itself.
- Tests:
  - **GameTest:** a Resource Frog within range walks to a pad and holds near it; a second
    frog does not claim a pad already claimed (one-per-pad); a perched frog still eats a
    slime in range and drops a Froglight; breaking the pad frees the frog.
  - **runClient (manual):** the perch reads naturally (frog settles on the pad, still
    hunts), the pad texture renders, the create interaction consumes one Sweetslime.

## Out of scope (v1)

- Vanilla / modded frog support (D5).
- A sitting/settled pose (D4) - deferred polish.
- Species-specific pads - a pad is generic; you pin the species you want by placing that
  frog nearest. Per-species pads could come later if a multi-species farm wants it.
- Wear-off / consumable pads (D1).

## Related

- Sweetslime (`ResourceFrog#isFood`) - this is its second use.
- Frog AI: `ResourceFrogAttackablesSensor` + `brainProvider` (the eat path must keep
  working while perched); brain-activity gotcha (`addActivityWithConditions`).
- Positioning siblings: Frog Net (#205, relocate), Terrarium (#185, contain).
- Config-gating convention (#196).
