# Changelog

## [Unreleased]

## v1.24.2 - 2026-06-29 - Name Tags

### Changed

- **The Terrarium's "Hatch" is now called the "Terrarium Hatch".** Its old bare name collided with the Wither Altar Hatch and End Dragon Altar Hatch in the creative menu, JEI, and search, making it easy to grab the wrong block. It now reads as plainly as its neighbour the Terrarium Controller, with all three hatches distinct. Same block, same recipe - only the name changed, so existing worlds and setups are unaffected.

## v1.24.1 - 2026-06-29 - No Crossed Wires

### Fixed

- **A redstone signal on one Sprinkler no longer pauses the Sprinklers next to it.** Sprinklers pack tightly into a Terrarium ceiling, and the new redstone switch-off (v1.24.0) leaked across them: flipping a lever on one Sprinkler, or running power into it, also paused every Sprinkler touching it - so you couldn't stage which resources ran without isolating each block. A Sprinkler no longer passes a redstone signal through to its neighbours, so a lever or comparator now controls exactly the one Sprinkler it's wired to. (#264)

## v1.24.0 - 2026-06-29 - Flip the Switch

### Added

- **Switch Terrarium Sprinklers on and off with redstone.** A Sprinkler now pauses while it's getting a redstone signal - power it off to stop production, cut the signal to start it again. The held milk and its remaining spawns just freeze in place and pick up right where they left off. Wire a comparator off a full chest so a Terrarium throttles itself when storage backs up, or flip a lever to pause a box by hand. Each Sprinkler is controlled on its own, so you can stage which resources run. (#263)

## v1.23.0 - 2026-06-29 - The Midas Touch

### Added

- **The Equivalence lane - turn any leftover into anything else, through frogs.** An opt-in, late-game transmutation chain for items the mod can't normally turn into slimes. Feed a spare item and a bucket into the **Alembic** (a powered copper-and-glass apparatus with a glowing star core) and it brews a **Mimic Slime Bucket** carrying that item's identity. Run that through the Milker into **Mimic Milk**, place or pipe it, and it spawns **Mimic Slimes** wearing the item's colour. Only one frog will eat them: **Midas**, hatched by kissing frogspawn with a Princess's Kiss. Midas devours Mimic Slimes and drops a **Prismatic Froglight**, which the **Distiller** renders back into the original item - and the duplication along the loop is what makes it an equivalent-exchange engine (pour in coal, pour out diamonds; the rate is yours to balance with milk catalysts and breeding). Midas breeds true, can perch and live in a Terrarium like any frog, and the whole lane is power-hungry by design. Crafting the machines needs a Nether Star, so it sits firmly in the endgame. **Off by default** - a modpack opts in with `equivalence.enabled`; when off, nothing in the lane is craftable or active. (#253)

### Fixed

- **The Wither Altar now works no matter which way you build it.** The altar only used to come together when built in one specific compass orientation - built any other way, the wither skeleton skulls and soul sand appeared stuck to the outside of the structure and no summon ever started, with no hint as to why. The altar now recognises itself in all four horizontal orientations: build it facing wherever you like, and Witherbane, the loaded receptacle items, and the summoned replica all orient themselves to match. Altars already built keep working exactly as before. (#247)

## v1.22.0 - 2026-06-15 - Apex Predators

### Added

- **The End Dragon Altar - a controllable, repeatable Ender Dragon farm.** Once you've beaten the dragon the normal way, build the altar right on the End exit portal: ring it with End Crystal Receptacles and reinforced froglights, with the Dragon Egg as the capstone. Drop an end crystal into each of the four receptacles and a replica dragon rises, charges, and is devoured by Dragonsbane - a special frog perched at the heart of the altar. The dragon's loot lands in the hatch: a Dragon Breath Froglight, a renewable Dragon Egg Froglight, the Princess's Kiss, and experience. No real dragon, no boss bar, no portal regrowth - just a hands-off dragon harvest you can pipe with hoppers. Boss-tier, gated behind the boss config; tunable via `boss.dragon_altar`. (#249)
- **The Wither Altar - a contained, repeatable Wither farm.** After your first Wither kill (its Nether Star crafts the altar's Withered Star), build a soul-forged arena of Reinforced Soul Sand and Blaze Rod Froglights and load the full vanilla summon - four soul sand and three wither skeleton skulls - into the ritual receptacles. A replica Wither charges in the arena exactly like a real spawn, blue glow, roar and all, until Witherbane devours it. Out comes a Nether Star Froglight (smelts back to a star), experience, and whatever else the Wither drops - with no boss bar, no exploding blocks, and no danger. The old "how do you cage a Wither" problem solved by never spawning a real one. Boss-tier, gated behind the boss config; tunable via `boss.wither_altar`. (#247)

## v1.21.0 - 2026-06-13 - By the Book

### Added

- **An in-game guide book.** Craft a vanilla book with a slime ball for an illustrated Productive Frogs field guide (requires Patchouli) that walks the whole mod start to finish - the core loop, every species, spawning slimes, Resource Slimes and Slime Milk, milk catalysts, Froglights and Brewed Froglights, the appliances, the Terrarium (with a build diagram), and the boss catalyst altars (each with its own altar diagram). Built to be extended: a modpack drops its own categories and entries into the book's namespace via a resource pack and they merge into the same book. Inert if Patchouli is absent. (#243)
- **The Sweetslimed Lily Pad - a frog perch.** Right-click a placed lily pad with a Sweetslime to turn it into a perch: a Resource Frog within range walks over, claims it (one frog per pad), and holds position on top of the pad instead of wandering off, while still eating same-species slimes in reach and dropping Froglights in place. Pin a frog over a hopper beside a Slime Milk source for a hands-off collection point - the active counterpart to the Frog Net (move a frog) and the Terrarium (contain a loop). Config-gated (`lily_pad_perch.enabled`, default on) with a tunable `lily_pad_perch.range`. (#214)

### Changed

- **Breeding frogs is less tedious - no more breeding backwards.** The stat-inheritance roll is reworked into two layers: an offspring stat is the **average of its parents** (round-half-up), then a chance to **climb one above** that average. The old random regression is gone - a breed never drops a stat below the parent average, so you never wait a full generation only to get a *worse* frog. On top of that, **every breed now improves at least one stat** (a guaranteed bump when nothing climbed on its own), so a breeding cycle is never wasted. The skill is in which two frogs you pair: breed your two best to hold a high average and climb toward 10/10/10; mixing in a weak frog blends you down. A handy side effect of averaging - you can **merge two specialized frogs** (say a high-Appetite and a high-Bounty) into one balanced frog. Tunable via `breeding.improvementChance` and the new `breeding.guaranteedImprovement` (default on); `breeding.regressionChance` is removed. (docs/frog_breeding.md)

### Fixed

- **Boss catalyst altars no longer spawn their slime inside the sealed source block.** A fully enclosed boss altar could pick the Slime Milk source's own cell as the slime spawn position, trapping the spawned slime inside the source block where it was stuck and unreachable. The source now excludes its own cell when choosing where to spawn, so the slime always lands in an open neighbouring space. (#242)

## v1.20.0 - 2026-06-10 - Full Bloom

### Changed

- **Promoted to a stable release - out of beta.** Productive Frogs has been beta on CurseForge since v1.9.1; across the v1.9-v1.19 line the full V1 scope (six species, the data-driven variant roster, the hand-operated appliances, frog stat breeding, the boss/endgame tier, the Terrarium multiblock, and the cross-mod variant pools) has been built out and soaked in the Sky Frogs pack, and the pre-release config suite (#200/#202/#203/#204) shipped in v1.18. The CurseForge release channel is now **Release**. No gameplay changes since v1.19.2 - this is the stability milestone.

## v1.19.2 - 2026-06-10 - No More Spilled Milk

### Added

- **Advancements.** A curated `Productive Frogs` advancement tab for standalone play: an entry node (obtain a Slime Bucket or Frog Egg), one per species when you farm it (have that species' Froglight), one per boss catalyst altar built, and one for crafting the production machines. Milestone guidance, complementary to a pack's own quests. (#183)

### Fixed

- **The Slime Bucket no longer dumps a water source when you release the slime.** Right-clicking a captured Slime Bucket onto a block placed a vanilla water source alongside the slime (a leak from the fish-bucket behavior it inherits), which could wash away nearby Slime Milk sources. It now releases the slime and returns an empty bucket, no water. (#234)
- **Water, lava, and other fluids no longer wash away Slime Milk source blocks.** A milk source is a no-collision fluid block, so a neighbouring water/lava flow (or a foreign modded fluid) could flow straight in and overwrite it, destroying the production pool. The source now rejects every fluid except its own milk, so an adjacent fluid treats it as a wall - while the milk's own flow is unchanged. Complements the existing guard that stops milk from washing away water, frogspawn, and other milk sources. (#235)
- **Jade's tadpole "Growing time" now reads correctly when a pack speeds up tadpole growth.** With `lifecycle.tadpoleGrowthTicks` lowered below vanilla's 24000, Jade's stock line showed a time several times too long, counting down faster than real time (the tadpole still matured on schedule - only the label was wrong). The look-at tooltip now shows the real remaining time. (#238)

## v1.19.1 - 2026-06-10 - Survival of the Fittest

### Changed

- **Frogs breed up faster.** The default frog-stat improvement chance rises from `0.20` to `0.40` per stat, so a breed now improves at least one of the three stats about 78% of the time (up from ~49%). A run of breeds with no gain is now rare, while a single breed still usually moves just one or two stats, not all three - the climb to a maxed frog is brisker without being a giveaway. Tuning only; the inheritance mechanic is unchanged and packs can still retune `breeding.improvementChance`. (#232)

## v1.19.0 - 2026-06-09 - Stone Soup

### Added

- **Lava Froglights burn as furnace fuel worth one lava bucket** (20,000 ticks, a hundred smelts) - so the renewable lava loop doubles as a renewable solid fuel with no empty bucket to manage. Joins the Coal (1600t) and Blaze (2400t) Froglights that already burn; the value is per-variant, so other Froglights stay inert decoration. (#231)
- **The Froglight Crucible now melts stone straight to lava** (Ex Deorum heated-crucible parity). Drop cobblestone, stone, gravel, or netherrack into a heated Crucible and it melts to lava on the same heat-from-below loop the Froglight lane uses - no frog required. Amounts and tags are copied verbatim from Ex Deorum: cobblestone / stone / gravel = 250 mB each (four to a bucket), netherrack = 500 mB (two to a bucket). Heat is required, like every Crucible melt. Pure-data recipes (one JSON per input), so a pack retunes or extends them freely; the right-click insert now accepts any block with a melt recipe, not just Froglights.

## v1.18.0 - 2026-06-09 - Made to Measure

### Added

- **Config: frog stat-breeding master switch (`frog_stats.enabled`, default on).** One toggle drops the whole Appetite / Bounty / Reach breeding layer for a pack that wants the plain six-species froglight loop without the minigame. When off, Sweetslime is uncraftable and hidden from JEI + the creative tab and no longer breeds frogs, every frog behaves at the baseline (effective stats fixed at 1, so no per-frog variance), newly bred frogs carry baseline stats, and the Jade Appetite/Bounty/Reach readouts are hidden. Stored stats on existing frogs are frozen, not deleted - re-enabling restores each frog's bred behavior. Non-breaking; recipe gating applies on world reload. (#202)
- **Config: boss-tier master switch (`boss.enabled`, default on).** One toggle drops the entire boss tier for a pack that wants the standard froglight loop without boss farming: it suppresses the four prime-only boss variants (wither skull / nether star / dragon egg / dragon breath - so no toxic milk and no catalyst-altar gating), makes the four catalyst-altar blocks uncraftable and hidden from JEI + the creative tab, and drops the boss Froglight smelt-back recipes. The narrower `variants.bossVariantsEnabled` (#203) still drops just the variants while keeping the altar blocks craftable. Non-breaking; recipe gating applies on world reload. (#200)
- **Config: disable individual variants, whole species, or the boss tier.** A new `[variants]` config section lets a pack scope content without datapack surgery: `disabledVariants` force-offs resources by id, `disabledCategories` force-offs whole species (cave/geode/bog/tide/infernal/void), and `bossVariantsEnabled` toggles the prime-only boss tier in one switch. A disabled variant is unprimable, never appears in split-discovery, and is hidden from JEI and the creative tab. It is a soft hide - the registry entry stays, so already-placed content keeps working and re-enabling restores everything (no save surgery). Default: nothing disabled, fully non-breaking. Applies on world reload. (#203)
- **Config: force off a host mod's variants with `variants.disabledIntegrations`.** List a provider modid (e.g. `["mekanism"]`) and every cross-mod variant gated solely behind that mod is disabled, exactly like `disabledVariants` - so a pack can keep a mod for its machines while dropping its Resource Slime variants. The provider is read from each variant's `mod_loaded` gate (most cross-mod metals are under `alltheores`); a variant shared by several providers (only `silicon`) is disabled only when all of them are listed. Default empty, non-breaking. (#204)

## v1.17.0 - 2026-06-09 - Frog Legs and Fairy Tales

### Added

- **The Froglight Cleaver.** A late-game sword that drops a Resource Slime's Froglight when it kills it - the active-play counterpart to the passive frog loop. It hits clearly harder than a netherite sword and is fire-resistant. Looting raises the Froglight yield, and a brewed slime's effect carries onto the Froglight, exactly like a frog eating it. Crafted in a sword shape from boss Froglights (Nether Star blade + Dragon Egg hilt) ringed with Dragon's Breath, so it is pure endgame. Config-gated (`froglight_weapon.enabled`, default on). (#212)
- **Potion of Hopping.** A brewed potion (awkward potion + raw Frog Legs) that makes you leap **forward** when you jump - a frog hop, distinct from vanilla's vertical Jump Boost - and softens your falls so leaping off a ledge does not punish. Level II hops further. Config-gated (`hopping.enabled`, default on). (#215)
- **Princess's Kiss.** The Ender Dragon - the princess - drops a Princess's Kiss when slain. Right-click any frog with it to turn it into a villager (the Frog Prince), a timed conversion with particles like the zombie-villager cure. The villager is a plain, unemployed one (not a nitwit), so it can take a job - a single Kiss bootstraps a whole trading economy, which is why it is gated behind the dragon. Config-gated (`princess_kiss.enabled`, default on). (#216)
- **Frog Legs.** Killing a frog now drops Frog Legs - a renewable meat for a skyblock where animals are scarce. Any frog (vanilla, Resource, or a modded frog in the `productivefrogs:frogs` tag) drops 1-2 Raw Frog Legs, Looting-scaled, and Cooked Frog Legs instead if it died on fire (the cow/chicken behavior). Raw cooks into Cooked in a furnace, smoker, or campfire; raw is chicken-tier food, cooked a step up. Config-gated (`frog_legs.enabled`, default on) for packs that want losing a frog to sting. (#194)
- **Frog Legs Soup.** A bowl meal one step above Cooked Frog Legs (rabbit-stew-tier food): craft a bowl with cooked frog legs and two mushrooms, eat it for the bowl back. Shares the `frog_legs` toggle. (#217)
- **The Frog Net.** A reusable tool that catches a frog into the item (right-click the frog) and releases it elsewhere (right-click a block), so a bred-up Resource Frog can be relocated or a Terrarium restocked without leashing or killing it. Works on any frog, vanilla or modded (anything that is a vanilla frog or subclass, or whose entity type is in the `productivefrogs:frogs` tag a pack can extend). For a Resource Frog the whole entity is preserved - species, the bred Appetite/Bounty/Reach stats, persistence, health, and a custom name - and a loaded net shows the caught frog's stats in its tooltip. Config-gated (`frog_net.enabled`, default on). Modelled on Productive Bees' bee cage. (#205)

- **Config toggle to disable Brewed Froglights** (`brewed_froglights.enabled`, default on - no change for existing packs). When off, a frog eating an effect-carrying slime drops a plain Froglight (no captured effect), and any already-brewed Froglights go inert: they keep their stored effect but apply no placed-aura / held / worn buff and read as plain. Lets a pack keep the resource loop without the potion-aura system. (#195)
- **Config toggles for the Slime Milker and Slime Churn** (`appliances.slimeMilker` / `appliances.slimeChurn`, default on). When off, the appliance is uncraftable and hidden from JEI and the creative tab; an already-placed block still works. Lets a pack ship a subset of the production appliances. (#196)
- **Config toggles for the Froglight Crucible and Casting Mold** (`appliances.crucible` / `appliances.castingMold`, default on). When off, the appliance is uncraftable and its JEI lane (melt / cast categories) is hidden, along with the creative-tab entry; a placed block still works. Lets a pack ship the resource loop without the melt-and-cast fluid lane. (#196)
- **Per-catalyst config toggles for Slime Milk catalysts** (`slime_milk_catalysts.count` / `.speed` / `.quantity` / `.infinite`, default on; each effective only when the catalysts master `enabled` is on). Lets a pack keep some catalysts while removing others - for example turn off the Infinite (Endless) catalyst while keeping Count, Speed, and Quantity. A disabled catalyst is uncraftable, hidden from JEI and the creative tab, and inert if dropped into a source or Sprinkler (the item is left for the player); upgrades already applied to existing sources are still honoured. (#201)

### Changed

- **The Terrarium Hatch now auto-collects Raw Frog Legs.** A frog that dies inside the enclosure (eaten by another, fall damage, etc.) drops Raw Frog Legs into the cavity; the Hatch vacuums them up alongside slimeballs, magma cream, and froglights so the meat pipes out with everything else. (#194)
- **Terrarium Incubators are now optional.** A Terrarium forms with zero Incubators - add them only if you want it to breed frogs in place; otherwise lead or net frogs in yourself. (Previously the structure refused to form without at least one Incubator.)
- **The Terrarium Controller GUI shows just the live frog count, not `count / cap`.** The cap is only an Incubator release gate, not a hard population limit (you can lead or net more frogs in past it), so the old `10 / 8` readout looked like a broken cap. The Incubator GUI still shows the cap that actually governs its releases.

### Fixed

- **Crucible plastic and pink_slime melts now yield exactly one bucket.** Every other fluid melt outputs 1000 mB (one bucket), but the Industrial Foregoing plastic Froglight melted to 1350 mB of latex (a partial-bucket remainder) and the pink_slime Froglight to 2000 mB (a silent double). Both now output 1000 mB, so "one Froglight melts into one bucket" holds across the board. (#223)
- **Bucketing a bred tadpole no longer wipes its stats.** Scooping a Resource Tadpole that carried inherited Appetite/Bounty/Reach into a bucket and placing it back down dropped those stats, so it matured into a baseline 1/1/1 frog. The bucket now carries the inherited stats the same way a world save does, so a bucketed tadpole grows into the frog it was bred to be.

## v1.16.0 - 2026-06-08 - The Terrarium

### Added

- **The Terrarium.** A sealed multiblock habitat that automates the whole frog loop. Pipe Slime Milk into the Controller and it feeds the ceiling Sprinklers, which rain slimes down into the enclosure; frogs raised in the Incubators eat them, and the Froglights they produce drop straight into the Hatch for piping out. Build the box, seal it, and it runs itself - the hand-operated Milker and Churn were the stepping stones to this.
- **Sprinklers spawn just like a placed milk source.** Each Sprinkler holds a bucket of milk and rains its variant's slimes into the cavity on the same rules a source block uses - cadence, budget, and catalysts (Speed, Quantity, Count, Endless) all carry through from the bucket. You can drop catalyst items onto a Sprinkler from above to upgrade it in place, and a filled Sprinkler drips its milk's colour so you can read it at a glance.
- **Incubators raise frogs with stats intact.** Drop a bottled frog egg into an Incubator and it grows a frog inside the habitat over the full egg-to-frog lifecycle, bred stats preserved. Frogs bred inside the Terrarium lay back into an Incubator instead of seeking water, and feeding an incubating Incubator a Sweetslime hurries it along. The habitat enforces a frog-population cap - Incubators hold matured frogs and release them as space frees, so it never overflows.
- **The Hatch gathers everything.** Froglights land in it directly with no item to chase, and it auto-collects loose slimeballs, magma cream, and froglights from the cavity. It opens as an ordinary chest, and when it's full the frogs stop eating so nothing is wasted.
- **Readouts everywhere.** Jade look-at tooltips and a status screen on the Controller show whether the structure is formed (and the first problem if not), the buffered milk, the Sprinkler/Incubator counts, and the live frog population; JEI shows the blocks and their recipes.
- **Configurable.** New `terrarium.*` config tunes the slime and frog caps, the Controller's milk buffer, the Sweetslime speed-up, and more.

Automation ships in the 1.x line - "V2 is just a name, not a rule."

## v1.15.0 - 2026-06-08 - The Slime Churn + Just Dire Things support

### Added

- **The Slime Churn.** The Slime Milker run backwards: a hand-operated block that turns Slime Milk back into captured slimes in buckets, with no entity to chase. Load a variant Slime Milk bucket plus some empty buckets, and it fills them with that variant's Slime Buckets on the exact rules a placed milk source spawns by - same cadence, same per-bucket budget, same catalyst behavior (Count, Speed, and Infinite all carry through). The milk bucket drains visibly as it works and can be pulled out half-spent to finish in-world, and the emptied milk container comes out its own slot, so a hopper draining slime buckets never grabs an empty - loop the empty straight back into the input for a self-feeding line. (#187)
- **Just Dire Things support.** With Just Dire Things installed, its resources farm through the frog loop: Ferricore (Cave), Blazegold and Celestigem (Infernal), and Eclipse Alloy (Void). Prime a slime with the ingot or gem, feed it to the matching frog, and smelt the Froglight back into the resource. Pipe-automatable Slime Milk included, like every variant. (#188)
- **Just Dire Things fuels, farmed through the Crucible.** Blaze Ember, Voidflame Coal, and Eclipse Ember run as a fuel lane with no furnace step: prime a slime with the coal, feed it to its frog, and melt the Froglight in the Froglight Crucible straight into a bucket of the matching refined fuel. The frog loop skips Just Dire Things' own coal-to-fuel refining chain entirely - the refined fuel is the payoff. (#188)

## v1.14.0 - 2026-06-07 - Brewed Froglights + the boss tier

### Added

- **Brewed Froglights.** Splash or linger a potion onto a slime before a frog eats it, and the Froglight it drops captures that effect. A placed brewed Froglight is an aura: right-click to toggle it, and while it's on it bathes everything in range in the effect (good or bad - a Poison or Wither Froglight makes a defensive perimeter, a Regeneration one a healing room). Held in your main hand or offhand, it buffs just you. It glows while active and its tooltip names the effect. Negative effects are allowed on purpose.
- **Curios slot for Brewed Froglights.** With Curios installed, a brewed Froglight goes in a dedicated Froglight charm slot (one at a time) and buffs you while worn - so you keep the effect without giving up a hand. Only brewed Froglights fit the slot.
- **Boss and endgame resources.** Four trophy resources become farmable: Wither Skeleton Skull, Nether Star, Dragon Egg, and Dragon Breath. They're earned, not stumbled into - you have to prime the first slime with the real drop (kill the wither, spend the dragon egg), and a farm only runs once you've built an altar: the matching catalyst block on all six sides of the Slime Milk source. Four new catalyst blocks craft from the boss resource itself.
- **Boss Slime Milk is toxic.** Standing in a boss resource's Slime Milk inflicts Wither on players - the reason you wall those sources off behind a catalyst altar. The slimes it spawns are unharmed, and creative players are immune.
- **Five more vanilla resources** join the roster: bone, string, gunpowder, rotten flesh (Bog) and magma cream (Infernal).
- **Refined Glowstone** is farmable with Mekanism installed (Infernal), alongside the Refined Obsidian already supported.

### Fixed

- **Resource Slimes show their inner block again.** A batch of recently-added slimes (ice, snow, the mob-drop and boss resources, and others) were rendering without the little block suspended inside them; their inner-surface textures are now generated. The Snow Slime in particular had been missing its inner block since it shipped.

## v1.13.0 - 2026-06-07 - Vanilla roster fills in, water & lava move to Cave

### Added

- **Six more vanilla resources are farmable.** Breeze rod (Cave), ghast tear (Infernal), phantom membrane (Void), armadillo scute and honeycomb (Bog), and turtle scute (Tide) all join the roster - prime a slime with one, feed it to the matching frog, and smelt the Froglight back into the same resource. These are the mob-adjacent drops the earlier vanilla sweep left out, several of them a real grind to farm by hand.
- **JEI shows the Casting Mold's recipes.** Pressing U on an ingot now surfaces the Mold as a source, and the category shows molten -> ingot the way the Crucible category shows Froglight -> molten.

### Changed

- **Water and Lava slimes are now Cave resources** (they were Tide and Infernal). They exist to feed the Froglight Crucible's renewable-fluid lane, and water and lava are day-one bucket resources - gating them behind the Tide and Infernal frogs put renewable fluids too late in the journey. The Cave Frog now farms both. **Migration: the Lava Slime's primer changed from magma block to pointed dripstone** (cave-native and renewable, matching the new home). If you primed lava slimes with magma block, switch to pointed dripstone - existing Lava slimes, Froglights, buckets, and milk all carry over and re-home to Cave on world load; only the priming item changed. Water keeps its kelp primer.

## v1.12.0 - 2026-06-06 - The Froglight Crucible + Casting Mold

### Added

- **The Froglight Crucible.** A heated basin that melts Froglights into real fluids - no GUI, no power, just heat from the block below. Park it over a torch, campfire, lava, or fire and right-click Froglights in; pull the fluid out with a bucket or pipes. Water and Lava Froglights melt into water and lava (a full bucket each), making both renewable on a skyblock. Hotter sources melt faster, the heat ladder is data-driven so packs can extend it, and hoppers can feed Froglights in for hands-off melting. (#153, #156)
- **Molten metal at ore-doubling yield.** Metal Froglights (iron, copper, gold, and the cross-mod metal roster) melt into molten fluids worth two ingots each. On a pack with AllTheOres installed the Crucible produces ATO's own molten metals, so they flow straight into existing tanks, pipes, and recipes; without it, Productive Frogs mints its own. (#157)
- **The Casting Mold.** Solidifies molten metal back into ingots - pour it in with a bucket or pipes, or stack heat / Crucible / Mold into a three-block tower and the Mold drinks from the Crucible directly, no pipes needed. Comes with a proper GUI: a fluid gauge you can hover for the exact contents, a casting progress arrow, and an output slot hoppers can empty. Fluid only goes in - once committed, molten leaves as an ingot. (#158)
- **Froglights are heat sources too.** A placed Lava Froglight heats a Crucible like lava, a Blaze Froglight burns hotter than fire, and Powah's Blazing Crystal Froglight is the hottest heat plate in the mod - your farmed decor doubles as the smelter's fire.
- **JEI shows the whole lane.** Two new categories: what melts into what, and the full heat-source ladder with each source's strength.
- **Industrial Foregoing liquids.** With IF installed, the Pink Slime Froglight melts into Pink Slime fluid and the Plastic Froglight melts down into Latex - two items' worth each, feeding IF's own machines.
- **Ice and Snow slimes.** Two new Tide variants - prime with an ice block or snow block, smelt the Froglight back into the same. Closes a real skyblock gap: with no cold biome there was previously no path to either material family (and Powah's Dry Ice needs blue ice to craft). The Ice Froglight also melts in the Crucible into a bucket of water. One placement note: Froglights glow at full brightness, and vanilla ice melts under bright light - don't light an ice farm with its own produce. (#155)

### Fixed

- **Pick-block on a placed Froglight keeps its variant.** Middle-clicking a placed variant Froglight previously handed back a generic Froglight with no variant.

## v1.11.0 - 2026-06-06 - Flux Networks + Powah compat, Blaze rod

### Added

- **Flux Networks support.** With Flux Networks installed, Flux Dust is farmable through the frog loop: prime a slime with flux dust, feed it to the Infernal Frog (flux dust is born from redstone dropped on obsidian, so it shares obsidian's tier), and smelt the Froglight back into flux dust. One dust variant covers the whole Flux chain, since ingots, blocks, and cores all craft from dust. Pipe-automatable Slime Milk included, like every variant. (#145)
- **The full Powah material ladder.** Energized Steel and Uraninite join the Cave Frog and Dry Ice joins the Tide Frog - with the four crystals farmable since v1.2, every Powah material now runs on frogs. Uraninite matters most in skyblock: there's no ore gen, so without it Powah can't bootstrap. (#146)

### Changed

- **The Blaze resource is now the blaze rod.** Prime a slime with a blaze rod and the Blaze Froglight smelts back into a blaze rod (both were blaze powder before). Rods are what blazes actually drop, each rod crafts into two powder, and the Froglight already burned as rod-equivalent furnace fuel - now every lane agrees. The Spawnery's Infernal primer is unchanged (still blaze powder). Existing Blaze slimes, Froglights, buckets, and milk all carry over; only the priming item and smelt output change. (#148)

### Fixed

- **Niotic and Nitro crystal colors were swapped.** Niotic slimes, Froglights, buckets, and milk now render cyan and Nitro red, matching Powah's actual crystals. Colors are sampled from the crystal textures themselves.

## v1.10.0 - 2026-06-06 - Obsidian joins the Infernal species

### Changed

- **Obsidian and Refined Obsidian slimes are now Infernal, not Cave.** Mining obsidian requires a diamond pickaxe, so it can't sit in the first-tier Cave roster - obsidian is the gate to the Nether itself, and Mekanism's Refined Obsidian follows its obsidian lineage. The Infernal Frog now eats both (previously the Cave Frog), and split-discovery rolls them in the Infernal pool. Existing slimes update themselves on world load; Froglights, Slime Buckets, and Slime Milk are all variant-keyed and unaffected. (#142)

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

## v1.0.1 - 2026-05-24

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

## v1.0.0 - 2026-05-24

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
