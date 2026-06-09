# Terrarium (build spec)

> **Status: SHIPPED in v1.16.0** (PR #193, 2026-06-08). The flagship multiblock frog habitat. Per the maintainer ruling **"V2 is just a name, not a rule"** (2026-06-08), the old "must not land in a V1.x branch" gate is dropped - it ships in the **1.x line**, not a 2.0.0. The full loop is implemented in one PR: structure + validation (the four blocks, the facing-aware candidate validator, `TerrariumManager`, config), the milk path (Controller charge buffer + Sprinkler spawn loop + component-preserving fluid wrapper + round-robin distribution + cavity cap), froglight output (direct-to-Hatch + two-layer backpressure), the Incubator (stat relay + frog-cap hold + breeding-lay redirect), Infernal-tier recipes, per-face block textures + oriented models, three GUIs (Controller/Incubator/Hatch), JEI info pages, and Jade look-at tooltips. **Construction guidance** ships as the native Controller validation feedback (right-click -> formed state + first structural problem in the Controller GUI); the **GuideME 3D scene is a deferred, separately-verified follow-up** (it needs a new dependency and a guidebook format that can't be CI-verified). Issue #185 holds the settled product rulings, reproduced in the decision log at the bottom.

## The pitch

A **Terrarium** is a multiblock frog habitat that contains the whole frog loop in one sealed box: pipe Slime Milk into a **Controller**, it feeds **Sprinklers** in the ceiling that rain slimes into the cavity, frogs (introduced through **Incubators**) eat them, and the resulting Froglights materialize directly in a **Hatch** inventory for piping out. Build it once, seal it, automate it. It is the V2 payoff that the v1.7 milk catalysts were a hand-operated stopgap toward.

## Scope guardrails (read before writing code)

- **V2, multiblock, no power.** The Terrarium is fed by milk and frogs, not energy. No FE/NeoForge-Energy dependency in this feature (power compat is a separate, later V2 line). It is "automation by structure," matching the V1 ethos of staying close to vanilla mechanics.
- **Reuses V1 mechanics wholesale.** The Sprinkler IS a placed Slime Milk source in a ceiling cell - it must reuse `SlimeMilkSourceBlock`'s spawn loop and `SlimeMilkSourceBlockEntity`'s budget/catalyst state, not reimplement them. The Incubator reuses the v1.5 stat-carry lineage. The Hatch overrides the v1.14 froglight drop. Net-new code is the multiblock validation, the Controller distribution, and the four machine blocks' shells - everything inside the loop is existing V1 behavior relocated.
- **Loose-adjacency multiblock, not a formed-structure registry.** Like the v1.14 catalyst altar and the Crucible heat tower, validation is a per-tick / on-demand neighbour scan, not a `StructureTemplate` match. V1-legal pattern, no new framework.

## Structure

The geometry, verbatim from the settled rulings:

- **The 5x4x5 is the INTERIOR cavity, not the walls.** It is the open air space the frogs live in: 5x5 in plan, **4 blocks high**. The height is capped at 4 because slimes spawned from ceiling Sprinklers take fatal fall damage from a taller drop. With a one-block-thick shell on every face, the full footprint is **7x6x7 outside**. The shell is not counted in the 5x4x5.
- **Interior (the 5x4x5 cavity): unrestricted.** Air, blocks, entities, decorations - anything. Validation never inspects the interior except to count entities for the caps.
- **Shell (the 6 faces wrapping the cavity): any solid block.** Only `state.isSolidRender` / full-cube solidity is required (themeable), with the machine blocks and Sprinklers as the allowed exceptions that also satisfy "shell is sealed."
- **Machine blocks sit IN the shell, replacing shell blocks, FACE positions only.** Controller / Incubator(s) / Hatch each occupy one shell cell on one of the six faces, flat against the cavity. **Corners and edges are banned** - a corner/edge cell has no unambiguous inside/outside axis.
- **Directionality:** Controller, Incubators, and Hatch are `FACING`-blocks placed so one face points INTO the cavity (habitat interaction) and the opposite face points OUT (GUI / piping). Validation requires the inward face to be the cavity-facing one.
- **Sprinklers go in the CEILING:** the 5x5 ceiling cells directly over the cavity accept **up to 25 Sprinklers** (full coverage allowed). A Sprinkler occupies a ceiling cell, counts as sealed shell, and implicitly faces down.

### Why loose-adjacency works here

The cavity is axis-aligned and the shell is exactly one block thick, so the structure is fully described by a single anchor (the Controller) plus the known 7x6x7 offsets. Validation does not need a template: from the Controller's position and facing, derive the cavity bounds, then check every shell cell. This is the same family as the catalyst altar's 6-face scan, just larger.

## The four machine blocks

| Block | Count | Placement | Role |
|---|---|---|---|
| **Terrarium Controller** | exactly 1 | shell face, in/out axis | The multiblock anchor and the single milk input. Holds a small **charge buffer** (not a blended tank - see storage model) of one variant at a time. Validates the structure. Distributes milk **round-robin to empty Sprinklers** and **auto-tops-up** draining Sprinklers of the matching variant. Exposes a fluid-handler capability + a bucket slot on its outward face. |
| **Sprinkler** | up to 25 | ceiling cells over the cavity | Spawn source AND storage. Holds **one bucket** of milk (full stats), retains its variant, and **runs the existing placed-Slime-Milk-source mechanics** (catalysts included) spawning slimes down into the cavity. **Drain: right-click with an empty bucket** returns the per-variant milk bucket. **Visual: slow milk-drip particles while filled.** |
| **Incubator** | 0 or more (optional) | shell face, in/out axis | Insert frogspawn / tadpoles from the outward face; they mature inside and the adult frog spawns into the cavity through the inward face, **stats preserved**. Also the **catch basin for in-cavity breeding**: a frog bred inside a formed Terrarium lays its frogspawn into an Incubator (laying behavior overridden) instead of seeking water. At the frog cap, mature frogs **wait inside** and release as space frees. More Incubators = faster population ramp. **Right-click an incubating Incubator with a Sweetslime to hurry it (default -10% of the lifecycle per slime, `terrarium.sweetslimeAcceleratePercent`).** |
| **Hatch** | exactly 1 | shell face, in/out axis | Froglight output. In a formed Terrarium the frog-eats-slime drop is **overridden to deposit the Froglight straight into the Hatch inventory** - no item entity ever spawns. Outward face exposes the inventory for piping. **When the Hatch is full, frogs stop eating (backpressure)** - nothing drops, nothing voids. |

All four follow the V1 appliance pattern (`content/block/<Name>Block` + `content/block/entity/<Name>BlockEntity` + an `Inventory` where applicable + a `Menu` + a `client/screen/<Name>Screen` extending `PFContainerScreen`), with capability routing registered in `PFModBusEvents.onRegisterCapabilities`. The Sprinkler is the exception: it has no GUI (right-click drain only), more like the Crucible's GUI-less posture.

## Storage model: the Controller charge buffer (the load-bearing design call)

The issue says "the Sprinklers hold the milk; the Controller is a funnel with an internal tank that holds one variant at a time." The naive reading - a `FluidTank` of blended milk - **breaks catalyst fidelity**, and that is the one thing the data-fidelity ruling forbids. Here is why and the fix.

### The problem

Milk catalysts (Count / Speed / Quantity / Infinite, v1.7) are stored as **data components on the bucket item** (`PFDataComponents.SPAWNS_REMAINING / MILK_CAPACITY / MILK_SPEED / MILK_QUANTITY / MILK_INFINITE`) and mirrored on `SlimeMilkSourceBlockEntity`. They are **NOT carried on the milk fluid**. The per-variant fluid identity carries only the *variant* (that is the whole point of v1.8 per-variant fluids). NeoForge's `FluidBucketWrapper` (the item->fluid capability PF already registers for every milk bucket) drains a plain `FluidStack` with default components - so a catalyzed bucket piped through any vanilla fluid transport arrives **stripped of its catalysts**. A blended `FluidTank` also cannot represent "this 2000 mB is Infinite+Speed2 but that 1000 mB is plain" - mixing is ill-defined.

### The fix: a FIFO charge buffer, not a blended tank

The Controller's "internal tank" is modelled as a **small FIFO queue of milk charges**, each charge = one bucket-equivalent carrying its full stat set:

```java
record MilkCharge(int spawnsRemaining, int capacity, int speed, int quantity, boolean infinite) {}
// Controller state:
ResourceLocation tankVariant;          // the one variant currently held (null = empty)
ArrayDeque<MilkCharge> charges;        // FIFO, bounded (CONTROLLER_BUFFER_DEPTH, default 4)
```

- **All charges share `tankVariant`.** A charge of a different variant is **rejected until the buffer fully drains** (the settled "reject until empty" ruling, now precise: it gates on variant, and the buffer must be empty to switch).
- **Distribution stamps a whole charge onto a Sprinkler.** Round-robin pops the head charge and writes it to an empty Sprinkler's `SlimeMilkSourceBlockEntity`-equivalent state via the existing `restoreUpgrades(remaining, capacity, speed, quantity, infinite)` path. The Sprinkler then spawns **exactly** as that bucket would if hand-placed. No blending, perfect fidelity, by construction.
- **Auto-top-up** of a draining same-variant Sprinkler: only when the Sprinkler's remaining spawns fall below a threshold AND a matching charge is available; tops it up by merging the charge's spawn pool (sum remaining/capacity, take max of speed/quantity, OR of infinite). Top-up is best-effort enrichment; the round-robin-fresh-charge path is the fidelity guarantee.

### Two intake paths, both fidelity-preserving

1. **Bucket slot (outward face):** reads the bucket item's `PFDataComponents` directly off the `ItemStack` and builds a `MilkCharge` with full stats. Always exact. This is the hand-feed and hopper path.
2. **Fluid handler (outward face, `Capabilities.FluidHandler.BLOCK`, fill-only):** an incoming `FluidStack` is one bucket-equivalent per 1000 mB. The catalyst data rides the `FluidStack`'s `DataComponentPatch` (NeoForge 1.21 supports fluid components) and is read into the charge. Variant is always preserved (it rides the fluid identity).

> **Decided (ruling): ship the component-preserving wrapper.** PF will register a milk-bucket->fluid wrapper that copies the item's catalyst components (`SPAWNS_REMAINING / MILK_CAPACITY / MILK_SPEED / MILK_QUANTITY / MILK_INFINITE`) onto the produced `FluidStack` (a thin `FluidBucketWrapper` subclass replacing the plain wrapper PF registers today for every milk bucket), so a fluid pipe network preserves catalysts **end to end** - automated milk keeps its Speed/Quantity/Infinite stats whether hand-fed via the bucket slot or piped. Caveat to document: a *non-PF* pipe that round-trips milk through a plain `FluidStack` may still strip components; the PF wrapper guarantees fidelity on the bucket-item legs, and the bucket slot is always exact.

This charge-buffer model is the single most important design decision in the feature; everything else is mechanical.

## Stat and data fidelity (the second hard requirement)

### Milk keeps its catalyst stats

Resolved by the charge buffer above: stats survive `bucket/fluid -> Controller charge -> Sprinkler restoreUpgrades -> spawn`. The Sprinkler's spawn behavior is literally `SlimeMilkSourceBlockEntity`'s, so a catalyzed charge spawns identically to a hand-placed catalyzed source. **GameTest assertion:** a charge built from an Infinite+Speed bucket, distributed to a Sprinkler, produces a Sprinkler whose `isInfinite()` / `getSpeedLevel()` match the source bucket.

### Frogs keep their stats through the Incubator

The v1.5 lineage already threads stats `conception -> PrimedFrogEggBlockEntity -> ResourceTadpole (pending stats) -> ResourceFrog (setStats after finalizeSpawn)`. The Incubator inserts itself as a relay in that chain:

- The Incubator BE holds the same stat fields as `PrimedFrogEggBlockEntity` (`hasStats, appetite, bounty, reach`).
- On accepting an input (manual seed, or breeding deposit - see below), it reads whatever stats the input carries and records them.
- On maturing, it spawns a `ResourceFrog` into the cavity and applies the recorded stats via `frog.setStats(...)` **after** `finalizeSpawn` (exactly as `ResourceTadpole.ageUp` does today), so an Incubator-raised frog is byte-identical to a hand-raised one.

### In-cavity breeding feeds the Incubator (settled ruling)

In-cavity breeding is **allowed and is the population-growth path**, but with one override: a frog bred inside a formed Terrarium does not seek water to lay - **its laying behavior is redirected to deposit frogspawn into an Incubator.** Specifically:

- **The Sweetslime breeding trigger stays a manual player action.** The player still clicks a Sweetslime treat onto two same-species frogs to start breeding (ruling: "clicking a sweetslime onto a frog is still manual by the player"). Breeding is not automated; the Terrarium only changes where the *result* goes.
- **`LayCategoryFrogspawn` is overridden inside a formed Terrarium.** Today the bred frog carries pending-offspring stats and lays a `PrimedFrogEggBlock` (whose BE gets stamped with those stats) onto water. Inside a Terrarium, the lay target becomes "the nearest Incubator with room" instead of a water block: the frog's pending-offspring stats are written straight into the Incubator BE's stat fields (the same `setPendingStats(appetite, bounty, reach)` shape `PrimedFrogEggBlockEntity` uses), then `clearPendingOffspring()`. No frogspawn block is placed in the cavity.
- **This resolves the stat-carrier gap cleanly.** Bred lineage flows `frog (pending stats) -> Incubator BE -> matured frog (setStats)`, identical to the egg-BE path, with **no** new stat-bearing item needed. The earlier "bottled Frog Egg item carries no stats" concern is moot for the breeding loop: bred stats never round-trip through an item. Manual seeding still works for getting the *first* frogs in (a plain bottled Frog Egg or a captured tadpole; a plain egg carries baseline stats, which is valid - the fidelity ruling is "preserve what the input carries").

This makes the Incubator do double duty - manual seed point AND the catch basin for the breeding the player triggers - and keeps the cavity entity-clean (no loose frogspawn blocks accumulating).

## The loop, end to end

1. Pipe or hand-feed Slime Milk into the **Controller**; it becomes one or more charges of the current variant (rejecting other variants until empty).
2. The Controller round-robins charges into empty **Sprinklers** and tops up draining matching ones. Each filled Sprinkler runs the V1 placed-milk spawn loop and rains its variant's slimes into the cavity, dribbling milk particles.
3. Frogs (seeded via **Incubator(s)** from frogspawn/tadpoles, then grown in-cavity by manual Sweetslime breeding whose frogspawn is redirected back into an Incubator, stats preserved throughout) eat same-species slimes via the existing `ResourceFrogAttackablesSensor` path.
4. The frog-eat drop is intercepted: the Froglight (variant- and effect-stamped exactly as today) is **deposited straight into the Hatch inventory**; pipe it out the Hatch's outward face.

**Multi-variant farming is sequential by design:** a Cave frog eats iron AND copper. Run iron milk through the Controller to fill some Sprinklers, drain the buffer, then run copper to fill others. Sprinklers retain what they hold, so both variants rain side by side even though the Controller funnels one variant at a time.

## Direct-to-Hatch override + backpressure

The froglight drop today is `new ItemEntity(...) ; level.addFreshEntity(...)` in `FrogTongueDropHandler.dropFroglightAtFrog`. Inside a formed Terrarium this is replaced by an inventory insert. Two pieces:

### Knowing a frog is inside a formed Terrarium (cheaply)

A per-`ServerLevel` registry of active terraria, keyed by Controller `BlockPos`, each holding the validated cavity `AABB` and the Hatch `BlockPos`:

```java
// server-side, cleared on level unload; entries added/removed on (in)validation
Map<ServerLevel, Map<BlockPos /*controller*/, FormedTerrarium>> ACTIVE;
record FormedTerrarium(AABB cavity, BlockPos hatch) {}
```

`FrogTongueDropHandler` does an O(active-terraria) containment test on the frog's position before building the drop. The count of formed terraria is tiny, so this is negligible. A Controller registers on successful validation and deregisters on break / invalidation; the registry is rebuilt lazily on level load by a one-time Controller-BE scan or simply repopulated as Controllers re-validate on their next tick.

### The override

```java
FormedTerrarium t = TerrariumManager.containing(frog.level(), frog.position());
if (t != null) {
    Hatch hatch = hatchAt(t);
    if (hatch == null || hatch.isFull()) return;            // backpressure: do not drop, do not void
    ItemStack froglight = buildFroglight(variantId, captured); // identical stamping to today
    hatch.insert(froglight);                                 // no ItemEntity, no pickup sweep
    return;
}
// ... existing world-drop path unchanged for non-Terrarium frogs ...
```

- **Backpressure:** if the Hatch is full, the handler returns *before* the slime is consumed-for-yield. The cleanest place is to gate `frog.startEatCooldown()` / the eat itself, so a full Hatch makes the frog stop eating rather than eat-and-waste. Realistically the drop handler fires on slime death; to truly stop eating, the **sensor** (`ResourceFrogAttackablesSensor.isMatchingEntity`) must also refuse to surface prey when the owning Terrarium's Hatch is full. Wire backpressure in both places (sensor to stop targeting, drop handler as the safety net), mirroring the two-layer species filter that already exists.
- **No item entity:** the froglight never exists as a world entity inside a Terrarium - no despawn timer, no pickup AABB sweep, no hopper vacuum needed. The Hatch is a passive inventory.

## Caps and tick cost

- **Continuous milk feed is the loop (settled ruling).** Sprinklers **deplete like placed sources** - each charge is a finite spawn budget, so the Terrarium keeps consuming milk you pipe in, and milk production stays a relevant ongoing system. **Infinite-catalyst milk is the premium set-and-forget shortcut**: a Sprinkler fed an Infinite charge never depletes (the v1.7 `MILK_INFINITE` flag rides the charge), so the endgame "build the infinite-milk source, then the box runs itself" is reachable but costs building the Infinite catalyst. Do **not** make Sprinklers non-depleting inside a Terrarium by default.
- **Slime cap: 15** (default, `terrarium.slimeCap` config), counting **ALL** slimes in the cavity AABB however they arrived - it is the box's entity budget. Sprinklers **pause spawning at the cap** (reuse `SlimeMilkSourceBlock`'s density-cap pause branch, but scoped to the cavity AABB and counting all slimes, not just same-category - it is the box budget). Resumes as frogs eat down.
- **Frog cap: 8** (default, `terrarium.frogCap` config). At the cap, **Incubators hold mature frogs** instead of releasing, releasing as space frees.
- **Item half is entity-free:** direct-to-Hatch means zero froglight item entities. Frogs and slimes still exist (the habitat is the point); only the item leg is entity-free.
- **Everything quiesces:** full Hatch stalls eating; the slime cap then stalls Sprinkler spawning; the frog cap stalls Incubator releases. Every failure direction goes quiet instead of leaking. **Worst case per Terrarium: 15 slimes + 8 frogs + 0 items.**
- **Validation cadence:** the Controller revalidates on a throttled tick (e.g. every 20-40 ticks) and on neighbour-change events, not every tick. The 7x6x7 scan is ~194 shell cells worst case; throttling keeps it cheap. Cache the formed/unformed result and the cavity AABB between scans.

