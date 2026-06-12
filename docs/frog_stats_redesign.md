# Frog Stats Redesign: Talent + Training

> **Status: IMPLEMENTED** (branch `feat/frog-stats-talent-training`, 2026-06-12).
> Reworks the Appetite / Bounty / Reach mechanic from a one-shot breeding lottery into a
> two-layer **Talent (bred ceiling) + Training (earned by play)** system. Supersedes
> decisions **D3** (mechanism is breeding) and **D7** (hi-biased inheritance with
> regression) in [frog_breeding.md](./frog_breeding.md); that doc remains accurate for
> everything this didn't change (stat definitions, effect curves, Sweetslime, the
> same-species gate, the carry pipeline).
>
> **Existing frogs are never reset (R7).** On load, a pre-redesign frog keeps its earned
> stats (live = the saved stat) and gets a talent ceiling at least that high, so it plays
> exactly as it did before the update - verified by the
> `migrationPreservesExistingFrogStats` GameTest.
>
> Master switch unchanged: the whole layer still drops with `frog_stats.enabled = false`.

## Why change it

The shipped system works correctly end-to-end (inheritance math, the
conception -> egg BE -> tadpole -> frog stat carry, the bucket round-trip, the Jade
preview - all verified). The problem is not a bug; it is **feel**, and it fails four
modern progression-design tests at once:

1. **It is a slot machine, not a decision.** Feed Sweetslime, pray, read Jade, cull.
   The only choice is which pair to breed; the outcome is dice. No skill expression,
   no plan a player executes.
2. **It bakes in loss aversion.** The `regressionChance` roll moves a stat *backward*.
   Breeding a `10/10/10` champion with a fresh `1/1/1` has a ~66% chance at least one
   offspring stat comes out *worse than the better parent*. "I bred my best frog and
   got a worse one" is the canonical way a genetics system feels broken.
3. **Improvement is divorced from the core loop.** To progress you *stop* farming and
   run a separate breed/cull minigame. The activity that is fun (running the farm) and
   the activity that advances you (breeding) are different things.
4. **Progress is invisible.** Cosmetic tiers are still deferred, so a maxed prize frog
   and a fresh dud look identical - no bar, no level, no glow-up.

## The model

Each stat (Appetite, Bounty, Reach) splits into two numbers:

| Layer | Range | Set by | Drives |
|---|---|---|---|
| **Talent** (ceiling) | `[STAT_MIN, statCap]` | **Breeding** (inheritance) + a default for non-bred frogs | The maximum the live stat can be trained to |
| **Trained value** (live stat) | `[STAT_MIN, Talent]` | **Training** (XP from the core loop) + player-directed allocation | All behavior: eat cadence, drop count, scan radius |

A frog's **behaviour reads the live trained value**, not the talent. A freshly bred
high-talent frog starts *weak* (live `1/1/1`) and must be trained up by working - so
the climb becomes the visible, earned, fun part. Breeding gives you *potential*;
training *realizes* it.

This is the Pokemon IV/EV split, reframed: bred genetics set the ceiling, play fills
toward it. It composes with - rather than throws away - the entire breeding pipeline
already built.

### Three overlapping progression vectors (the arc)

- **Early:** craft/Spawnery frogs have a modest default talent (config, default `3/3/3`).
  Farm with them and they visibly train up to that ceiling. Immediate feedback, no
  breeding required to start progressing.
- **Mid:** breed to *raise ceilings*. A bred frog has higher talent than its default,
  so there is headroom to train into.
- **Late:** chase a `10/10/10`-talent lineage AND fully train them. Two axes to max,
  not one terminal state.

## Training (the new core)

### Earning XP

A frog earns **training XP by eating slimes** - the existing core loop. Hook point:
`FrogTongueDropHandler.onResourceSlimeKilled` (right where `startEatCooldown()` already
fires on every eat), plus the player direct-feed path in `ResourceFrog.mobInteract`.

- `stats.trainingXpPerEat` (default e.g. `1`) XP per slime eaten.
- Optional small bonus for variant slimes vs category-only, if we want to reward
  farming "real" resources - deferred, flagged below.

### Leveling and allocation

XP accumulates toward a **level**; each level grants **one stat point** to spend, and
points can only raise a stat up to its Talent.

- A frog can earn at most `(T_app - 1) + (T_bounty - 1) + (T_reach - 1)` points
  (each live stat starts at `STAT_MIN`). Once every live stat sits at its Talent the
  frog is **fully trained for its talent** and banks no further XP (or banks it
  harmlessly, hidden).
- Level curve: `stats.trainingXpPerLevel` flat cost, or a gentle ramp
  (`base * level`). Default a flat cost for predictability; config-tunable.

**Allocation - the agency choice (resolved: Training Focus, no GUI).** On level-up the
frog gains a point and pours it into its **Focus** stat:

- Sneak-right-click a frog with an empty hand **cycles its Focus**:
  `Appetite -> Bounty -> Reach -> Auto`. The current Focus shows in the Jade tooltip
  and via a brief actionbar / particle cue.
