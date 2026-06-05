# Changelog

## v1.9.2 - 2026-06-05 - Chorus Froglight smelting fix

### Fixed

- **The Chorus Fruit Froglight now smelts back into chorus fruit.** It previously followed vanilla's furnace chain and produced popped chorus fruit - the only Froglight that didn't smelt back into the resource it was farmed from, which also meant the frog loop couldn't produce the chorus fruit needed to prime more slimes. If you want popped chorus fruit, smelt the fruit a second time, same as vanilla.

## v1.9.1 - 2026-06-02 - Beta

### Changed

- **Promoted from alpha to beta on CurseForge.** The V1 feature set is complete across sixteen releases and cross-mod compatibility (Mekanism, Create, Applied Energistics 2, Refined Storage, and the rest) has now been smoke-tested in a real pack, not just structurally. No functional or content changes since v1.9.0; this release only updates the CurseForge release type.

## v1.9.0 - 2026-05-31 - Refined Storage support

### Added

- **Refined Storage support.** With Refined Storage installed, its three processors (Basic / Improved / Advanced) and Quartz Enriched Iron are now farmable through the frog loop: prime a slime with one, feed it to the matching frog, and smelt the Froglight it drops back into the resource. Each gets its own pipe-automatable Slime Milk, like every other variant.

### Changed

- **Silicon now works with Refined Storage as well as Applied Energistics 2.** The shared Silicon resource was previously farmable only when AE2 was installed; it now works on a pack with either mod (or both).

## v1.8.3 - 2026-05-31 - Froglight fuel

### Added

- **Coal and Blaze Froglights burn as furnace fuel.** A Coal Froglight burns like a coal item (smelts 8) and a Blaze Froglight burns like a blaze rod (smelts 12), so the Froglights you farm from those resources double as fuel. Every other Froglight stays purely decorative.

## v1.8.2 - 2026-05-31 - Slime Milk drain timing

### Fixed

- **A Slime Milk source no longer lingers for one extra spawn after its counter reaches zero.** The final spawn and the source draining away now happen on the same tick, so Jade no longer briefly reads "0 left" on a source that is still standing and about to spawn one more slime. The total number of slimes a source produces over its life is unchanged.

## v1.8.1 - 2026-05-30 - Slime Bucket fixes

### Added

- **Dispensers can release a captured slime.** A dispenser loaded with a Slime Bucket now places the slime (size 1, no water) into the block it faces, instead of just ejecting the bucket - a small automation touch. It falls back to ejecting the bucket when there is no room in front.

### Fixed

- **Releasing a slime from a Slime Bucket no longer dumps water.** A captured Resource Slime is a land mob, but the bucket was inheriting the fish-bucket behaviour of placing a water source on empty. It now releases just the slime.
- **A released slime is always size 1.** Bucketing is only allowed on size-1 slimes, but release was passing through vanilla slime spawn logic and could come out a random larger size (2 or 4). It now always matches the size-1 slime that was captured.

## v1.8.0 - 2026-05-29 - Automatable Slime Milk

Slime Milk can now be piped. Each variant has its own milk fluid, so tank-and-pipe automation (Just Dire Things Fluid Collector / Placer, Mekanism, Pipez, any fluid handler) can collect a variant's milk, move it through pipes and tanks, and place it back as the same variant. Hands-off variant farming, end to end.

### Added

- **Per-variant Slime Milk fluids.** Every variant now has its own Slime Milk fluid, source block, and bucket. Because the variant is the fluid itself, fluid mods that move it through pipes and tanks keep the variant intact, so a Fluid Collector + Fluid Placer setup farms a specific variant unattended. The Slime Milker loop and the bucket round-trip work exactly as before.
- **Slime spawn cap.** A Slime Milk source now pauses spawning when its own species already crowds the area (default: 30 slimes within 8 blocks), and resumes once frogs thin them out. It does not spend its remaining-spawn budget while paused. This keeps an automated or Endless source from flooding the server when frogs can't keep up. Tunable or disable-able under the `slime_milk_spawning` config (`spawnCapEnabled`, `maxNearbySlimes`, `spawnCapRadius`).

### Changed