## Shell broken mid-operation

The structure **pauses, never spills:**
- Sprinklers stop spawning (the Controller marks them inactive on invalidation, or they self-pause when their Controller deregisters).
- The frog-eat override reverts to normal world drops (the Terrarium is no longer in `ACTIVE`, so `FrogTongueDropHandler` falls through to the vanilla item-entity path).
- Machines keep their contents (charges, held milk, incubating frogs, Hatch inventory all persist in BE NBT).
- Frogs/slimes stay where they are and may hop out the breach - that is the player's problem, by ruling.
- Repair the shell and the next validation tick re-forms it; everything resumes. No voiding, no spilling.

## Validation algorithm

From the Controller (the anchor), given its `pos` and `FACING` (outward):

1. **Derive the cavity.** The Controller sits in a shell face; its inward direction is `FACING.opposite()`. Walk inward one block to the cavity boundary, then the cavity is the 5x4x5 box (5 on the footprint axes, 4 on Y) positioned so the Controller's cell is on the shell directly outside one cavity face. (Anchor math: the Controller can be on any face cell of its face; the implementation enumerates the candidate cavity placements - the centered cell first - and accepts the first that fully validates. The candidate count is facing-dependent: a floor/ceiling anchor has two footprint perpendiculars -> 5x5 = 25 candidates; a wall anchor has one footprint + one height perpendicular -> 5x4 = 20.)
2. **Confirm the cavity is a clean 5x4x5** (open or filled is fine - "unrestricted interior"; the bound just has to be 5 on the footprint axes and 4 high). Reject if the bounded region is not exactly 5x4x5.
3. **Check every shell cell** (the 7x6x7 minus the 5x4x5 interior = 194 cells): each must be either a solid full-cube block OR an allowed machine/Sprinkler block in a legal position.
4. **Check machine placement rules:** exactly 1 Controller (this one) on a face, exactly 1 Hatch on a face, 0+ Incubators (optional) on faces, 0-25 Sprinklers in ceiling cells only, all directional machines facing inward. Corners/edges of the shell may only be plain solid blocks (no machines).
5. **On success:** register in `ACTIVE` with the cavity AABB and Hatch pos; light up (a `FORMED` blockstate on the Controller for a glow/indicator).
6. **On failure:** deregister; the Controller's right-click reports the **first** structural problem (chat/HUD line + particle highlight at the offending cell) - "missing solid block at X,Y,Z", "machine on an edge", "Sprinkler outside the ceiling", "no Hatch found", "more than one Controller". This is the "why won't it form" debugging story and ships regardless of any guide-mod integration.

