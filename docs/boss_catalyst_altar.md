# Boss Catalyst Altar (build spec)

> **Status: SHIPPED in v1.14.0 (#182, 2026-06-07).** Built as written. The boss-tier variants (#172/#173) + their prime-only `weight 0` mechanic shipped in the same release. The decision log at the bottom records how the shape was settled; everything below describes the shipped behavior.
>
> **Config master (#200):** the whole tier can be turned off with `boss.enabled = false`. That suppresses the four boss variants (so no priming, no source, no toxic milk, no altar in play), makes the four catalyst-altar blocks uncraftable + hidden, and drops the boss Froglight smelt-backs - the standard loop with no boss farming in one switch. The narrower `variants.bossVariantsEnabled` (#203) drops just the variants while keeping the altar blocks craftable. See [modpack_integration.md](./modpack_integration.md).
>
> Like every variant toggle this is a **soft hide**: turning the master off does not delete content already in the world. A boss slime/source/Froglight placed before the flip keeps working (a pre-existing Froglight Cleaver can still harvest a pre-existing boss slime), and re-enabling restores everything. The toggle gates *reaching* the boss tier - priming, discovery, crafting, JEI/creative - not in-world state.

## The pitch

A boss-tier resource (Wither Skeleton Skull, Nether Star, Dragon Egg, Dragon Breath) can be *primed* once you've earned the first drop, but its Slime Milk source **will not spawn** until you build an **altar**: the matching catalyst block on all six faces of the source. Complete the shell and the source arms; break one face and it pauses. Farming the rarest resources becomes a deliberate construction, not a placed bucket.

## Two gates, one resource

The boss tier is the only content in the mod behind two independent gates:

1. **Prime-only (`weight 0`, already shipped):** the variant never appears in split-discovery, so the *first* one must be primed with the real item - kill the wither, sacrifice the world's one dragon egg. The frog loop amplifies what you earned; it never hands you a boss resource from a lucky roll.
2. **Catalyst altar (this spec):** even with a primed slime milked into a source, the source spawns nothing until the 6-face altar is complete. This gates *scaling* the farm.

Neither replaces the other. Prime-only gates entry; the altar gates throughput.

## Data model: the `spawn_catalyst` variant field

Add one optional field to `SlimeVariant` (and its `CODEC`), defaulting to `false`:

```java
boolean spawnCatalyst   // codec: Codec.BOOL.optionalFieldOf("spawn_catalyst", false)
```

- Decoupled from `weight` on purpose - "requires an altar" and "excluded from discovery" are independent concepts. A future non-boss variant could want an altar without being prime-only, or vice versa. The four boss variants ship with **both** (`weight: 0` + `spawn_catalyst: true`).
- Generic and data-driven: the *field* is open to any variant. Only the catalyst **blocks** (below) are hardcoded to the four - see the extensibility note.

## The four catalyst blocks

Four hand-registered blocks (NOT the configurable-froglight one-block-tinted pattern - these must each look genuinely different, decision #184):

| Block id | Arms the source variant | Texture theme |
|---|---|---|
| `nether_star_catalyst` | `productivefrogs:nether_star` | beacon/star radiance |
| `dragon_egg_catalyst` | `productivefrogs:dragon_egg` | black shell, purple sheen |
| `wither_skeleton_skull_catalyst` | `productivefrogs:wither_skeleton_skull` | charred bone / soul |
| `dragon_breath_catalyst` | `productivefrogs:dragon_breath` | pink-purple swirl |

- Registered in `PFBlocks` + `BlockItem`s in `PFItems` + creative-tab entries in `PFCreativeTabs` (grouped after the appliances). Standard full-cube blocks; no BlockEntity needed (they hold no state - they're checked by the source's tick).
- A static map `Map<ResourceLocation variantId, Block catalyst>` (or a switch) is the single source of truth wiring each boss variant to its catalyst block. The 6-face check and the recipe generator both read it.
- **Art via the gen/ pipeline** (gpt-image-1, the Crucible/Mold precedent): four 16x16 block textures, picked from `comparison.html`, NN-downscaled + 16-color quantized. Front-load this before the Java so the blocks register against real textures.

**Extensibility tradeoff (on the record):** hardcoding four blocks makes boss-catalyst farming a vanilla-only, closed set. A pack adding its own `spawn_catalyst: true` variant via world datapack would have no catalyst block and couldn't farm it. Accepted: the boss tier is curated and vanilla-by-definition; nobody adds a 5th vanilla boss. If a datapack boss ever needs farming, that's a future "generic fallback catalyst" item, not this spec.

## The gate: where it lives

`SlimeMilkSourceBlock#tick` already early-returns on two "pause without spending budget" conditions (exhausted; density-capped). The catalyst gate is a **third**, inserted immediately after the density-cap check and before `spawnBatch`:

```java
// Boss-tier altar gate: a spawn_catalyst variant spawns nothing until the
// matching catalyst block is on all six faces. Pause (no spawn, no
// decrement) and reschedule, like the density cap - so the altar can be
// completed later without the source draining in the meantime.
if (requiresCatalyst(level, variantId) && !altarComplete(level, pos, variantId)) {
    scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
    return;
}
```

- `requiresCatalyst` resolves the variant from the registry and reads `spawnCatalyst()`.
- `altarComplete` checks the six `Direction` neighbours of `pos` are all the catalyst block mapped to `variantId`. Block-id equality (`state.is(catalystBlock)`) - no component match needed since the catalyst is per-resource.
- **Pause semantics, not destroy** (decision #184): an incomplete altar freezes the source - `spawns_remaining` is untouched, the source persists, and it resumes the instant the shell completes. Tearing down a face mid-farm just pauses; it never wastes budget or drains the source.
- Non-boss sources never hit this branch (`spawn_catalyst` defaults false), so the overwhelming majority of sources pay only one extra registry lookup + boolean check per tick. Cheap.

## Catalyst recipe: sacrifice to scale

Each catalyst is crafted **consuming its own boss resource** (decision #184) - you spend nether stars to build the farm that makes nether stars, so the resource stays meaningful even once farmable. Proposal (tune in the PR):

- `nether_star_catalyst` = nether star + 8 surrounding (obsidian? crying obsidian?) - one star per catalyst, six catalysts per altar = six stars to arm one farm.
- The other three follow the same shape (the resource centered in a frame).

The six-stars-per-altar cost is the real sink; it makes the first altar a genuine project and keeps boss resources from trivializing once the loop closes.

## Toxic boss milk (the narrative glue)

Boss-tier Slime Milk is **toxic to players** - standing in it inflicts Wither. This is *why* you cage a boss source in an altar: the milk is dangerous, so you contain it. `ToxicMilkHandler` (`PlayerTickEvent.Post`, server-only, once a second) applies Wither I to any player standing in a boss variant's milk.

- **Players only** (decided): the boss slimes a source spawns wade through their own milk and must not poison themselves; non-player entities are untouched. Creative/spectator exempt.
- **Same closed boss set** as the catalyst blocks (`PFBlocks.catalystForVariant().keySet()`); both source and flowing milk of a variant share one `FluidType`, so `isInFluidType` catches either.
- **Independent of the altar gate**: toxicity is always-on for boss milk wherever it sits; the altar gates *spawning*, the toxicity gives the *reason* to wall it in. Wither (not Poison) because it can actually kill - the boss-tier "get out" signal - but it's escapable.
- **No GameTest**: `PlayerTickEvent` doesn't fire for gametest mock players, and `isInFluidType` needs real fluid physics - manual `runClient` verification (wade into boss milk, take Wither; non-boss milk is harmless; creative is exempt).

## UX / feedback

- **Jade line on the source**: `Catalyst altar: N/6` (the source BE is already Jade-annotated for spawns-remaining; add the line in the same provider, computed from the six neighbours). Makes a broken shell debuggable at a glance.
- **Armed visual**: when the altar is complete, the source emits a few ambient particles (an `animateTick` branch on the source block, gated on `requiresCatalyst && altarComplete`) so "it's working" reads without opening Jade. Reuse the effect-particle approach from the brewed-Froglight aura.
- Each catalyst block's item tooltip names which resource it arms.

## GameTests

- `bossSourcePausesWithoutFullAltar`: place a `nether_star` source with 5/6 catalysts; after a generous delay assert no slimes spawned **and** `spawns_remaining` is unchanged (pause, not waste). Complete the 6th face; assert spawning resumes.
- `bossSourceRejectsMismatchedCatalyst`: a `dragon_egg_catalyst` on a `nether_star` source does not count toward the six.
- `nonBossSourceIgnoresCatalyst`: a normal variant source (e.g. `iron`) spawns with no catalysts present (the default-false field never gates it).
- All three use the existing milk-source test harness (`spawnCapOverride` etc.); no client visuals (those ride a manual `runClient` pass).

## Registration / wiring checklist

- [ ] `SlimeVariant`: add `spawnCatalyst` field + codec entry (`optionalFieldOf("spawn_catalyst", false)`); the four boss variant JSONs gain `"spawn_catalyst": true`.
- [ ] `PFBlocks`: four catalyst blocks (full cube, stone-ish strength). `PFItems`: four BlockItems. `PFCreativeTabs`: accept after the appliances.
- [ ] The `variantId -> catalyst Block` map (one source of truth for the gate + the recipe gen).
- [ ] `SlimeMilkSourceBlock#tick`: the gate branch; `requiresCatalyst` + `altarComplete` helpers.
- [ ] Blockstates + models + four gen/ textures; loot tables; `mineable/pickaxe` tag; lang (4 block names + 4 item tooltips).
- [ ] Four crafting recipes (resource-consuming).
- [ ] Jade `N/6` line; armed-particle `animateTick`.
- [ ] Three GameTests.

## Decision log (2026-06-07, issue #184)

1. **Mechanic**: 6-face catalyst shell gates the boss milk source's spawn. Loose adjacency (V1-legal, the Crucible-tower family), not a formed multiblock.
2. **Stacks with prime-only**, doesn't replace it - two gates (earn the first / build to farm).
3. **Four distinct blocks, bespoke art** - not one tinted configurable block ("they each would look differently").
4. **`spawn_catalyst` is a generic variant field**; only the catalyst blocks are hardcoded to the four (extensibility = non-goal for the boss tier).
5. **Pause, don't destroy** - an incomplete altar freezes the source; no budget waste.
6. **Catalyst consumes the boss resource** - sacrifice-to-scale economic loop.
7. **All six faces, strict** - no data-driven count (revisit only if a lighter gate is ever wanted).
8. **Boss milk is toxic to players** (Wither), players-only - the narrative reason to contain a boss source in the altar (added 2026-06-07).
