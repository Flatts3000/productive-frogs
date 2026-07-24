# Productive Frogs roadmap

Where the mod is going. This is the canonical "what's shipped, what's coming, what's parked" document. The [CHANGELOG.md](./CHANGELOG.md) tracks each shipped release in detail; this doc tracks the runway. Engineering rationale and scope boundaries live in [docs/versioning.md](./docs/versioning.md).

Living document. Anything below a "shipped" version can move between tracks based on player feedback, modpack-author requests, or scope realities.

Targets Minecraft 1.21.1 / NeoForge 21.1.230 / Java 21.

---

## Shipped

The mod is fully playable today. Find a slime in its themed biome, prime it with the resource you want, feed it to the matching frog, and smelt the Froglight it drops back into that resource. Slime Milkers and Slime Milk source blocks keep the loop running without you standing over it. Everything in this section is live on CurseForge.

### v1.0 (shipped 2026-05-24): foundation + appliances

The playable core. Six frog species (Bog, Cave, Geode, Tide, Infernal, Void), each spawning in its themed biome and eating only the slimes that match it. The starter resource set: iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, ender pearl. Right-click a wild slime with the resource to convert it. The Slime Milker (a furnace-style appliance) and Slime Milk source blocks keep production going hands-off. JEI Information pages and Jade in-world tooltips ship out of the box. Adding a new resource is a single JSON file, no Java.

### v1.1 (shipped 2026-05-25): vanilla resource coverage

Every vanilla resource that fits cleanly under one of the six species became farmable, with no new species and no new code. Bog frogs gained the mob drops (bone, gunpowder, clay, rotten flesh, string, leather, feather); Cave gained glow ink sac, obsidian, echo shard; Geode gained amethyst; Tide gained ink sac and prismarine crystals; Infernal gained netherite scrap, glowstone, soul sand, soul soil, netherrack, blaze, quartz; Void gained chorus fruit and shulker shell.

### v1.2 (shipped 2026-05-25): cross-mod variant pools

Drop Productive Frogs into a modded pack and the relevant resource sets light up automatically, no configuration. Tech-mod metals and gems are covered out of the box: Mekanism, Create, Thermal, Applied Energistics 2, AllTheOres, Mystical Agriculture, Powah, Industrial Foregoing, and more. Each entry gates behind a soft mod-loaded check and silently skips when its source mod is absent, so the same jar is safe to drop into any pack. This release also added a built-in, opt-in debug-logging framework that makes troubleshooting a misbehaving setup far easier.

### v1.3 (shipped 2026-05-26): cross-mod crush yields

The one cross-mod piece that was missing from the v1 line. With Mekanism, Immersive Engineering, or EnderIO installed, crushing a metal Froglight yields double the resource instead of the single unit you get from smelting, matching how those mods already reward ore processing. It ships as optional `mod_loaded` recipes, so it activates only when one of those mods is present and changes nothing otherwise. AllTheOres, when present, broadens the metals covered. Design: [docs/v1_3_crush_recipes.md](./docs/v1_3_crush_recipes.md).

### v1.4.0 (shipped 2026-05-26): the Spawnery (skyblock bootstrap)

A new V1 appliance for skyblock and other restricted-biome packs: the **Spawnery**.
It is the frog-side analogue of the Slime Milker - a furnace-style block that turns
glass bottles into bottled frogspawn, fueled by slime balls (one ball per bottle).
A primer is required: a slime ball primes plain vanilla frogspawn, or a species
primer (iron ingot for Cave, amethyst shard for Geode, bone for Bog, prismarine
shard for Tide, blaze powder for Infernal, an ender pearl for Void) primes that
species' eggs. It is
**disabled by default** - a normal world has swamps and never needs it - and a pack
flips one config flag to turn it on. The primer set is pack-overridable via item
tags. This unblocks the frog side of the loop where vanilla frogspawn is
unreachable. Spec + design: [docs/spawnery.md](./docs/spawnery.md).