## Construction guidance

Players should not count blocks off a wiki. From the issue's research, in order of fit:

1. **GuideME structure scenes** (recommended) - the AE2 guidebook toolkit, standalone on NeoForge 1.21.1. Ship a small PF guide with the Terrarium as a rotatable live 3D scene imported from a structure file (`ImportStructure`, per-block annotations). Sky Frogs already ships GuideME (came with Powah), so pack-side cost is zero.
2. **Controller validation feedback** (PF-native, no dependency) - step 6 above. Ship this regardless; it is the in-world debugging story.
3. **Patchouli multiblock API** (optional) - the established in-world ghost preview (project the structure, tick off as placed). Best build-in-place UX, at the cost of a second guide dependency. Add only if the ghost UX is judged worth it.
4. **Multiblock Projector / Building Gadgets template** - pack-side options, zero PF work; the pack can hand out a paste template as a quest reward.

**Recommendation: GuideME scene + Controller validation feedback.** Patchouli only if the ghost preview is wanted.

## Progression and recipes (settled ruling: Infernal-gated)

The Terrarium sits at the **Infernal tier**: its blocks are crafted from **Infernal-species resources** (the Infernal pool - blaze, quartz, glowstone, soul sand/soil, netherrack, netherite scrap, magma cream). Not boss-tier-gated and not a cheap early craft - it is the reward for having an Infernal frog loop running, which keeps the boss tier (v1.14) as a separate prestige track and keeps the hand-operated loop as a required learning phase before automation.

