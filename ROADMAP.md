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

---

## v2: automation

The scale-up release. v1 lives unchanged; v2 layers automation on top. A player who never crafts a v2 block still has every v1 capability.

**Buffered / auto-upgrading Slime Milker:** the v1 Milker and Spawnery already expose basic hopper I/O (`Capabilities.ItemHandler.BLOCK`), so hoppers can feed and drain them today. The v2 layer is the buffered upgrade - internal slime/milk buffers and auto-cycling so a line keeps running without per-item hopper handoff.

**Frog Terrarium / Habitat block:** placeable frog housing with input/output inventory. Houses one or more frogs in a contained system.

**Auto-feeders:** hopper-fed slime delivery to nearby frogs (the frog side, not the appliance side - distinct from the appliance hopper I/O that already shipped in v1). Alternative to milk-spawn proximity.

**Capacity / efficiency upgrades** for habitat blocks.

**Pipe / hopper-aware fluid handling** for Slime Milk.

**Potential:** FE / NeoForge Energy power compat.

---

## Parked for v2: the Froglight Juicer

A processing block that converts a **Froglight into a fluid** - a third Froglight cash-out lane alongside smelting (1x solid) and crushing (2x dust). Its first iteration targets renewable **water and lava**: a lava slime drops a lava Froglight, which the Juicer turns into lava (water likewise), so a skyblock or nether-locked pack can farm the two fluids it most lacks.

It is **v2, not a v1.x appliance**, because it introduces two mechanics the mod has never shipped: an internal fluid tank (drained by a right-click bucket or by pipes via `Capabilities.FluidHandler.BLOCK`) and an energy requirement (NeoForge `Capabilities.EnergyStorage.BLOCK`, the FE-equivalent every power mod bridges to, no hard dep). As the lightest possible v2 block (single block, no multiblock), it is a good candidate to open v2 with and establish the energy + fluid-tank capability patterns the rest of v2 (Frog Terrarium, buffered Slime Milker) will reuse.

Decided shape, the new water/lava slime variants it needs, and the open questions to resolve before building: [docs/froglight_juicer.md](./docs/froglight_juicer.md).

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

**Native crusher block.** The 2x metal-Froglight crush yield is delegated to external crusher mods (Mekanism / Immersive Engineering / EnderIO, via the optional `mod_loaded` recipes shipped in v1.3.0). The mod will not ship its own crusher; packs that want the 2x payoff install one of those mods.

**Drop-collection block.** Vanilla hoppers under the frog pen already collect dropped Froglights. The mod will not ship a custom collector.

**Custom Slime Milk containers (jugs / tanks).** Bucket is the only first-party container. The Slime Milk fluid is already accessible to tank/pipe mods (the bucket's fluid capability + vanilla `LiquidBlock`), so bulk storage is covered by those; the mod will not ship jugs or tanks.