- Earned points flow into the Focus stat until it reaches its Talent, then overflow to
  the next-lowest live stat (so a point is never wasted).
- `Auto` (and the default for an un-touched frog) fills the lowest live stat first -
  balanced growth with zero player input.

This delivers the "choice that matters" the current system lacks **without a container
GUI** - it matches the mod's "mirror a vanilla interaction" ethos (sneak-interact is
free real estate; vanilla uses it for nothing on a frog).

> **Simpler fallback if Focus feels like too much:** drop the cycle entirely and always
> auto-fill the lowest live stat on level-up. Pure idle-farm leveling, closest to the
> "botany" feel one playtester called out. The Focus layer is additive on top, so we
> can ship auto-fill first and add Focus later with no data change.

## Breeding becomes the Talent layer

Breeding is otherwise unchanged (same-species gate, Sweetslime trigger, the whole
conception -> egg -> tadpole carry). Two changes:

1. **Inheritance sets the offspring's Talents, not its live stats**, with a
   **no-regression rule**:
   - with probability `breeding.improvementChance` (default `0.40`): `min(cap, hi + 1)`
   - else: `hi` (hold at the better parent's *talent*)
   - **The regression-to-mean branch is removed.** A breed can never lower a talent
     below the better parent. `regressionChance` is deleted from the config.
   `hi`/`lo` are computed from the parents' **Talents**.
2. **The offspring matures with live stats at baseline** (`STAT_MIN` across the board)
   and its inherited Talents as ceilings - so it must be trained up, exactly like any
   other frog.

The existing `pendingOffspring{Appetite,Bounty,Reach}` payload (carried conception ->
egg BE -> tadpole -> frog) is **reinterpreted as Talents**. No new carry fields, no new
plumbing - the three ints that flow through the pipeline today simply become talents,
and `ResourceTadpole.ageUp` sets `frog.setTalents(...)` + live stats `= STAT_MIN`
instead of `frog.setStats(...)`.

## Cosmetic tiers (now meaningful)

Key the deferred render tiers off the **live `statTotal`** (sum of trained values), so
the glow-up tracks the player's training effort and climbs in real time as a frog
works - the visible feedback the current system never had. The maxed look fires at full
training of a `10/10/10`-talent frog. (Talent could drive a *secondary* cue - e.g. a
faint aura denoting a high-ceiling bloodline - but that is optional polish.)

## Config (`PFConfig`, COMMON)

| Key | Default | Meaning | Change |
|---|---|---|---|
| `frog_stats.enabled` | true | master switch for the whole layer | unchanged |
| `breeding.sameSpeciesOnly` | true | same-species breeding gate | unchanged |
| `breeding.improvementChance` | 0.40 | per-stat chance a bred **talent** rolls `hi + 1` | repurposed to talents |
| `breeding.regressionChance` | - | - | **removed** (no-regression) |
| `breeding.nonBredTalentDefault` | 3 | talent ceiling for crafted / Spawnery / non-bred frogs | **new** |
| `breeding.statCap` | 10 | max talent (and thus max live stat) | unchanged |
| `stats.trainingXpPerEat` | 1 | training XP a frog earns per slime eaten | **new** |
| `stats.trainingXpPerLevel` | e.g. 8 | XP per level (one point per level) | **new** |
| `stats.appetiteCooldownMin` / `Max` | 30 / 100 | eat cooldown at live-stat 10 / 1 | unchanged (reads live) |
| `stats.bountyMaxDrops` | 3 | Froglights per slime at live-stat 10 | unchanged (reads live) |
| `stats.reachRadiusMin` / `Max` | 8 / 16 | scan radius at live-stat 1 / 10 | unchanged (reads live) |
| `frogs.persistent` | true | Resource Frogs do not despawn | unchanged |
| lifecycle.* | - | hatch / growth / breed-cooldown timers | unchanged |

`stats.trainingFocusEnabled` (default true) toggles the Focus cycle; off = pure
auto-fill.

## Data model (entity)

`ResourceFrog` synced data + NBT:

- **Keep** `DATA_APPETITE` / `DATA_BOUNTY` / `DATA_REACH` - these are now the **live
  trained** stats (behavior reads them via `effectiveAppetite/Bounty/Reach`, which gain
  the live-vs-talent clamp; the config-off baseline path is unchanged).
- **Add** `DATA_TALENT_APPETITE` / `DATA_TALENT_BOUNTY` / `DATA_TALENT_REACH` (synced
  for the Jade `value/talent` readout), `DATA_TRAINING_XP`, `DATA_FOCUS` (enum ordinal).
- NBT persists all of the above. Live stats clamp to `[STAT_MIN, talent]`; talents clamp
  to `[STAT_MIN, statCap]` on every read/write (same tamper-proofing as today).
- The `pendingOffspring*` fields and the egg-BE / tadpole carry are unchanged in shape;
  their meaning shifts from "final stats" to "talents."

### Migration (existing saves)

An existing frog has `Appetite/Bounty/Reach` in NBT under the old meaning (its final
stats). On load:

- `liveStat = storedStat` (no one loses power they earned).
- `talent = max(storedStat, nonBredTalentDefault)` (its ceiling = where it already is,
  so it stays valid; breeding can raise it further).
- `trainingXp = 0`, `focus = Auto`.

A migrated frog is therefore "fully trained for its current talent" and plays exactly
as it did before the update - the redesign is non-breaking for live worlds.

## Implementation outline

- **`FrogStats`** - add `inheritTalent(hiTalent, loTalent, improvementChance, cap, rng)`
  (no-regression; the old `inheritStat` is removed once call sites move). Add the
  level-curve helpers (`xpToLevel`, `pointsForLevel`) as pure, unit-testable functions.
- **`ResourceFrog`** - talent accessors + live-stat clamp-to-talent; `addTrainingXp(int)`
  (accumulate, level up, allocate per Focus/Auto, fire the level-up cue); `cycleFocus()`
  off sneak-interact in `mobInteract`; redefine `effective*` to read live stats.
- **`ResourceTadpole.ageUp`** - set talents from the pending payload, live stats to
  `STAT_MIN`.
- **`FrogTongueDropHandler.onResourceSlimeKilled`** - `frog.addTrainingXp(perEat)` beside
  the existing `startEatCooldown()`.
- **`ResourceFrog.mobInteract`** - sneak + empty hand -> `cycleFocus()`; direct-feed path
  also grants training XP.
- **`ProductiveFrogsJadePlugin`** - show `live/talent` per stat + level + Focus (the egg
  / tadpole preview now shows inherited *talents*).
- **`ResourceFrogRenderer`** - wire the cosmetic tier off live `statTotal` (closes the
  long-deferred art hook).
- **`PFConfig`** - the table above.
- **`docs/frog_breeding.md`** - fold this in; supersede D3/D7.

## Testing

- **Unit (JUnit):** `inheritTalent` (improve / hold, the `min(cap, hi+1)` cap, equal-parent
  no-op, **never below hi**); XP -> level -> points curve; allocation clamps to talent and
  overflows to the next stat; Focus cycle order; the migration mapping
  (`talent = max(stored, default)`, `live = stored`); config defaults.
- **GameTest:** a frog eating slimes gains XP and levels; a level-up raises the focused
  live stat and the Bounty curve reflects it on the next kill; a non-bred frog trains up
  to `nonBredTalentDefault` then stalls (no further live gain); a bred frog has a higher
  talent ceiling than a non-bred one and can train past the default; cross-species frogs
  still do not breed.
- **runClient (manual; GameTest is blind to visuals):** Jade shows `live/talent` + level
  + Focus; sneak-interact cycles Focus with a cue; the cosmetic tier brightens as a frog
  trains up.

## Decisions (this redesign)

- **R1 - Two layers: Talent (bred) + Training (earned).** Breeding sets the ceiling,
  play fills it. Supersedes D3 (breeding is no longer the *only* improvement mechanism)
  and reuses D4's same-species gate untouched.
- **R2 - No regression, ever.** Bred talents only hold or climb; trained stats only
  hold or climb. Earned progress is never taken away. Supersedes D7.
- **R3 - Improve by playing.** The core loop (frog eats slime) is the XP source, making
  progression self-reinforcing rather than a side minigame.
- **R4 - Agency via Training Focus, not a GUI.** Sneak-interact cycles the stat a frog
  trains toward; auto-fill is the zero-input default and the shippable fallback.
- **R5 - Non-bred frogs are usable** (default talent `3/3/3`, config) but breeding is the
  way to raise the ceiling - so breeding stays meaningful without gating early play.
- **R6 - Reuse the carry pipeline.** The conception -> egg -> tadpole -> frog payload is
  reinterpreted as talents; no new plumbing, no save-shape churn.
- **R7 - Non-breaking migration.** Existing frogs keep their power; their talent ceiling
  is set to their current stat so they read as fully-trained.

## Open questions

- **Variant-slime XP bonus?** Should eating a variant (resource) slime grant more
  training XP than a category-only slime, to reward farming real resources? Deferred -
  start flat, revisit if early game feels too slow.
- **Level cap visibility.** Show a numeric level (`Lv 7`) or only the per-stat
  `live/talent` bars in Jade? Leaning toward both (level as the headline, bars as detail).
- **Does Talent want its own cosmetic cue** (high-ceiling bloodline aura) separate from
  the live-total tier, or is one tier enough? Polish-tier decision.
- **Sweetslime's role.** Still the breeding trigger (unchanged). Do we *also* want a
  training treat that grants a small XP burst, or keep training strictly play-earned?
  Leaning play-earned to protect R3.

## Related

- Current shipped system: [frog_breeding.md](./frog_breeding.md)
- Productive Bees comparison: [productive_bees_analysis.md](./productive_bees_analysis.md)
- Scope: [versioning.md](./versioning.md), [../ROADMAP.md](../ROADMAP.md)