- The five blocks (Controller, Sprinkler, Incubator, Hatch) draw on Infernal materials; the **Controller** (the expensive anchor) should be the clearest Infernal-resource sink, the **Sprinkler** the cheapest (you craft up to 25). Tune the exact recipes in the PR.
- Whether the recipes additionally carry a `config_enabled` gate (like the Spawnery) is a pack-facing question - default to **always craftable** unless a pack reason emerges; the Infernal-material cost is the gate.
- Froglights themselves are not consumed in the recipes (the Terrarium produces them); the gate is the Infernal *resource* tier, reachable by smelting Infernal froglights back or by vanilla means.

## Config surface (`terrarium.*`)

- `terrarium.slimeCap` (default 15) - all-slimes-in-cavity budget.
- `terrarium.frogCap` (default 8) - Incubators hold at this.
- `terrarium.controllerBufferDepth` (default 4) - charges the Controller holds.
- `terrarium.validationIntervalTicks` (default ~30) - revalidation cadence.
- `terrarium.sprinklerTopUpThreshold` (default 4) - when a draining matching Sprinkler gets topped up.
- `terrarium.sweetslimeAcceleratePercent` (default 10) - % of the full lifecycle a Sweetslime shaves off an incubating Incubator (0 disables).
- `terrarium.hatchVacuumIntervalTicks` (default 8) - cadence of the Hatch's in-cavity item auto-collect sweep. The whitelist of items it vacuums is the `productivefrogs:hatch_collectible` item tag (slimeballs, magma cream, froglights, and Raw Frog Legs from frogs that die in the cavity); extend the tag to collect more.
- Sprinkler spawn cadence/cap reuse the existing `SlimeMilkSource` config, scoped to the cavity.