- **BREAKING - placed Slime Milk and milk buckets from older worlds will not carry over.** The old single `slime_milk` fluid / block and `slime_milk_bucket` item are replaced by per-variant ones (`<variant>_slime_milk`, `<variant>_slime_milk_bucket`). Any Slime Milk placed in the world or sitting in inventories in a pre-1.8 save becomes an empty/air reference. Re-mill from Slime Buckets after updating. No world migration is provided, consistent with prior breaking releases.
- **Flowing milk tints from its own fluid** instead of tracing back to the nearest source block, so a poured pool always shows its variant colour.

### Notes

- Catalyst buffs, the Jade readout, the bucket tooltip, and the Slime Milker all behave as in v1.7. Buffs still ride the bucket through re-bucketing; routing milk through a tank is the one path where buffs are not preserved.
- Adding your own pipe-automatable variant from a pack (beyond the ones the mod ships) is a planned follow-up. See [docs/automated_milk_variants.md](docs/automated_milk_variants.md).

## v1.7.0 - 2026-05-29 - Slime Milk catalysts

Hands-off production gets its first real boost. Four craftable **catalysts** let you buff a placed Slime Milk source: just toss one into the pool. The buffs save to the source and ride along when you bucket it back up.

### Added

- **Slime Milk catalysts.** Craft and drop them into a placed Slime Milk source to upgrade it:
  - **Bountiful** - more slimes before the source runs dry (stack as many as you like; uncapped).
  - **Rapid** - the source spawns slimes faster, up to a cap.
  - **Teeming** - the source spawns more slimes at once, up to a cap.
  - **Endless** - the source never runs dry. Built from Bountiful catalysts, it's the top of the count line.
- **Jade upgrade readout.** Look at a buffed source and Jade shows its Speed and Quantity levels alongside the spawns-remaining count (or "unlimited").
- **Config section `slime_milk_catalysts`.** Turn the whole feature off (catalysts become uncraftable and hidden from JEI), and tune the per-catalyst amounts, the Speed/Quantity caps, and the spawn-rate floor.

### Changed

- **A Slime Milk source's spawn count is no longer capped at 16.** The depletion counter moved onto the source's block entity so Count catalysts can raise it without limit; the default starting budget is unchanged.

### Notes

- Catalysts are enabled by default. A dropper aimed into the pool will feed them automatically - a small taste of the automation to come.
- Upgrades already applied to a source are always honoured, even if the feature is later disabled in the config.

## v1.6.0 - 2026-05-28 - The Bog grows up, and Slime Milk settles down

A swamp-and-soil makeover for the Bog frog, plus a batch of reliability fixes to Slime Milk and predictable, pack-tunable timings for the whole frog life cycle.

### Added

- **New Bog resources.** The Bog frog now farms **clay, dirt, mud, moss, mycelium, and lily pad**, plus **plastic** (when Industrial Foregoing is installed) - leaning into a swamp-and-soil identity.
- **Tunable life-cycle timers.** New config options set a fixed, predictable **hatch time** for primed frogspawn, a **tadpole growth time**, and a **breeding cooldown**, so a modpack can pace progression instead of relying on the random vanilla timings. Vanilla frogs and frogspawn keep their stock pacing.

### Changed

- **The Bog frog is now an organic / swamp species** - earth, plants, and slime, rather than mob loot.
- **Mystical Agriculture essences moved to the Void frog.** Inferium and Supremium are a magic line, so they sit with the Void frog's arcane resources.
- **Frogs raised from crafted or Spawnery frogspawn now start at baseline stats** (the lowest values); breeding is the only way to raise them. (They used to roll a small random head start.)
- **The Bog Spawnery primer is now a clay ball** (was a bone), matching the Bog frog's new theme.
- **Clay Froglights smelt back into clay balls** (they used to turn into a brick).

### Removed

- **Bone, Gunpowder, Rotten Flesh, and String slimes** - they no longer fit the Bog frog's identity. **Modpack note:** items already carrying these variants (slimes, froglights, buckets) will lose their names and smelt recipes after updating; re-point any quests or recipes that used them, and the old `bone` Bog Spawnery primer is now `clay_ball`.

### Fixed

