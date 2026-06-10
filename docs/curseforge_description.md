<!--
This file is the canonical source for the CurseForge project Description.

Workflow:
  1. Edit this file when you want to update the CF page.
  2. Copy the body (everything below this comment) into the CF Description
     field at https://authors.curseforge.com/#/projects/1552728/edit
  3. Save on CF.

Why a separate doc instead of reusing README.md: the CF Description is a
self-contained sales / onboarding surface for players who land on the CF
page without ever visiting GitHub. README.md targets contributors and
existing players. They overlap in spirit, diverge in structure.

Guardrails (per project memory):
  - No hard counts of species / variants / features (changes every release)
  - No mod-internal jargon (species-matched, Resource Slime, datapack)
  - No em-dashes (use hyphens, commas, or restructure)
  - Lead with player benefit; concrete examples beat abstract claims
  - Links use absolute GitHub URLs (CF readers don't have repo context)

The Roadmap section here should stay in sync with the canonical
[ROADMAP.md](../ROADMAP.md) at repo root. When the roadmap shifts, edit
ROADMAP.md first, then mirror the changed lines into the Roadmap section
below, then paste this file's body into CF.
-->

**Renewable resources, farmed by frogs.** Prime slimes with iron, copper, diamonds, or whatever resource you want, feed them to your frogs, and harvest the Froglights they drop. Slime Milkers keep the loop running unattended.

## How it works

1. Find a slime (they spawn in themed biomes).
2. Right-click it with the resource you want: iron ingot, copper ingot, diamond, prismarine shard, ender pearl, and more.
3. Feed the slime to the matching frog.
4. The frog drops a Froglight tied to that resource.
5. Smelt the Froglight in a vanilla furnace. Get your resource back.

## Sustained farming

Stop running around hand-priming wild slimes. Build a **Slime Milker**:

* Drop a Slime Bucket of the variant you want into the input slot.
* Wait a short cook. A bucket of matching Slime Milk comes out.
* Place the Slime Milk in the world. It spawns more of the same slime nearby.

Hopper-compatible. No power, no pipes, no multiblocks. The whole loop runs unattended once you've built it.

## Melt and cast

Froglights are not just decor anymore. Build a **Froglight Crucible** and park it over a heat source - a torch works on day one, lava and fire melt faster:

* Water and Lava Froglights melt into real, bucketable water and lava. Renewable lava on a skyblock, no nether trips.
* Metal Froglights melt into molten metal worth two ingots each. Stack a **Casting Mold** on top and it casts the molten straight back into ingots - a three-block smelting tower with no power and no pipes.
* Some of your Froglights are heat sources themselves: a Lava Froglight burns like lava, a Blaze Froglight hotter than fire.

Pipes and hoppers hook into both blocks if you want to automate; buckets and right-clicks do the job if you don't.

## Modpack-friendly

Data-driven and extensible: adding a new resource variant is a single JSON file, no code or recompile needed. JEI Information pages auto-extend to teach the new entry. Cross-mod variants for Mekanism, Create, and more are included; each gates behind a soft mod-loaded check and silently skips when the source mod is absent.

Full authoring guide: [docs on GitHub](https://github.com/Flatts3000/productive-frogs/tree/main/docs).

## Companion mods (recommended, optional)

* **[JEI](https://www.curseforge.com/minecraft/mc-mods/jei)**: Information pages on every Productive Frogs item explain the production role, who hunts what, what smelts to what. Hover an item, press **U** or **R**.
* **[Jade](https://www.curseforge.com/minecraft/mc-mods/jade)**: shows the species and variant in the in-world tooltip.

Both are optional. The mod works fine without them; you just lose the contextual UI.

## Platform

* **Minecraft:** 1.21.1
* **Loader:** NeoForge 21.1.230 (NeoForge only; no Fabric port planned)
* **Java:** 21

## Links

* [GitHub repository](https://github.com/Flatts3000/productive-frogs)
* [Issue tracker](https://github.com/Flatts3000/productive-frogs/issues)
* [Source + design docs](https://github.com/Flatts3000/productive-frogs/tree/main/docs)
* [Roadmap (canonical)](https://github.com/Flatts3000/productive-frogs/blob/main/ROADMAP.md)

## Roadmap

Where the mod is going. The [CHANGELOG](https://github.com/Flatts3000/productive-frogs/blob/main/CHANGELOG.md) tracks shipped releases in detail; this tracks the runway. Engineering rationale lives in the [versioning.md doc](https://github.com/Flatts3000/productive-frogs/blob/main/docs/versioning.md). The canonical source for this section is [ROADMAP.md](https://github.com/Flatts3000/productive-frogs/blob/main/ROADMAP.md).

Living document. Anything below a "shipped" version can move between tracks based on player feedback, modpack-author requests, or scope realities.

### Shipped

The mod is fully playable today. Everything below is live on CurseForge. Targets Minecraft 1.21.1 / NeoForge 21.1.230 / Java 21.

**v1.0 (shipped 2026-05-24): foundation + appliances.** Six frog species (Bog, Cave, Geode, Tide, Infernal, Void), each spawning in its themed biome and only eating the slimes that match it. The starter resource set: iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, ender pearl. The Slime Milker appliance and Slime Milk source blocks keep production going hands-off. JEI Information pages and Jade tooltips out of the box. Adding a new resource is a single JSON file, no Java.

**v1.1 (shipped 2026-05-25): vanilla resource coverage.** Every vanilla resource that fits cleanly under one of the six species became farmable. Bog frogs gained the mob drops (bone, gunpowder, clay, rotten flesh, string, leather, feather); Cave gained glow ink sac, obsidian, echo shard; Geode gained amethyst; Tide gained ink sac and prismarine crystals; Infernal gained netherite scrap, glowstone, soul sand, soul soil, netherrack, blaze, quartz; Void gained chorus fruit and shulker shell.

**v1.2 (shipped 2026-05-25): cross-mod variant pools.** Drop the jar into a modded pack and the relevant resource sets light up automatically, no configuration. Mekanism, Create, Applied Energistics 2, AllTheOres, Mystical Agriculture, Powah, Industrial Foregoing, and more are covered out of the box. Each entry gates behind a soft mod-loaded check and silently skips when its source mod is absent, so the same jar is safe in any pack.

**v1.3 (shipped): cross-mod crush yields.** With Mekanism, Immersive Engineering, or EnderIO installed, crushing a metal Froglight yields double the resource instead of the single unit you get from smelting, matching how those mods already reward ore processing. It activates only when one of those mods is present and changes nothing otherwise. Without a crusher mod, you smelt directly for the single unit.

**v1.4.0 (shipped): the Spawnery.** An optional, config-gated skyblock bootstrap block: feed it glass bottles and slime balls and it produces bottled frogspawn so packs without a swamp can still start the loop. A slime ball primes plain frogspawn; a species primer (iron ingot, amethyst shard, bone, prismarine shard, blaze powder, ender pearl) primes that species. Off by default, and uncraftable + hidden until a pack enables it.

**v1.4.1 (shipped): Jade look-at tooltips and per-variant flowing-milk tint.** Look at a slime, frog, or appliance with Jade installed and the tooltip names the species and variant. Flowing Slime Milk now picks up its per-variant color, matching the source block.

**v1.5 (shipped): frog stat breeding.** Resource Frogs gain three stats - Appetite, Bounty, and Reach - that govern how productive they are. Breed two same-species frogs on a Sweetslime treat and the offspring inherits a blend of its parents' stats with a chance to roll better than either; keep the winners and ladder a line to maxed. Plus roster tidy-ups and JEI recipe pages for the Spawnery and Slime Milker.

**v1.6.0 (shipped): the Bog makeover + Slime Milk reliability.** The Bog frog became an organic/swamp species - it gained dirt, mud, moss, mycelium, lily pad, and plastic, while a few resources moved to better-fitting frogs. Alongside it, a batch of Slime Milk fixes (flowing milk no longer washes away frogspawn, water, or other milk; frogs no longer drown in it; the Jade spawn counter updates live) and predictable, pack-tunable hatch, growth, and breeding timers.

**v1.7.0 (shipped): Slime Milk catalysts.** The first real boost to hands-off production. Craft four catalysts and just toss them into a placed Slime Milk source to buff it: more spawns before it runs dry (uncapped), faster spawning, more slimes per spawn, or an Endless catalyst that makes the source never run dry. The buffs save to the source and ride along when you bucket it back up, the in-world tooltip and the bucket itself tell you what a source carries, and a dropper aimed into the pool feeds catalysts automatically. Enabled by default; a pack can disable or retune them.

**v1.8.0 (shipped): automatable Slime Milk.** A chosen slime can now be farmed completely hands-off. Each variant's Slime Milk is its own liquid, so fluid-automation mods (Just Dire Things, Mekanism, Pipez, and other fluid handlers) can pump milk out of a source, carry it through pipes and tanks, and place it back as the same variant - no need to stand there with a bucket. The Slime Milker and the hand-bucket loop work just like before. One catch: a source's catalyst boosts ride along when you bucket it up by hand, but they reset when milk runs through a tank, so an automated line spawns at the base rate. Heads-up for existing worlds: old placed Slime Milk and old milk buckets do not carry over - re-mill them from Slime Buckets. More detail: [the automated milk doc](https://github.com/Flatts3000/productive-frogs/blob/main/docs/automated_milk_variants.md).

**v1.8.1 - v1.8.3 (shipped): polish and Froglight fuel.** A handful of small fixes - releasing a slime from a bucket no longer spills water, a released slime is always the small size you captured, and a dispenser can place a slime for you. And a new convenience: Coal and Blaze Froglights now burn as furnace fuel, each like the resource it's made of - a Coal Froglight burns like coal, a Blaze Froglight like a blaze rod. Every other Froglight stays purely decorative.

**v1.9.0 (shipped): Refined Storage support.** Running Refined Storage? Its processors and Quartz Enriched Iron are now farmable through the frog loop - prime a slime with one, let the matching frog eat it, and smelt the Froglight back into the resource, with the same hands-off Slime Milk farming as everything else. And Silicon, the resource it shares with Applied Energistics 2, now works whether you have Refined Storage, AE2, or both installed.

**v1.10.0 (shipped): obsidian moves to the Infernal frog.** Obsidian takes a diamond pickaxe to mine, so it belongs after diamond in the journey - the Infernal Frog now farms Obsidian (and Mekanism's Refined Obsidian), taking over from the Cave Frog. Existing slimes adjust themselves on world load; Froglights, buckets, and Slime Milk are untouched.

**v1.11.0 (shipped): Flux Networks + the full Powah ladder.** Running Flux Networks? Flux Dust is now farmable through the frog loop - one variant covers the whole chain, since ingots, blocks, and cores all craft from dust. Powah's roster is complete too: Energized Steel, Uraninite, and Dry Ice join the crystals that were already farmable, so every Powah material runs on frogs - especially valuable in skyblock, where Uraninite has no ore to mine. Also: the Blaze resource is now the blaze rod (prime with a rod, smelt the Froglight back into a rod - each rod crafts into two powder), and the Niotic/Nitro crystal colors were un-swapped to match Powah's actual crystals.

**v1.12.0 (shipped): the Froglight Crucible + Casting Mold.** A heated basin that melts Froglights into real fluids - park it over a torch, campfire, lava, or fire and right-click Froglights in. Water and Lava Froglights melt into bucketable water and lava (renewable on a skyblock), and metal Froglights melt into molten metal worth two ingots each, which the new Casting Mold solidifies back into ingots - stack heat, Crucible, and Mold into a three-block tower and it runs end to end with no power and no pipes. Some Froglights are heat sources themselves (Lava, Blaze, and Powah's Blazing Crystal). With AllTheOres installed the molten flows into its tanks and recipes directly; with Industrial Foregoing, Pink Slime and Plastic Froglights melt into Pink Slime fluid and Latex. Plus two new farmable resources: ice and snow - previously impossible to obtain on a skyblock with no cold biome (the Ice Froglight also melts into water, and a placement tip: Froglights glow bright, and bright light melts vanilla ice, so don't light an ice farm with its own produce). JEI shows the whole lane, including the heat ladder.

**v1.13.0 (shipped): more vanilla resources, and renewable fluids move earlier.** Six more vanilla materials became farmable: breeze rod, ghast tear, phantom membrane, armadillo scute, turtle scute, and honeycomb - several of them a real grind to gather by hand. And the Water and Lava slimes moved to the Cave frog (the first one you raise), since renewable water and lava are early-game staples and shouldn't wait until the aquatic and nether frogs. Heads-up if you already farm lava: the Lava slime is now primed with pointed dripstone instead of a magma block (your existing lava slimes carry over and just change frog).

**v1.14.0 (shipped): Brewed Froglights and the boss tier.** Splash a potion onto a slime before your frog eats it and the Froglight it drops keeps that effect: place it as a glowing aura that buffs (or, with Poison or Wither, harms) everything around it, hold it to buff just yourself, or wear it in a Curios charm slot. And the trophy resources are now farmable - Wither Skeleton Skull, Nether Star, Dragon Egg, and Dragon Breath - but you earn them: you prime the first slime with the real drop, and a farm only runs once you've walled its Slime Milk source in a six-sided catalyst altar (the milk is toxic, so you want it contained). Plus five more everyday drops (bone, string, gunpowder, rotten flesh, magma cream) and, with Mekanism, Refined Glowstone.

**v1.15.0 (shipped): the Slime Churn + Just Dire Things.** The Slime Churn is the Milker run backwards - load a bucket of a variant's Slime Milk plus empty buckets and it fills them with that variant's captured slimes, on the same spawn economy as a placed source, so you can store and ship slimes as easily as milk. And Just Dire Things support: Ferricore, Blazegold, Celestigem, and Eclipse Alloy farm through the loop, plus a Crucible fuel lane that melts those Froglights straight into JDT's refined fuels.

**v1.16.0 (shipped): the Terrarium.** The flagship automation block - a sealed multiblock that runs the whole loop on its own. Pipe Slime Milk into the Controller, ceiling Sprinklers rain slimes into the cavity, frogs raised in built-in Incubators eat them, and the Froglights collect in the Hatch for piping out. The Incubators handle the full egg-to-frog lifecycle and keep bred stats; the Controller screen and Jade tooltips show structure state, buffered milk, and live frog count.

**v1.17.0 (shipped): Frog Legs and Fairy Tales.** A content drop for skyblock and beyond. Killing any frog now drops Frog Legs - a renewable meat (cook it, or make Frog Legs Soup); brew a Potion of Hopping from them to leap forward and land soft. The Froglight Cleaver is an endgame sword that drops a slime's Froglight when it kills it. The Ender Dragon now drops a Princess's Kiss that turns a frog into a villager - a single kiss can bootstrap a trading economy. And the Frog Net catches and relocates a frog without leashing or killing it, keeping a bred frog's stats intact. Plus a wide set of config switches so a pack can ship just the parts it wants.

**v1.18.0 (shipped): Made to Measure.** A full set of config controls for modpack authors: switch off individual resources, whole species, the boss tier, the stat-breeding system, or just a particular mod's variants - all without touching datapacks. Every toggle is a soft one, so disabling content never breaks a world and re-enabling restores it.

**v1.19.0 (shipped): renewable lava, two ways.** Lava Froglights now burn as furnace fuel worth a full lava bucket, so the renewable-lava loop doubles as a renewable solid fuel with no empty bucket to juggle. And the Froglight Crucible now melts stone straight to lava - drop in cobblestone, stone, gravel, or netherrack over heat and it renders down to lava, no frog required (matching Ex Deorum's heated crucible).

### v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Auto-fed Slime Milker:** an upgraded variant that pulls and pushes buckets without you standing there. (Basic hopper input/output already works in v1.)

**Frog Terrarium / Habitat block:** shipped in v1.16.0 (see the release notes above) - the sealed multiblock that automates the whole loop.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs.

**Capacity / efficiency upgrades** for habitat blocks.

**Potential:** FE / NeoForge Energy power compat.

### Long-shot ideas (not committed)

* **Stat upgrades for your frogs and slimes.** A buff item that levels a creature's stats toward a 10/10/10 cap. Grinding one up to max becomes its own goal, and maxed creatures could show it off with cosmetic tiers.
* **Deep genetics / breeding tree** on the frog side (Forestry-style).
* **Alchemical slime category** tied to brewing. Infuse with a potion item, the slime takes on that potion's effect, killing it drops the matching potion.
* **New dimensions** (Frog Realm).
* **Quest / advancement integration.**

### Compatibility promise

* v2 won't break v1 worlds.
* v2 machines remain optional. Every v1 capability stays usable without them.
* Cross-mod compatibility add-ons are independent of the v1 / v2 split.

### Explicitly NOT planned

**Fabric port.** Productive Frogs is NeoForge-only by design and will remain so.
