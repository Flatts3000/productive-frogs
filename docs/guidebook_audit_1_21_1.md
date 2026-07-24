# Guidebook coverage audit (mc-1.21.1 Patchouli book)

Input to #343. Every shipped player-facing block, item, and mechanic on this line,
checked against the in-game Patchouli guide (`assets/productivefrogs/patchouli_books/guide/`).
Verdicts: **covered** (has an entry or a proper section), **thin** (mentioned but
under-served), **missing** (no coverage, or a passing mention only).

The book has **34 entries across 8 categories** at the time of this audit (the Slime
Milk Basin and Virtual Terrarium entries landed with their backports, #342/#341).

## Coverage table

| Surface | Entry | Verdict |
|---|---|---|
| Core loop (slime -> frog -> Froglight -> smelt) | `getting_started/core_loop` | covered |
| The six species | `frogs/species` | covered |
| Per-species slime spawning | `spawning_slimes/*` (6) | covered |
| Resource Slimes | `slimes_and_milk/resource_slimes` | covered |
| Slime Milk | `slimes_and_milk/slime_milk` | covered |
| Slime Milk catalysts | `slimes_and_milk/catalysts` | covered |
| **Slime Milk Basin** | `slimes_and_milk/slime_milk_basin` | covered (new, #342) |
| Froglights | `froglights/froglights` | covered |
| Brewed Froglights | `froglights/brewed_froglights` | covered |
| Slime Milker + Churn | `appliances/slime_milker_and_churn` | covered |
| Spawnery | `appliances/spawnery` | covered |
| Crucible | `appliances/crucible_and_mold` | covered |
| Casting Mold + molten metals | `appliances/crucible_and_mold` (1 page) | **thin** |
| **Virtual Terrarium** | `appliances/virtual_terrarium` | covered (new, #341) |
| Terrarium multiblock + build | `terrarium/terrarium` | covered |
| Terrarium Controller / Hatch / Incubator | `terrarium/*` | covered |
| Sprinkler | `terrarium/sprinkler` | **thin/stale** (no redstone control) |
| Stats & breeding | `frogs/stats_and_breeding` | covered (reflects the v1.21 rework) |
| Frog Net | `frogs/frog_net` | covered |
| Sweetslimed Lily Pad | `frogs/lily_pad_perch` | covered |
| Boss tier + Wither/Dragon altars + catalysts + receptacles | `advanced/*` (8) | covered |
| Equivalence lane (Alembic, Distiller, Midas, Princess's Kiss) | `advanced/equivalence_lane` | covered |
| Frog Egg items (bottled + primed) | `getting_started` / `frogs/species` (implicit) | thin |
| **Frog Legs (raw / cooked / soup)** | - | **missing** (1 passing mention) |
| **Froglight Cleaver** | - | **missing** (1 passing mention in `boss_tier`) |
| **Hopping potion + effect** | - | **missing** (0 mentions) |
| **Resource Tadpole Bucket** | - | **missing** (0 mentions) |

## Actions

### Missing -> new entries (all shipped features, all have `docs/`)

1. **Frog Legs** - raw drop, cook to Cooked Frog Legs, craft Frog Legs Soup. `docs/frog_legs.md`.
2. **Hopping** - the Hopping potion/effect. `docs/hopping.md`.
3. **Froglight Cleaver** - the endgame weapon crafted from renewable boss drops. `docs/froglight_weapon.md`. Goes in `advanced` (near the boss tier that feeds it).
4. **Resource Tadpole Bucket** - captures a tadpole; it keeps maturing. Parallel to the Slime Bucket. Goes in `frogs`.

### Stale -> fix in the rewrite

5. **Sprinkler** - add the redstone on/off control (v1.24.0): a redstone signal pauses a Sprinkler; each is controlled on its own (v1.24.1).

### Thin -> judged during the rewrite

- **Casting Mold / molten metals** - one page inside `crucible_and_mold`. Adequate as a shared entry; the rewrite adds a molten-metal note rather than a new entry.
- **Frog Egg items** - the bottled + primed egg items are taught implicitly by the loop entries. The rewrite makes the bottling/priming path explicit where it already comes up (core loop, species) rather than adding a standalone item entry.

### Confirmed NOT stale (verified against post-book changes)

- **Stats & Breeding** already describes the v1.21 rework (average of parents, climb-one, no regression). No change needed beyond the voice pass.
- **Hatch** entry title already reads "Terrarium Hatch" (v1.24.2 rename). Body checked in the rewrite.
- **Underwater breathing** (v1.24.5) is vanilla parity; the book never claimed otherwise, so nothing to correct.

## Part B (the voice rewrite) is tracked separately

Every one of the 34 existing entries gets rewritten against the quest-voice **Guides**
register (`F:\minecraft-repos\mc-pack-toolkit\quest-voice\voice_spec.md`), plus the
four new entries above written to the same standard. `lint_quest_voice.py` is the
backstop; the spine test is applied by hand. Client read-through in `runClient` is the
only validator - Patchouli content is never build-checked on this line.