- **Flowing Slime Milk no longer washes away** frogspawn, water source blocks, or neighboring Slime Milk sources.
- **Frogs (and players) no longer drown in Slime Milk** - it's safe to stand in.
- **Bucketing and replacing a Slime Milk source keeps its remaining-spawns count** instead of refilling to full.
- **The Jade "slime spawns left" readout counts down live** as a source depletes, instead of sticking at the full number.

## v1.5.3 - 2026-05-28 - Recipe lookups for the Spawnery and Slime Milker

JEI now shows you how to make the two things it couldn't before.

### Added

- **Recipe pages for bottled frogspawn and slime milk.** Look up slime milk in JEI
  and it shows the Slime Milker pressing a captured slime into milk; look up bottled
  frogspawn and it shows the Spawnery's bottle-and-primer recipe, including which
  primer raises which kind of frog. The pages follow your installed setup, so a
  modpack that changes the Spawnery's primers shows those changes automatically.

## v1.5.2 - 2026-05-27 - Steel joins the Cave frog

A new metal for the Cave roster.

### Added

- **Steel Slimes.** Infuse a Cave Slime with a steel ingot to make Steel Slimes, and a
  Cave frog will hunt them down for Steel Froglights that smelt back into steel. Priming
  vanilla frogspawn with a steel ingot raises a Cave frog too. Steel Froglights smelt back
  into Mekanism steel, so Steel Slimes appear only when Mekanism is installed; with Mekanism
  present, infusing accepts steel ingots from any mod that uses the common steel-ingot tag.

## v1.5.1 - 2026-05-27 - Lapis joins the Geode frog

A small correctness fix to the Geode roster.

### Changed

- **Lapis moved from the Cave frog to the Geode frog.** Lapis lazuli is a gem, so it
  now sits with diamond, emerald, and amethyst on the Geode roster. Infuse a Geode Slime
  with lapis lazuli to make Lapis Slimes (the Cave frog no longer accepts it), and
  priming vanilla frogspawn with lapis lazuli now raises a Geode frog instead of a Cave
  one. The Lapis Slime, Froglight, and milk keep their familiar blue colour.

## v1.5.0 - 2026-05-27 - Frog stat breeding

Your frogs are no longer interchangeable. Each one now has three stats you can
improve by breeding - the headline progression system.

### Added

- **Frog stats: Appetite, Bounty, and Reach.** Every frog now carries three stats.
  **Appetite** sets how fast it eats, **Bounty** how many Froglights it drops per
  slime, and **Reach** how far away it spots prey. A fresh frog rolls low - breeding
  is how you climb.
- **Breed your frogs to improve them.** Feed two frogs of the same species a new
  **Sweetslime** treat and let them breed the normal way; the offspring inherits a
  blend of its parents' stats with a chance to come out better than either. Keep the
  winners, cull the duds, re-breed the best pair, and ladder a species toward a fully
  maxed frog.
- **Sweetslime**, the breeding treat - crafted from a slime ball and sugar (makes
  two, enough for one pair). It is the only thing that puts frogs in the mood, so a
  loose slime ball in your farm won't set off accidental breeding.
- **Frog stats in the Jade tooltip.** With Jade installed, look at a frog to read its
  Appetite, Bounty, and Reach at a glance.

### Changed

- **Frogs no longer despawn.** A frog you've bred up is valuable, so it now stays put
  instead of wandering off into the despawn timer.

## v1.4.3 - 2026-05-26 - Deep dark to the Void

A balance and correctness pass on the deep dark and the Void roster.

### Added

- **Sculk** and **end stone** are now farmable through the **Void** frog - sculk is renewable for sculk sensors, end stone for bulk building.

### Changed

- **Echo shard moved from Cave to Void.** The deep-dark loot now sits on the Void roster, which thematically fits (the deep dark is the eerie, endgame kin of the End) and pulls a high-value resource off the heavily-stacked Cave species.

### Fixed

- **Cave Slimes no longer spawn in the deep dark**, matching vanilla (the deep dark suppresses natural mob spawns). Cave Slimes spawn in dripstone caves and lush caves - lush caves was always the intended coverage but had never actually been wired up.

## v1.4.2 - 2026-05-26 - Geode & Tide roster rebalance

More to farm in the two thinnest species, plus a balance move.

### Added