### v1.4.1 (shipped 2026-05-26): Jade tooltips + tinted milk

Quality-of-life polish. Optional Jade look-at tooltips for the appliances - the Slime Milk
source block shows its remaining spawn count, and the Slime Milker / Spawnery show cook
progress while running. Flowing and spread Slime Milk now tints per-variant, so a stream
running off a source block matches the source's colour instead of falling back to the base hue.

### v1.5.0 (shipped 2026-05-27): frog stat breeding

The headline progression system. Resource Frogs gain three stats - **Appetite**, **Bounty**, and **Reach** (1-10 each, 10/10/10 maxed) - that govern how productive they are in the loop. You improve a frog by **breeding** it: feed two same-species frogs a **Sweetslime** treat (slime ball + sugar), run the stock vanilla frog breeding process, and the offspring inherits a blend of its parents' stats with a chance to roll better than either. Keep the winners, cull the duds, re-breed, and ladder a species' line to maxed, in the spirit of Productive Bees' gene-maxing. Frog-only and self-contained (no Terrarium dependency). The deferred piece is the cosmetic "prize" look for maxed frogs - a follow-up render-layer / art pass, not a redesign. Full spec: [docs/frog_breeding.md](./docs/frog_breeding.md).

### v1.5.1 - v1.5.3 (shipped 2026-05-27 to 2026-05-28): roster + JEI polish

Small follow-ups: lapis moved to the Geode frog where it belongs (v1.5.1); the Cave frog gained steel (v1.5.2); and JEI gained recipe pages for the Spawnery and Slime Milker so you can look up how to make bottled frogspawn and slime milk (v1.5.3).

### v1.6.0 (shipped 2026-05-28): the Bog makeover + Slime Milk reliability

The **Bog frog became an organic / swamp species**: it gained clay's neighbours - dirt, mud, moss, mycelium, and lily pad - plus Industrial Foregoing **plastic**, while the Mystical Agriculture essences moved to the **Void** frog and four mob-drop slimes (bone, gunpowder, rotten flesh, string) were retired. The Bog Spawnery primer is now a **clay ball** (was a bone). Alongside the makeover, a batch of **Slime Milk reliability fixes** - flowing milk no longer washes away frogspawn, water sources, or other milk sources; frogs (and players) no longer drown in milk; re-bucketing a source keeps its remaining-spawns count; and the Jade spawn counter updates live - plus **deterministic, pack-tunable hatch / tadpole-growth / breeding timers**. Frogs raised from crafted or Spawnery frogspawn now start at baseline stats (breeding is the only way up). **Breaking for modpacks:** items carrying a retired variant lose their names + recipes; see [CHANGELOG.md](./CHANGELOG.md).

### v1.7.0 (shipped 2026-05-29): Slime Milk catalysts

The first real boost to hands-off production - an early-game stopgap ahead of the
V2 Frog Habitat. Four craftable **catalysts** dropped into a placed Slime Milk
source buff it in place: **Count** (more slimes before it runs dry, uncapped),
**Speed** (spawns faster), **Quantity** (more slimes per spawn), and **Infinite
Count** (never runs dry; built from Count catalysts). The buffs save to the source
and survive re-bucketing, Jade shows the upgrade levels, and a dropper aimed into
the pool feeds catalysts automatically. Enabled by default; a pack can disable or
retune them. Still a single-block, hand-applied V1 buff - no power or pipes. Spec:
[docs/slime_milk_catalysts.md](./docs/slime_milk_catalysts.md).

### v1.8.0 (shipped 2026-05-29): Automatable Slime Milk

Each variant's Slime Milk is now its own fluid, so a chosen variant can be farmed
fully hands-off. A fluid-automation mod (Just Dire Things Fluid Collector/Placer,
Mekanism, Pipez, or any fluid handler) can collect a variant's milk from a source,
move it through pipes and tanks, and place it back as the same variant - no player
in the loop. The Slime Milker and the empty-bucket round-trip work exactly as
before. One caveat: catalyst buffs survive a normal bucket re-pickup but are
**not** preserved when milk passes through a tank, so an automated line spawns at
base rates. **Breaking for existing worlds:** old placed Slime Milk and old milk
buckets do not carry over - re-mill from Slime Buckets. Design:
[docs/automated_milk_variants.md](./docs/automated_milk_variants.md).