## Registration / wiring checklist

> All items below shipped in v1.16.0; the lone unchecked item (the GuideME guide page) is the deferred post-release follow-up.

- [x] `PFBlocks`: `TERRARIUM_CONTROLLER`, `SPRINKLER`, `INCUBATOR`, `HATCH` (directional `FACING`, `FORMED`/`WORKING`-style state where useful). `PFItems`: four `BlockItem`s. `PFCreativeTabs`: after the appliances.
- [x] `PFBlockEntities`: one BE each (Controller, Sprinkler, Incubator, Hatch). **`PFBlocks` before `PFBlockEntities`** (the `BlockEntityType.Builder.of` ordering constraint already documented in `ProductiveFrogs.java`).
- [x] `PFMenuTypes` + `client/screen`: menus/screens for Controller (buffer + status), Incubator (input/held frog), Hatch (output inventory). Sprinkler has no menu (right-click drain only). Screens extend `PFContainerScreen`.
- [x] `PFModBusEvents.onRegisterCapabilities`: Controller `Capabilities.FluidHandler.BLOCK` (fill-only, outward face) + `Capabilities.ItemHandler.BLOCK` for the bucket slot; Hatch `Capabilities.ItemHandler.BLOCK` (extract-only, outward face); Incubator `Capabilities.ItemHandler.BLOCK` (insert, outward face).
- [x] **Component-preserving milk-bucket->fluid wrapper** (decided) - replace the plain `FluidBucketWrapper` PF registers for milk buckets with a subclass that copies the catalyst components onto the `FluidStack`, so piped milk keeps catalysts.
- [x] `FrogTongueDropHandler`: the formed-Terrarium override + backpressure; `TerrariumManager` per-level registry.
- [x] `ResourceFrogAttackablesSensor`: refuse prey when the owning Terrarium's Hatch is full (backpressure layer 1).
- [x] **`LayCategoryFrogspawn` override** (decided) - inside a formed Terrarium, redirect the lay target from water to the nearest Incubator with room, writing pending-offspring stats into the Incubator BE (`setPendingStats`) instead of placing a `PrimedFrogEggBlock`. Sweetslime breeding trigger stays the unmodified manual player action.
- [x] Sprinkler reuses `SlimeMilkSourceBlockEntity` spawn/budget/catalyst logic - factor the shared spawn loop so both the placed source and the Sprinkler call it (do not fork it). Sprinklers **deplete** (continuous-feed ruling); Infinite charge = non-depleting.
- [x] Incubator stat relay (mirror `PrimedFrogEggBlockEntity` stat fields; apply via `frog.setStats` post-`finalizeSpawn`). Manual seed accepts a bottled Frog Egg (baseline stats) or a captured tadpole (carries stats); bred stats arrive via the lay override above.
- [x] Blockstates + models + textures (gen/ pipeline) for four machines + Sprinkler; loot tables; `mineable/pickaxe` tags; lang (4-5 block names + tooltips + the validation-feedback messages + any GuideME page).
- [x] Crafting recipes for the five blocks, **built from Infernal-species resources** (Controller the heaviest sink, Sprinkler the cheapest).
- [ ] GuideME guide page (3D scene) - **deferred post-release follow-up** (needs the GuideME dependency + a manual runClient verification; native Controller right-click feedback ships in its place).