- **Geode** gained **tuff** and **calcite** - the stony, crystal-adjacent blocks that make up an amethyst geode, now farmable through the Geode frog.
- **Tide** gained **sea pickle** and **nautilus shell** - renewable nautilus shells are handy for conduits.

### Changed

- **Diamond is now a Geode resource** (was Cave), so the Geode frog eats diamond slimes. A balance move that gives the sparser Geode species a marquee resource. Existing diamond slimes and Froglights keep working - only which frog eats them changes.

## v1.4.1 - 2026-05-26 - Jade tooltips & tinted milk pools

Optional Jade integration for at-a-glance appliance readouts, plus a fix so
poured Slime Milk actually looks like its variant.

### Added

- **Jade tooltips for the appliances.** With Jade installed, looking at a Slime Milk source block shows its remaining spawn count (out of the configured depletion count, or "unlimited" when depletion is off), and the Slime Milker + Spawnery show cook progress while working. Only real variant-carrying source blocks are annotated - spread/flowing milk is not.

### Fixed

- **Flowing/spread Slime Milk now tints to its variant.** Slime Milk is meant to be poured into pools that slimes spawn from, but spread milk rendered pure white (fluid spreading doesn't carry the source's variant), so a copper-coloured milk flowed white. Flowing milk now resolves back to its source block and tints the whole pool to that variant's colour.

## v1.4.0 - 2026-05-26 - the Spawnery (skyblock bootstrap)

A new V1 appliance for skyblock and other restricted-biome packs, plus a round of
appliance/JEI fixes. The Spawnery turns glass bottles into bottled frogspawn so the
frog side of the loop can start where swamps and mangroves are unreachable - off by
default, and fully pack-tunable.

### Added

- **Spawnery** - a skyblock-bootstrap appliance, disabled by default
  (`spawnery.enabled`). A furnace-style block that turns glass bottles into
  bottled frogspawn, fueled by slime balls (one ball per bottle). A primer is
  required: a slime ball primes plain vanilla frogspawn, or a species primer
  (iron ingot for Cave, amethyst shard for Geode, bone for Bog, prismarine shard
  for Tide, blaze powder for Infernal, an ender pearl for Void) primes that
  species' eggs. Species defaults are normal-world resources; modpacks retune
  them per species via the `spawnery_primer/<species>` tags.
  Hopper-aware. Crafted from 5 cobblestone + 3 planks + 1 bonemeal. When disabled
  it is uncraftable and hidden from JEI + the creative tab. The primer set is
  pack-overridable via the `spawnery_primer/<species>` item tags. See
  `docs/spawnery.md`.

### Fixed

- **The Slime Milker now has a crafting recipe** (5 cobblestone + 3 planks + 1
  slime ball). It previously shipped with a loot table but no recipe, so a
  survival player had no intended way to obtain it (only creative / `/give`).
- **Slime Milk variants now show individually in JEI.** Every variant appeared in
  the creative tab, but JEI collapsed them into a single Slime Milk Bucket (a
  missing subtype interpreter); each variant is now its own JEI entry with its
  info page.