### v1.8.1 - v1.8.3 (shipped 2026-05-30 to 2026-05-31): bucket fixes, drain timing, Froglight fuel

A run of small fixes and one new convenience. **v1.8.1:** releasing a slime from a Slime Bucket no longer dumps water, a released slime is always size 1, and a dispenser loaded with a Slime Bucket places the slime instead of just ejecting the bucket. **v1.8.2:** a Slime Milk source no longer lingers for one extra spawn after its counter hits zero - the final spawn and the source draining away now happen on the same tick. **v1.8.3:** Coal and Blaze Froglights burn as furnace fuel - a Coal Froglight burns like a coal item and a Blaze Froglight like a blaze rod, so the Froglights you farm from those resources double as fuel. Every other Froglight stays purely decorative.

### v1.9.0 (shipped 2026-05-31): Refined Storage support

Refined Storage's three processors and Quartz Enriched Iron became farmable through the frog loop, each with its own pipe-automatable Slime Milk. Silicon, the resource RS shares with Applied Energistics 2, now works with either mod installed (it was AE2-only before).

### v1.9.1 - v1.9.2 (shipped 2026-06-02 to 2026-06-05): beta promotion + Chorus fix

**v1.9.1:** promoted from alpha to beta on CurseForge - V1 is feature-complete and cross-mod compat has been smoke-tested in a real pack. **v1.9.2:** the Chorus Fruit Froglight smelts back into chorus fruit (its primer) instead of popped chorus fruit, restoring loop self-sufficiency for the variant.

### v1.10.0 (shipped 2026-06-06): obsidian joins the Infernal species

Obsidian and Mekanism's Refined Obsidian moved from the Cave species to Infernal (#142): obsidian requires a diamond pickaxe, so it must come after diamond (Geode) in the resource chain - and obsidian is the Nether gate itself. The Infernal Frog now farms both. Existing slimes update themselves on world load; Froglights, buckets, and milk are variant-keyed and unaffected.

### v1.11.0 (shipped 2026-06-06): Flux Networks + Powah compat, Blaze rod