## GameTests

GameTest is blind to client visuals (particles, glow, GUI) - those ride a manual `runClient` pass. Server-side behavior to cover:

- `terrariumFormsWith5x4x5Cavity`: build a valid shell + 1 Controller + 1 Hatch + 1 Incubator + N Sprinklers; assert the Controller validates (registers in `ACTIVE`).
- `terrariumRejectsMachineOnEdge`: a Controller on a shell edge/corner fails validation.
- `terrariumRejectsSprinklerOffCeiling`: a Sprinkler in a wall cell does not count and breaks the seal.
- `controllerRejectsSecondVariantUntilEmpty`: feed variant A, then a B bucket while charges remain -> B refused; drain to empty -> B accepted.
- `chargePreservesCatalysts`: feed an Infinite+Speed bucket; distribute to a Sprinkler; assert the Sprinkler's `isInfinite()` / `getSpeedLevel()` match.
- `sprinklerSpawnsLikePlacedSource`: a filled Sprinkler spawns its variant's slimes into the cavity (reuse the milk-source test harness with `spawnCapOverride`).
- `cavitySlimeCapPausesSprinklers`: fill the cavity to the slime cap; assert Sprinklers pause (no spawn, no budget spend) and resume after removal.
- `incubatorPreservesStats`: insert a stat-bearing carrier (tadpole NBT with known Appetite/Bounty/Reach); assert the released frog has those exact stats.
- `frogCapHoldsInIncubator`: at the frog cap, an Incubator holds a mature frog; removing a frog releases it.
- `hatchReceivesFroglightDirectly`: a frog eats a slime inside a formed Terrarium; assert the Froglight lands in the Hatch inventory and **no `ItemEntity` exists** in the cavity.
- `fullHatchStopsEating`: fill the Hatch; assert the frog stops eating (sensor refuses prey) and no slime is consumed-for-yield.
- `shellBreakReverts`: break a shell cell; assert the Terrarium leaves `ACTIVE`, Sprinklers pause, and a subsequent in-cavity frog-eat drops a normal world item entity again; repair restores formed behavior.