- **Item tooltips now render in the Slime Milker and Spawnery GUIs.** Hovering a
  slot in either appliance showed no tooltip (NeoForge 1.21.1's container screen
  doesn't render tooltips unless the screen asks it to); both now do.

## v1.3.0 - 2026-05-26 - cross-mod crush yields

The cross-mod crush release. With Mekanism, Immersive Engineering, or EnderIO
installed, metal Froglights now crush to double their resource - the optional 2x
payoff for routing them through a processing mod, with nothing required if you
have none. Also bundles a round of JEI and naming polish.

### Added

- **Cross-mod crush recipes (2x yield).** With Mekanism, Immersive Engineering,
  or EnderIO installed, crushing a metal Froglight yields 2 dust (smelts to 2
  ingots) instead of the 1 ingot from a direct furnace smelt. Ships as 33
  optional `mod_loaded`-gated recipes (one per crusher per supported metal);
  AllTheOres, when present, broadens the metal coverage. Activates only when a
  crusher mod is present and changes nothing otherwise. Generated by
  `scripts/generate_crush_recipes.ps1`.

### Fixed

- **Cross-mod variant slimes no longer show a raw translation key** in the
  Froglight JEI info text (e.g. "...eats a
  entity.productivefrogs.resource_slime.osmium"). The JEI info now uses the
  title-case fallback, and every shipped variant gained explicit names across
  all five per-variant key families.
- **JEI info text now calls the block "Froglight"**, not its registry-flavored
  "Configurable Froglight" id.
- **Resource Slimes are now captured with an empty bucket**, not a water bucket.
  A size-1 Resource Slime right-clicked with an empty bucket fills a Slime
  Bucket; the previous behaviour (inherited from vanilla `Bucketable`, which
  fish/axolotls/tadpoles use) keyed capture on a water bucket and read wrong for
  a non-aquatic slime.
- **Species now display in canonical progression order** (CAVE -> GEODE -> BOG
  -> TIDE -> INFERNAL -> VOID) across creative tabs, JEI, and the recipe book,
  instead of the previous alphabetical order. Save-safe: `Category` persists by
  name, so the enum reorder does not touch existing worlds.

## v1.2.0 - 2026-05-25 - cross-mod compatibility + observability

The V1.2 compatibility + tooling release. Resource Slimes now extend to the
popular ATM10 mods' resources through common tags (no hard dependencies), a
gated debug-logging framework lands across every layer, and the Slime Milk fluid
collapses to a single component-driven block/bucket - completing the goal that a
new Resource Slime variant is addable by datapack alone.

### Added

- **Cross-mod variant pools.** 24 Resource Slime variants for the popular
  ATM10 mods, all condition-gated so they only appear when their provider mod is
  installed: AllTheOres metals (tin, lead, osmium, nickel, silver, zinc, aluminum,
  uranium), Create brass, Mekanism refined obsidian + fluorite, AE2 certus quartz
  / fluix / silicon, Mythic Metals orichalcum / mythril / aquarium, Powah blazing
  / niotic / spirited / nitro crystals, Industrial Foregoing pink slime, and
  Mystical Agriculture inferium / supremium essences. Each is a datapack JSON +
  a Froglight smelt recipe (no Java, no textures, no lang) generated by
  `scripts/generate_cross_mod_variants.ps1`. Thermal Series picks are deferred
  (it has no 1.21.1 release). Design: `docs/cross_mod_compat.md`.
- **`primer_tag` on the `slime_variant` codec** (alongside the now-optional
  `primer_item`). A variant can be primed by membership in a common tag
  (`c:ingots/tin`), so one cross-mod variant accepts any mod's matching item.
  Resolved at infusion time via `SlimeVariant.findByPrimer`; an exact
  `primer_item` match wins over a `primer_tag` match, and the codec rejects a
  variant declaring neither (it could never be primed).
- **Observability framework (`PFDebug`).** A gated debug-logging layer across 12
  mod subsystems (lifecycle, registry, config, infusion, split, tongue, egg,
  sensor, milker, milk_source, render, tint). Off by default and near-zero cost
  when disabled; enable per-area at launch with `-Dproductivefrogs.debug=<areas>`
  or at runtime with the `/pf debug <area> on` command. Design:
  `docs/observability.md`.
- Single neutral Slime Milk texture set tinted per-variant at render (fluid via
  position-aware `getTintColor` reading the source BlockEntity; bucket via a
  2-layer model + item color on the milk layer). One greyscale set serves every
  variant, including datapack-added ones.
- `ResourceSlimeRenderer` falls back to the category texture when a variant ships
  no `<variant>_resource_slime.png`, so a datapack variant renders cleanly
  (category cube + its `primary_color` shell) instead of a missing texture.
- Title-cased display-name fallback (`VariantNames`) on the Slime Milk bucket /
  Configurable Froglight / spawn egg, so a datapack variant needs no lang file.

### Changed (breaking)

- **Slime Milk collapsed to a single component-driven fluid/bucket/block.** The
  ~35 per-variant Slime Milk registrations (one `FluidType`, Source + Flowing
  fluids, `<variant>_slime_milk` block, and `<variant>_slime_milk_bucket` item
  per variant) are replaced by **one** of each: a single `slime_milk` fluid, a
  `slime_milk` source block whose BlockEntity stores the variant, and a single
  `slime_milk_bucket` item carrying the variant in the `SLIME_VARIANT` data
  component (the same pattern as Configurable Froglight / Slime Bucket / the
  spawn egg). **Migration: hard break** - the per-variant `*_slime_milk_bucket`
  item IDs and `*_slime_milk` block/fluid IDs are gone; placed milk blocks and
  stashed milk buckets in existing worlds become orphaned refs (regenerate, per
  the project's pre-stable policy).
- **Why:** this is the last step of the data-driven variant refactor. A new
  Resource Slime variant is now addable **by datapack alone** - JSON, no Java,
  no recompile, no per-variant assets or lang. Per-variant fluids could never be
  datapack-added because fluids register at mod construction, before any world
  datapack loads. Design: `docs/refactor_data_driven_variants.md`.

## v1.1.0 - 2026-05-25 - vanilla resource coverage

> A minor bump that is nonetheless **breaking** (it removes item IDs and a
> shipped variant; see Breaking). Includes the v1.0.2 housekeeping below, which
> never shipped standalone and is folded into this release.

The v1.1 content release: **22 new Resource Slime variants** (33 total) extend
vanilla resource coverage across all six species, with no new mechanics. Plus
the CR-9 enabler that made the additions pure data: the Resource Slime spawn egg
is now a single component-driven item instead of one item ID per variant.

### Added

- **22 new variants** (33 total), each with a Resource Slime, Configurable
  Froglight, Slime Bucket, Slime Milk fluid/block/bucket, smelting recipe, and a
  downscaled in-slime block interior (the variant's `inner_block`):
  - **Bog** (+7): bone, gunpowder, clay, rotten flesh, string, leather, feather
  - **Cave** (+3): glow ink sac, obsidian, echo shard
  - **Geode** (+1): amethyst
  - **Tide** (+2): ink sac, prismarine crystals
  - **Infernal** (+7): netherite scrap, glowstone dust, soul sand, soul soil, netherrack, blaze, quartz
  - **Void** (+2): chorus fruit, shulker shell
- `scripts/generate_v1_1_variants.ps1`: data-table generator that emits the four
  templated JSON files per variant (slime_variant, smelting recipe, milk
  blockstate, milk bucket model).
- `scripts/generate_resource_slime_textures.py`: bakes a downscaled copy of each
  variant's resource-block texture onto the slime's inner-cube faces.

### Breaking

- The 12 per-variant spawn-egg item IDs (`productivefrogs:iron_slime_spawn_egg`,
  `..._copper_...`, ...) are **removed** and replaced by a single
  `productivefrogs:resource_slime_spawn_egg` whose variant rides in the
  `slime_variant` data component. **Migration:** these are creative-only items;
  any stashed in an existing world disappear on load (survival play never grants
  them). Use `/give @s productivefrogs:resource_slime_spawn_egg[...]` or the
  creative tab to get the new form.
- **Removed two redundant variants:** `magma_cream` (a v1.0 variant - magma
  cream is itself a slime-cube drop, and the Infernal pool already covers the
  nether) and `slime_ball` (a slime made of slimeballs). Their slimes, Slime
  Milk fluids/blocks/buckets, Configurable Froglight stacks, and smelting recipes
  are gone. **Migration:** any `magma_cream`-variant items/slimes in an existing
  world lose their variant on load (the entity falls back to its Infernal
  category visuals + name); `slime_ball` never shipped.

### Changed

- Creative tab, JEI subtypes, and inventory tint now enumerate variants from the
  `slime_variant` datapack registry, so a new variant's spawn egg appears with no
  code change. (Variant-driven creative entries populate in-world; like vanilla's
  enchanted-book entries they are empty on the title screen until a world loads.)
- Adding a variant no longer needs a spawn-egg Java edit; only the Slime Milk
  `VARIANTS` entry remains. Docs (`architecture.md`, `versioning.md`,
  `v1_1_scope.md`) updated.
- `scripts/generate_slime_milk_textures.ps1` now does its per-pixel tint in a
  compiled `LockBits` helper instead of interpreted `GetPixel`/`SetPixel`. The
  interpreted path crashed the PowerShell engine once the variant count grew
  past ~14; the compiled path is robust and produces byte-identical output.
- **Resource Slime interior rendering reworked.** The v1.0.1 "live block model
  drawn inside the slime" layer (`ResourceSlimeInnerBlockLayer`) is **deleted**:
  an opaque block drawn in a separate render pass is depth-culled by the slime's
  translucent shell, so it never actually showed - what players saw was the
  per-category coloured inner cube (every Cave variant looked like a redstone
  cube, every Void variant purple, etc.). The interior is now a downscaled copy
  of the variant's resource block baked onto the slime's own inner-cube faces
  (rendered as part of the translucent body, so it is reliably visible), with the
  per-variant tint kept on the translucent exterior shell.

### Tests

- Removed the timing-flaky end-to-end AI tongue GameTest
  (`frogTongueAiPathDropsConfigurableFroglight`). The category-match drop path
  stays covered by `matchingFrogKillDropsConfigurableFroglight` (deterministic
  manual-damage kill, no AI).
- `infusionWithVariantPrimerSetsSpecificVariant`'s negative case now uses
  `ghast_tear` (deferred from v1.1) instead of `blaze_powder`, which became the
  `blaze` variant's primer.

## v1.0.2 (folded into v1.1.0, 2026-05-25)

Internal hardening + cleanup patch from the 2026-05-24 code review
(`docs/code_review_2026_05_24.md`). **No world migration, no API or data
changes.** One small player-facing tweak; the rest is invisible in play.

### Player-facing

- Placing a Frog Egg now plays the bottle-empty sound instead of the frogspawn
  hatch sound (the hatch sound is for tadpoles emerging, not for placing the egg).
- Slime Milker block entities now sync their contents to the client, so info-HUD
  mods (Jade/WTHIT) read the inventory + cook progress without opening the GUI.

### Robustness

- The `Category` network codec rejects out-of-range ordinals cleanly instead of
  crashing the decode thread (defends the item data-component sync path).
- Slime Milker cook progress is clamped on load; `slime_variant` colours are
  range-checked; a warning fires if `minSpawnIntervalTicks > maxSpawnIntervalTicks`.

### Internal

- A modded `parent_species` JSON now wires a slime into **both** infusion and
  split-discovery (infusion previously used a hardcoded class check).
- Six near-identical parent-species renderers collapsed into one; dead code and
  stale post-V1.5 docs swept. Net ~350 fewer lines. Build + all 50 GameTests green.

## v1.0.1 — 2026-05-24

Visual-polish patch. **No behavior changes.** No world migration, no API surface change. Production loop, drops, AI, tints, infusion semantics, JEI subtypes: unchanged.

### What changed visually

- **Resource Slimes now have an actual vanilla resource block rendered inside the translucent shell.** Pre-v1.0.1, the inner cube was textured with a 6x6-downsampled copy of the block image stamped into a per-variant atlas (blurry at large slime sizes). v1.0.1 draws the real block model (iron block, copper block, ...) so the interior is the genuine vanilla block at native resolution, with vanilla's own UVs and mipmaps.
- Same treatment for the 6 parent species (Bog/Cave/Geode/Tide/Infernal/Void) - each shows a themed vanilla block inside (moss, stone, amethyst, prismarine, netherrack, end stone).
- The slime's face (eyes + mouth) and tinted translucent shell are unchanged.

### How

A new `ResourceSlimeInnerBlockLayer` renders the resource block via `BlockRenderDispatcher` in the volume vanilla's 6x6x6 inner cube occupies. The base renderer still draws the vanilla inner model (cube + eyes + mouth) and the tinted outer shell, both unchanged from v1.0 - the eyes live on the vanilla inner body layer, so keeping that model preserves the face. The opaque block covers the inner cube's body; the eyes sit proud of the cube's front face and stay visible.

### Data layer

- New optional `inner_block` field on `SlimeVariant` and `ParentSpeciesEntry` codecs. Format: a plain vanilla block id (e.g. `minecraft:iron_block`), resolved to its default block state at render time.
- Fully data-driven, parallel to the existing tint config: Resource Slime variants read `inner_block` from the variant JSON (like `primary_color`); parent species read it from their `parent_species` registry entry. A modpack can repoint any slime's interior block by editing JSON, no code change.
- All 12 shipped variant JSONs populated; all 6 parent_species JSONs populated.
- Removed the pre-v1.0.1 per-variant atlas `texture` field from `SlimeVariant` (the renderer no longer reads it; the outer-shell atlas is per-category).

### Asset cleanup

- 12 per-variant `<variant>_resource_slime.png` atlas PNGs deleted (the inner content is now a rendered block; the outer-shell atlas is per-category).
- `scripts/generate_variant_slime_textures.ps1` deleted (the textures it produced are gone).

### Fallback

A variant JSON without an `inner_block` field (typo, modded block from an absent mod) skips the inner-block render pass - the slime renders with its shell, eyes, and inner cube but no interior block.

### Modpack-author note

No migration. No third-party variants are known to ship between v1.0 and v1.0.1; if any exist, they'll continue to load (`inner_block` is optional) and just render without an interior block until a JSON edit adds the field.

---

## v1.0.0 — 2026-05-24

First public release. **Minecraft 1.21.1 / NeoForge 21.1.230.**

### Production loop
- Six parent slime species, each in a themed biome: **Bog** (swamps), **Cave** (dripstone / deep dark / lush caves), **Geode** (mountain peaks), **Tide** (oceans), **Infernal** (nether), **Void** (end islands).
- Right-click a parent slime with a variant primer item (iron ingot, copper ingot, diamond, prismarine shard, magma cream, ender pearl, etc.) to convert it into a **variant Resource Slime**.
- Six matching frogs — Bog Frog, Cave Frog, Geode Frog, Tide Frog, Infernal Frog, Void Frog. Each eats only Resource Slimes of its species.
- Frog kills a Resource Slime → drops a **Configurable Froglight** stamped with the slime's variant.
- Smelt the Froglight in a vanilla furnace → get the original resource back.

### Production block
- **Slime Milker** — furnace-style appliance. Drop a variant Slime Bucket in the input slot, get the matching Slime Milk bucket out 100 ticks later. Hopper-compatible.
- **Slime Milk source blocks** — place in world to spawn the source variant slime periodically. Self-sustaining production loop.

### Item collection
- **Bottle of Frog Eggs** — right-click vanilla frogspawn with a glass bottle to bottle it. Prime the bottle with a variant primer to assign a species. Place on water to hatch into tadpoles.
- **Primed Frog Egg blocks** — six species. Place on water; hatches into species tadpoles after a delay.
- **Resource Tadpole Bucket / Slime Bucket** — capture / release with category and variant preserved.
- 30 spawn eggs (6 parent species + 6 frogs + 6 tadpoles + 12 variant slimes).

### Integrations
- **JEI Information pages** on every PF item — explains the production role, who hunts what, what smelts to what. Dynamically generated from the SlimeVariant datapack registry.
- **Jade** — drop-in compatible (no plugin code needed); shows species + variant in the in-world entity tooltip.

### Datapack-driven
- Add new slime variants without recompiling: drop a JSON in `data/<modpack>/productivefrogs/slime_variant/<name>.json` (the `primer_item` field is the exact 1:1 match for infusion), an inner-cube texture, a smelting recipe, and a lang entry for the display name. JEI Information pages auto-extend to cover the new entry.
- Cross-mod variants (Mekanism, Create, Thermal, etc.) gated by `mod_loaded` neoforge conditions — see `docs/cross_mod_compat.md`.

### Known limitations
- One end-to-end AI tongue gametest (`frogTongueAiPathDropsConfigurableFroglight`) shipped `required = false` in v1.0 due to CI timing flakiness. It was **removed in v1.1** as redundant: the AI path is covered by `frogTongueTargetsOnlyMatchingCategorySlime` (sensor/targeting, polling pattern) and the drop path by `matchingFrogKillDropsConfigurableFroglight` (manual damage). No flaky `required = false` test remains.
- No automation (hoppers, power, pipes, multiblocks) in v1.0 — that's the V2 scope; v1.0 is the playable foundation + hand-operated appliance layer.

### Modpack note
This is a fresh release on the MC 1.21.1 line. There is no migration path from earlier 1.21.11 / 1.21.x development snapshots; modpack maintainers carrying those should regenerate worlds.