**Flux Networks:** flux dust is farmable (Infernal - flux dust is born from redstone dropped on obsidian, so it shares obsidian's tier; #145). **Powah roster completed:** Energized Steel and Uraninite joined Cave and Dry Ice joined Tide (#146), so with the four crystals farmable since v1.2 the whole Powah material ladder now runs on frogs - and the swapped Niotic/Nitro colors were fixed (niotic is cyan, nitro red, texture-faithful). **Blaze resource is the rod** (#148): prime with a blaze rod, smelt the Froglight back into a rod - rods are the actual blaze drop and each crafts into two powder; the Spawnery's Infernal primer keeps using blaze powder. Also: the dev environment now carries every smoke-testable provider mod, and the cross-mod gametest derives its expectations from the live mod list.

### v1.12.0 (shipped 2026-06-06): the Froglight Crucible + Casting Mold

The heat-driven melt-and-cast lane specced in [docs/froglight_crucible.md](./docs/froglight_crucible.md). The **Froglight Crucible** (#153, #156) is a GUI-less heated basin: heat comes from the block below (data-map-driven, Ex Deorum's ladder, pack-overridable - and placed Lava / Blaze / Blazing Crystal Froglights are heat sources themselves), Froglights right-click or hopper in, fluid leaves by bucket or pipe. Water and Lava Froglights melt to their fluids; metal Froglights melt to **molten fluids at ore-doubling yield** (2 ingots' worth, 180 mB), deferring to AllTheOres' molten when present (#157). The **Casting Mold** (#158) - the mod's third GUI block - casts molten back to ingots at 90 mB each; stacked heat / Crucible / Mold towers pull directly, no pipes (loose composition like hopper-under-furnace, **not** a formed multiblock - why this stayed v1.x). Melt pacing is anchored on the metal lane: vanilla-furnace pace per ingot over lava, blast-furnace pace over a Blaze Froglight, with the doubling as the tower's reward. Also: Industrial Foregoing liquids (Pink Slime -> pink slime fluid, Plastic -> latex), **ice + snow Tide variants** (#155 - skyblock had no cold biome; ice also melts to water), JEI melt + heat-ladder categories, and the shared `PFContainerScreen` base for the now-three GUIs. This retired the old "no native crusher" guideline: the crush lane stays delegated to crusher mods, but the first-party 2x comes from melt-and-cast.

### v1.13.0 (shipped 2026-06-07): vanilla roster fills in, water & lava move to Cave

Six mob-adjacent vanilla resources became farmable (#161): breeze rod (Cave), ghast tear (Infernal), phantom membrane (Void), armadillo scute + honeycomb (Bog), turtle scute (Tide) - the drops the v1.1 sweep left out. **Water and Lava slimes moved from Tide/Infernal to Cave** (#164): they feed the Crucible's renewable-fluid lane and are day-one resources, so they belong in tier 1; the Lava Slime's primer changed magma block -> pointed dripstone to match the cave-native home (existing slimes re-home on world load, only the priming item changed). Plus the Casting Mold JEI category (#160), completing the v1.12 melt-and-cast JEI lane.

### v1.14.0 (shipped 2026-06-07): Brewed Froglights + the boss tier

**Brewed Froglights** (#162/#171): a slime carrying a potion effect when a frog eats it drops an effect-stamped Froglight - a toggleable aura when placed (affects all entities in range; Poison/Wither perimeters, Regen rooms), a self-buff when held in hand, or a worn charm in a dedicated Curios slot. JEI-invisible by construction (no per-effect row explosion); right-click toggle + glint + tooltip. **The boss/endgame tier** (#172/#173/#175/#184): Wither Skeleton Skull, Nether Star, Dragon Egg, and Dragon Breath plus five mob drops (bone/string/gunpowder/rotten flesh/magma cream). The four bosses are doubly gated - prime-only (weight 0, never from split-discovery; you spend the real drop to start) and a **6-face catalyst altar** that must wall the Slime Milk source before it spawns - and their milk is **toxic to players** (Wither), the narrative reason to cage them. Also: Mekanism Refined Glowstone (#180), and a fix baking the inner-block surface onto a batch of recently-added slimes (ice/snow/mob/boss) that had been rendering hollow. Roster: 94 variants.

### v1.15.0 (shipped 2026-06-08): the Slime Churn + Just Dire Things

**The Slime Churn** (#187): the Milker run backwards - load a variant Slime Milk bucket plus empties and it fills them with that variant's captured slimes, on the placed-source spawn economy (same cadence, budget, and catalysts), with a separate empty-bucket output slot so a self-feeding line never grabs an empty. **Just Dire Things support** (#188): Ferricore (Cave), Blazegold + Celestigem (Infernal), and Eclipse Alloy (Void) farm through the loop, plus Blaze Ember / Voidflame Coal / Eclipse Ember as a Crucible fuel lane (melt the Froglight straight into refined fuel, skipping JDT's own refining chain).

### v1.16.0 (shipped 2026-06-08): the Terrarium

The flagship multiblock (#185/#193). A sealed box automates the entire loop: Slime Milk piped into the **Controller** feeds ceiling **Sprinklers** that rain slimes into the cavity; frogs raised in **Incubators** (full egg-to-frog lifecycle, bred stats preserved, Sweetslime speed-up, frog-cap hold/release) eat them; and the Froglights land in the **Hatch**, which also auto-collects loose drops and reads as a chest. Sprinklers carry their milk's catalysts and accept catalyst tosses from above; Jade tooltips and a Controller status screen show structure state, buffered milk, machine counts, and live frog population; JEI shows the blocks. Tunable via `terrarium.*` config. Automation ships in the 1.x line - **"V2 is just a name, not a rule."**

### v1.17.0 (shipped 2026-06-09): Frog Legs and Fairy Tales

A big content drop plus a wide config sweep. **Frog Legs** (#194): killing any frog - vanilla, Resource, or a modded frog in the `productivefrogs:frogs` tag - now drops Frog Legs, a renewable meat for a skyblock where animals are scarce (raw cooks to Cooked in any furnace; **Frog Legs Soup** (#217) is a bowl meal a step above). **Potion of Hopping** (#215), brewed from raw Frog Legs, makes you leap *forward* when you jump and softens your falls - a frog hop, distinct from vanilla Jump Boost. **The Froglight Cleaver** (#212) is an endgame sword crafted from boss Froglights that drops a Resource Slime's Froglight when it kills it - the active-play counterpart to the passive frog loop, Looting- and brewed-effect-aware. **Princess's Kiss** (#216), dropped by the slain Ender Dragon, right-clicks a frog into an unemployed villager (the Frog Prince), bootstrapping a whole trading economy off a single dragon kill. **The Frog Net** (#205) catches a frog into the item and releases it elsewhere - for a Resource Frog the whole entity is preserved (species, bred stats, name), modelled on Productive Bees' bee cage. The Terrarium Hatch now also vacuums Raw Frog Legs, Incubators became optional (form the box without them), and the Controller GUI shows just the live frog count. The **config sweep** lets a pack ship a subset: toggles for Brewed Froglights (#195), the four appliances (Milker / Churn / Crucible / Casting Mold, #196), and per-catalyst switches (#201). Fixes: Crucible plastic / pink_slime melts now yield exactly one bucket (#223), and bucketing a bred tadpole no longer wipes its stats (#210). Every new feature is config-gated, default on.

### v1.18.0 (shipped 2026-06-09): Made to Measure

The pre-release config suite - the knobs a modpack author needs to scope the mod without datapack surgery. A new `[variants]` section (#203) disables individual resources (`disabledVariants`), whole species (`disabledCategories`), or a host mod's variants (`disabledIntegrations`, #204); a disabled variant is unprimable, gone from split-discovery, and hidden from JEI and the creative tab. It is a **soft hide** - the registry entry stays, so already-placed content keeps working and re-enabling restores everything with no save surgery. Two master switches drop whole subsystems: `boss.enabled` (#200) removes the boss tier (the four prime-only variants, the catalyst-altar blocks, and the toxic milk) and `frog_stats.enabled` (#202) removes the Appetite / Bounty / Reach breeding layer (Sweetslime uncraftable, every frog at baseline, stored stats **frozen not deleted** so re-enabling restores them). All default on and non-breaking; gating applies on world reload.

### v1.19.0 (shipped 2026-06-09): Stone Soup

Renewable lava, two ways. **Lava Froglights now burn as furnace fuel** worth one lava bucket (20,000 ticks, a hundred smelts), joining the Coal and Blaze Froglights - so the renewable lava loop doubles as a no-bucket solid fuel (#231). **The Froglight Crucible melts stone straight to lava** (Ex Deorum heated-crucible parity, #230): drop cobblestone, stone, gravel, or netherrack into a heated Crucible and it melts to lava on the same heat-from-below loop the Froglight lane uses, no frog required - amounts and tags copied verbatim from Ex Deorum (cobblestone / stone / gravel 250 mB each, netherrack 500 mB). Pure-data recipes (one JSON per input), so a pack retunes or extends them freely; the Crucible's right-click insert now accepts any block with a melt recipe, not just Froglights.

### v1.19.1 (shipped 2026-06-10): Survival of the Fittest

Breeding balance. The default frog-stat improvement chance rises from `0.20` to `0.40` per stat (#232), so a breed now improves at least one of the three stats about 78% of the time (up from ~49%) - a run of breeds with no gain becomes rare while a single breed still usually moves only one or two stats, not all three. Tuning only: the inheritance mechanic is unchanged and packs can still retune `breeding.improvementChance`.

### v1.19.2 (shipped 2026-06-10): No More Spilled Milk

Bug-fix and advancements release. A curated Productive Frogs advancement tab for standalone play - an entry node, one per species you farm, one per boss catalyst altar built, and one for crafting the production machines (#183). Three fixes: the Slime Bucket no longer dumps a vanilla water source when you release the slime (#234); water, lava, and foreign modded fluids no longer flow into and wash away Slime Milk source blocks (#235); and Jade's tadpole "Growing time" reads correctly when a pack speeds up tadpole growth (#238).

### v1.20.0 (shipped 2026-06-10): Full Bloom

The stability milestone - promoted out of beta to a stable Release on CurseForge. No gameplay changes since v1.19.2; across the v1.9-v1.19 line the full V1 scope (six species, the data-driven variant roster, the appliances, frog stat breeding, the boss/endgame tier, the Terrarium, the cross-mod variant pools) was built out and soaked in the Sky Frogs pack, and the pre-release config suite (#200/#202/#203/#204) shipped in v1.18. The CurseForge release channel is now Release.

### v1.21.0 (shipped 2026-06-13): By the Book

The in-game guide book and a new frog perch. A **Patchouli guide book** (#243) - craft a book with a slime ball - walks the whole mod start to finish (the core loop, every species, spawning slimes, Resource Slimes and Slime Milk, milk catalysts, Froglights and Brewed Froglights, the appliances, the Terrarium with a build diagram, and the four boss catalyst altars each with its own altar diagram) and is extensible by modpacks via resource packs; it is inert when Patchouli is absent. The **Sweetslimed Lily Pad** (#214) is a frog perch: right-click a placed lily pad with a Sweetslime and a Resource Frog within range claims it (one frog per pad) and holds position on top of the pad while still eating same-species slimes in reach - pin a frog over a hopper beside a Slime Milk source for a hands-off collection point, the active counterpart to the Frog Net and the Terrarium. This release also de-tediumed the breeding stat grind with a **blend-then-climb** inheritance (offspring stat is the parent average, then a chance to climb one above it; no regression, and at least one stat improves every breed - #241), and fixed a fully enclosed boss catalyst altar spawning its slime inside the sealed Slime Milk source block (#242).

### v1.22.0 (shipped 2026-06-15): Apex Predators

Two contained, repeatable boss farms. The **End Dragon Altar** (#249) is built on the End exit portal: ring it with End Crystal Receptacles and reinforced froglights under a Dragon Egg capstone, drop an end crystal into each receptacle, and a replica dragon rises and is devoured by **Dragonsbane**, a display frog at the altar's heart - the loot (Dragon Breath Froglight, renewable Dragon Egg Froglight, the Princess's Kiss, XP) lands in the hatch, with no real dragon, boss bar, or portal regrowth. The **Wither Altar** (#247) does the same for the Wither: a soul-forged arena of Reinforced Soul Sand and Blaze Rod Froglights loaded with the full vanilla summon, a replica Wither that charges exactly like a real spawn until **Witherbane** devours it, out comes a Nether Star Froglight (smelts back to a star) and XP - no boss bar, no exploding blocks. Both are boss-tier, gated behind the boss config (`boss.dragon_altar` / `boss.wither_altar`).

### v1.23.0 (shipped 2026-06-29): The Midas Touch

The Equivalence lane (#253) - the post-capstone transmutation engine for **off-roster** items (anything with no Resource Slime variant). Alembic (item + bucket + power -> Mimic Slime Bucket) -> Milker -> Mimic Milk -> Mimic Slimes -> **Midas** (a Kiss-primed frog that eats only Mimic Slimes) -> Prismatic Froglight -> Distiller (+ power) -> the original item, with duplication along the loop the player balances via milk catalysts and breeding. Machines need a Nether Star to craft; **off by default** (`equivalence.enabled`) and wholly inert when disabled. Design: `docs/equivalence_lane.md`.

### v1.24.x (shipped 2026-06-29 to 2026-07-09): Flip the Switch onward

Sprinkler redstone on/off control (#263/#264), the Terrarium Hatch rename, tadpole suffocation + Sweetslime-feed fixes (#276/#277), underwater-breathing parity (#286), the 2.x bucket + Slime Milk art backport (v1.24.6), and the Hatch shift-click dupe + dispenser-pickup fixes (v1.24.7).

### v1.25.0 (shipped 2026-07-24): Second Helpings

A one-time reopening of the frozen line for three features carried back from the 2.x line: the **Slime Milk Basin** (#342) - a milk source that persists when it empties, so you pipe it and leave it; the **Virtual Terrarium** (#341) - one frog's whole eat loop in two blocks, void-tier, with upgrade slots; and a **guidebook pass** (#343) - new entries for both, plus Frog Legs, the Potion of Hopping, the Froglight Cleaver, and the Tadpole Bucket. The predator/Apex path stays out (it needs 26.1's predation). Back to hotfix-only after this.

---

## v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Buffered / auto-upgrading Slime Milker:** the v1 Milker and Spawnery already expose basic hopper I/O (`Capabilities.ItemHandler.BLOCK`), so hoppers can feed and drain them today. The v2 layer is the buffered upgrade - internal slime/milk buffers and auto-cycling so a line keeps running without per-item hopper handoff.

**Frog Terrarium / Habitat block:** SHIPPED in v1.16.0 (see Shipped above) - it landed in the 1.x line per "V2 is just a name, not a rule."

**Auto-feeders:** hopper-fed slime delivery to nearby frogs (the frog side, not the appliance side - distinct from the appliance hopper I/O that already shipped in v1). Alternative to milk-spawn proximity.

**Capacity / efficiency upgrades** for habitat blocks.

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

---

## Long-shot ideas (not committed)

Captured during v1 design. Not on the schedule; revisited when v2 ships and we know how much hook-into-vanilla the framework supports.

- **Deep genetics / breeding tree** (Forestry-style multi-trait inheritance + mutation) layered on top of the specced frog stat breeding above - the next depth pass once that ships.
- **Alchemical slime category** tied to brewing. Infuse with a potion item, the slime takes on that potion's effect, and killing it drops the matching potion (or splashes its effect in an area). Parent could be a "Brew Slime" spawning near witch huts or nether wart.
- **New dimensions** (Frog Realm).
- **Quest / advancement integration.**

---

## Compatibility promise

- v2 datapacks won't break v1 worlds.
- v2 machines remain optional. Every v1 capability stays usable without them.
- Cross-mod compat datapacks are independent of the v1 / v2 split.

---

## Explicitly NOT planned

**Fabric port.** Productive Frogs is NeoForge-only by design and will remain so. See [docs/architecture.md](./docs/architecture.md) for the technical reasoning.

**Native crusher block.** The mod will not ship its own crusher; the 2x *crush* yield stays delegated to external crusher mods (Mekanism / Immersive Engineering / EnderIO, via the optional `mod_loaded` recipes shipped in v1.3.0). *(Softened 2026-06-06: the broader "no first-party 2x lane" guideline behind this entry was retired when the v1.11 Crucible + Casting Mold melt-and-cast lane was greenlit - it was a guideline, not a rule. A literal crusher block remains not planned.)*

**Drop-collection block.** Vanilla hoppers under the frog pen already collect dropped Froglights. The mod will not ship a custom collector.

**Custom Slime Milk containers (jugs / tanks).** Bucket is the only first-party container. The Slime Milk fluid is already accessible to tank/pipe mods (the bucket's fluid capability + vanilla `LiquidBlock`), so bulk storage is covered by those; the mod will not ship jugs or tanks.