## Open implementation decisions (settle in the PR)

These are genuinely open at spec time; everything in the rulings tables is settled.

1. **Controller buffer depth** - 4 is a starting default; tune for distribution smoothness vs hoarding.
2. **Auto-top-up merge rule** - the exact stat merge when topping up a partly-drained Sprinkler with a fresh charge (sum spawns, max speed/quantity, OR infinite is the proposal; confirm it cannot produce a "better than any input bucket" Sprinkler in a way that matters).
3. **`ACTIVE` registry persistence** - rebuild lazily on level load via Controller revalidation (proposed) vs a `SavedData`. Lazy rebuild is simpler and self-healing; confirm it has no first-tick window where a frog eats before the Controller re-registers (acceptable: that one drop falls to the world path).
4. **Exact Infernal recipes** - which Infernal resources and quantities per block (the tier is settled; the recipe shapes are a PR-time balance pass).

Resolved by the 2026-06-07 rulings (no longer open): the fluid-wrapper question (ship it), the Incubator stat-carrier question (bred stats flow via the lay override; no new item needed), the milk-supply question (continuous feed, Sprinklers deplete), the progression question (Infernal-gated), and construction guidance (GuideME + native feedback, Patchouli optional).

## Decision log (settled rulings, maintainer, 2026-06-07, issue #185)

| Question | Ruling |
|---|---|
| Geometry | **5x4x5 is the INTERIOR cavity** (5x5 plan, 4 high - slime fall-damage cap); one-block shell -> 7x6x7 outside |
| Interior contents | **Unrestricted** - any blocks/entities/air |
| Shell blocks | **Any solid block** (themeable); machines/Sprinklers are the sealed exceptions |
| Machine placement | **Faces only** - corners and edges banned |
| Machine counts | **1 Controller / 1 Hatch / 1+ Incubators** |
| Sprinklers | **up to 25, ceiling cells only**, implicitly face down, count as sealed shell |
| Milk storage | **Sprinklers hold the milk** (1 bucket each); the Controller is a single-variant funnel |
| Tank variant switch | **Reject until empty** - new variant refused while milk is held |
| Sprinkler fill routing | **Round-robin** across empty Sprinklers; **auto-top-up** matching ones |
| Milk intake form | **Both** - fluid handler + bucket slot |
| Clearing a Sprinkler | **Empty-bucket drain** returns the per-variant milk bucket |
| Hatch full | **Backpressure** - frogs stop eating; nothing drops or voids |
| Froglight output | **Direct-to-Hatch inventory**, no item entity (tick-cost win) |
| Slime cap | **15** default (config), counts ALL slimes in the cavity; Sprinklers pause at cap |
| Frog cap | **8** default (config); Incubators hold mature frogs at the cap |
| Stat fidelity | **Milk keeps its catalyst stats** through funnel/Sprinkler/spawn; **frogs keep their stats** through the Incubator |
| Shell broken mid-run | **Pause, entities stay**, resume on repair; no spilling/voiding |
| Construction guidance | **GuideME scene + native Controller validation feedback**; Patchouli optional |

### Open-question rulings (maintainer, 2026-06-07, second round)

| Question | Ruling |
|---|---|
| Frog population growth | **In-cavity breeding allowed**, but the lay behavior is **redirected into an Incubator** (no water-seeking, no loose frogspawn). The **Sweetslime breeding click stays manual** - the Terrarium only changes where the bred frogspawn goes. Also resolves the bred-stat carrier (stats flow frog -> Incubator BE -> frog). |
| Progression / recipe gate | **Infernal-tier** - blocks crafted from Infernal-species resources. Not boss-gated, not cheap. |
| Milk steady state | **Continuous feed is the loop** - Sprinklers deplete; Infinite-catalyst milk is the premium set-and-forget shortcut. |
| Catalysts through pipes | **Ship the component-preserving fluid wrapper** - piped milk keeps Speed/Quantity/Infinite end to end. |

### Implementation-derived decisions (this spec, 2026-06-07)

1. **Charge-buffer Controller, not a blended FluidTank** - the only model that preserves per-bucket catalyst fidelity through a single-variant funnel (a blended tank cannot represent mixed catalyst levels and the vanilla fluid wrapper strips catalysts). This is the load-bearing engineering call.
2. **Sprinkler reuses `SlimeMilkSourceBlockEntity`** spawn/budget/catalyst logic via a shared, factored spawn loop - it is a placed source in a ceiling cell, not a reimplementation.
3. **Incubator is a relay in the existing v1.5 stat chain**, applying stats post-`finalizeSpawn` exactly as `ResourceTadpole.ageUp` does; the bottled-egg-stat carrier is a noted prerequisite.
4. **`TerrariumManager` per-`ServerLevel` registry** of formed cavity AABBs, consulted by `FrogTongueDropHandler` for the direct-to-Hatch override; lazy rebuild on level load.
5. **Two-layer backpressure** (sensor refuses prey + drop handler safety net), mirroring the existing two-layer species filter.
